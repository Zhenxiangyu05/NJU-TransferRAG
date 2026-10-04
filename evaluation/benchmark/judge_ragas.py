#!/usr/bin/env python3
"""Phase B: score saved Phase A rows with Ragas 0.4.3; never re-runs RAG."""

from __future__ import annotations

import argparse
import asyncio
import json
import os
from pathlib import Path
from typing import Any, Callable

from metrics import mean, render_summary_markdown


async def score_rows(rows: list[dict[str, Any]],
                     on_result: Callable[[dict[str, Any]], None] | None = None) -> dict[str, Any]:
    from openai import AsyncOpenAI
    from ragas.embeddings import OpenAIEmbeddings
    from ragas.llms import llm_factory
    from ragas.metrics.collections import AnswerRelevancy, ContextRecall, Faithfulness

    base_url = os.environ["AI_BASE_URL"]
    api_key = os.environ["AI_API_KEY"]
    judge_model = os.environ.get("RAGAS_JUDGE_MODEL") or os.environ["AI_CHAT_MODEL"]
    judge_embedding = os.environ.get("RAGAS_JUDGE_EMBEDDING_MODEL") or os.environ.get("AI_EMBEDDING_MODEL", "bge-m3")
    client = AsyncOpenAI(api_key=api_key, base_url=base_url, max_retries=0, timeout=90.0)
    llm = llm_factory(judge_model, provider="openai", client=client, temperature=0)
    embeddings = OpenAIEmbeddings(client=client, model=judge_embedding)
    relevance = AnswerRelevancy(llm=llm, embeddings=embeddings)
    faithfulness = Faithfulness(llm=llm)
    context_recall = ContextRecall(llm=llm)

    async def score_one(row: dict[str, Any]) -> dict[str, Any]:
        scored: dict[str, Any] = {"caseId": row["caseId"], "version": row["version"],
                                  "answerRelevancy": None, "faithfulness": None, "contextRecall": None}
        if (row.get("httpStatus") == 200 or row.get("executionStatus") == "COMPLETED") and row.get("answer"):
            try:
                value = await relevance.ascore(user_input=row["query"], response=row["answer"])
                scored["answerRelevancy"] = getattr(value, "value", value)
            except Exception as exc:  # Preserve Phase A and report judge/provider incompatibility safely.
                scored["answerRelevancyError"] = type(exc).__name__
        retrieved_contexts = row.get("retrievedContexts")
        approved_contexts = row.get("approvedContexts")
        if retrieved_contexts or approved_contexts:
            if row.get("answer"):
                try:
                    if not approved_contexts:
                        raise ValueError("approvedContext unavailable; faithfulness is not scoreable")
                    value = await faithfulness.ascore(user_input=row["query"], response=row["answer"],
                                                      retrieved_contexts=approved_contexts)
                    scored["faithfulness"] = getattr(value, "value", value)
                except Exception as exc:
                    scored["faithfulnessError"] = type(exc).__name__
            reference_answer = row.get("referenceAnswer")
            if reference_answer and retrieved_contexts:
                try:
                    value = await context_recall.ascore(user_input=row["query"], reference=reference_answer,
                                                        retrieved_contexts=retrieved_contexts)
                    scored["contextRecall"] = getattr(value, "value", value)
                except Exception as exc:
                    scored["contextRecallError"] = type(exc).__name__
        else:
            scored["faithfulnessNAReason"] = "actual approved context unavailable"
            scored["contextRecallNAReason"] = "actual retrieved context unavailable"
        return scored

    results = []
    # Limit judge load while preserving the frozen case order and resumable checkpoint.
    for offset in range(0, len(rows), 3):
        batch = await asyncio.gather(*(score_one(row) for row in rows[offset:offset + 3]))
        for scored in batch:
            results.append(scored)
            if on_result is not None:
                on_result(scored)
    return {
        "ragasVersion": "0.4.3",
        "api": "collections metric .ascore(**kwargs)",
        "judgeModel": judge_model,
        "judgeEmbeddingModel": judge_embedding,
        "temperature": 0,
        "deterministic": "temperature=0; provider may still be nondeterministic",
        "judgeEvaluationCount": sum(row.get("answerRelevancy") is not None for row in results),
        "judgeEvaluationsRequested": len(results),
        "scores": results,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path)
    parser.add_argument("--full", action="store_true",
                        help="Use a private, resumable checkpoint for the new full benchmark")
    args = parser.parse_args()
    rows: list[dict[str, Any]] = []
    for name in ("raw-v1.jsonl", "raw-v2.jsonl"):
        path = args.result_dir / name
        rows.extend(json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip())
    if args.full:
        checkpoint = args.result_dir / "ragas-progress.jsonl"
        prior = [json.loads(line) for line in checkpoint.read_text(encoding="utf-8").splitlines()
                 if line.strip()] if checkpoint.exists() else []
        expected_keys = [(row["caseId"], row["version"]) for row in rows]
        prior_keys = [(row["caseId"], row["version"]) for row in prior]
        if prior_keys != expected_keys[:len(prior_keys)]:
            raise SystemExit("RAGAS checkpoint does not match the frozen full trace order")
        def save_progress(scored: dict[str, Any]) -> None:
            with checkpoint.open("a", encoding="utf-8") as output:
                output.write(json.dumps(scored, ensure_ascii=False) + "\n")
        result = asyncio.run(score_rows(rows[len(prior):], on_result=save_progress))
        result["scores"] = prior + result["scores"]
        result["judgeEvaluationCount"] = sum(row.get("answerRelevancy") is not None
                                             for row in result["scores"])
        result["judgeEvaluationsRequested"] = len(rows)
    else:
        result = asyncio.run(score_rows(rows))
    path = args.result_dir / ("ragas-full.json" if args.full else "ragas-smoke.json")
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.full:
        print(json.dumps({"output": str(path), "ragasVersion": result["ragasVersion"],
                          "scored": len(result["scores"]),
                          "answerRelevancyScored": result["judgeEvaluationCount"],
                          "faithfulnessScored": sum(x.get("faithfulness") is not None
                                                   for x in result["scores"]),
                          "contextRecallScored": sum(x.get("contextRecall") is not None
                                                    for x in result["scores"])}, ensure_ascii=False))
        return 0
    summary_path = args.result_dir / "summary.json"
    generation_path = args.result_dir / "generation-summary.json"
    if not summary_path.exists() or not generation_path.exists():
        print(json.dumps({"output": str(path), "ragasVersion": result["ragasVersion"],
                          "scored": len(result["scores"]),
                          "answerRelevancyScored": sum(x.get("answerRelevancy") is not None
                                                        for x in result["scores"]),
                          "faithfulnessScored": sum(x.get("faithfulness") is not None
                                                     for x in result["scores"]),
                          "contextRecallScored": sum(x.get("contextRecall") is not None
                                                      for x in result["scores"]),
                          "legacySummaryUpdated": False}, ensure_ascii=False))
        return 0
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    generation = json.loads(generation_path.read_text(encoding="utf-8"))
    grouped = {version: [row for row in result["scores"] if row["version"] == version]
               for version in ("V1", "V2")}
    for version in ("V1", "V2"):
        for key in ("faithfulness", "answerRelevancy", "contextRecall"):
            scores = [row.get(key) for row in grouped[version]]
            generation[version][key] = {"score": mean(scores),
                                        "n": sum(score is not None for score in scores),
                                        "reason": None if any(score is not None for score in scores)
                                        else "not scoreable with fields exposed by the production API"}
        for raw_name in (f"raw-{version.lower()}.jsonl",):
            raw_path = args.result_dir / raw_name
            raw_rows = [json.loads(line) for line in raw_path.read_text(encoding="utf-8").splitlines() if line.strip()]
            scores_by_id = {row["caseId"]: row for row in grouped[version]}
            for raw in raw_rows:
                metric_row = scores_by_id.get(raw["caseId"], {})
                for key in ("faithfulness", "answerRelevancy", "contextRecall"):
                    raw[key] = metric_row.get(key)
            raw_path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in raw_rows), encoding="utf-8")
    generation["ragas"] = {"version": result["ragasVersion"], "judgeModel": result["judgeModel"],
                           "judgeEmbeddingModel": result["judgeEmbeddingModel"],
                           "temperature": result["temperature"], "status": "COMPLETED"}
    generation_path.write_text(json.dumps(generation, ensure_ascii=False, indent=2), encoding="utf-8")
    summary["metrics"] = generation
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.result_dir / "summary.md").write_text(render_summary_markdown(summary), encoding="utf-8")
    print(json.dumps({"output": str(path), "ragasVersion": result["ragasVersion"],
                      "judgeModel": result["judgeModel"], "judgeEmbeddingModel": result["judgeEmbeddingModel"],
                      "scored": len(result["scores"]),
                      "judgeEvaluations": result["judgeEvaluationCount"],
                      "answerRelevancyScored": sum(x.get("answerRelevancy") is not None for x in result["scores"]),
                      "faithfulnessScored": sum(x.get("faithfulness") is not None for x in result["scores"]),
                      "contextRecallScored": sum(x.get("contextRecall") is not None for x in result["scores"])}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
