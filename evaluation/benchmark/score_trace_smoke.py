#!/usr/bin/env python3
"""Compute deterministic metrics for an existing in-process trace smoke; no RAG calls."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from metrics import (citation_accuracy, expected_fact_recall, hit_at_k,
                     reciprocal_rank, refusal_accuracy)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def selected_ids(row: dict[str, Any]) -> list[int] | None:
    layer, fallback = row.get("retrievalLayer"), row.get("fallback")
    if layer == "CANONICAL" and fallback is False:
        candidates = row.get("evidenceEquivalentRanking")
    elif layer == "EVIDENCE":
        candidates = row.get("evidenceFallbackRanking") if fallback else row.get("rawRetrievedCandidates")
    else:
        return None
    return [int(item["documentId"]) for item in candidates or [] if item.get("documentId") is not None]


def rate(values: list[float | bool | None]) -> dict[str, Any]:
    present = [float(value) for value in values if value is not None]
    return {"score": sum(present) / len(present) if present else None, "n": len(present)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path)
    parser.add_argument("--gold", type=Path, default=Path("evaluation/benchmark/smoke-gold.json"))
    parser.add_argument("--dataset", type=Path, default=Path("evaluation/test-cases.json"))
    args = parser.parse_args()
    gold = {row["caseId"]: row for row in json.loads(args.gold.read_text(encoding="utf-8"))["cases"]}
    dataset = {row["caseId"]: row for row in json.loads(args.dataset.read_text(encoding="utf-8"))}
    ragas_path = args.result_dir / "ragas-smoke.json"
    ragas = json.loads(ragas_path.read_text(encoding="utf-8")) if ragas_path.exists() else {"scores": []}
    ragas_by_key = {(row["caseId"], row["version"]): row for row in ragas.get("scores", [])}
    summaries: dict[str, dict[str, Any]] = {}
    for version in ("V1", "V2"):
        rows = read_jsonl(args.result_dir / f"raw-{version.lower()}.jsonl")
        per_metric: dict[str, list[Any]] = {key: [] for key in (
            "hitAt3", "mrr", "faithfulness", "answerRelevancy", "contextRecall",
            "expectedFactRecall", "citationAccuracy", "refusalAccuracy")}
        for row in rows:
            label = gold[row["caseId"]]
            confirmed = not label.get("manualReviewRequired", True)
            expected = label.get("expectedDocumentIds") if confirmed else None
            ranking = selected_ids(row)
            per_metric["hitAt3"].append(hit_at_k(ranking, expected))
            per_metric["mrr"].append(reciprocal_rank(ranking, expected))
            per_metric["citationAccuracy"].append(citation_accuracy(row.get("finalCitationDocumentIds"), expected))
            per_metric["refusalAccuracy"].append(refusal_accuracy(
                label.get("answerable") if confirmed else None, row.get("answer"),
                row.get("finalCitationDocumentIds")))
            per_metric["expectedFactRecall"].append(expected_fact_recall(
                row.get("expectedFacts") or dataset.get(row["caseId"], {}).get("expectedFacts"),
                row.get("retrievedContexts")))
            judged = ragas_by_key.get((row["caseId"], version), {})
            for metric in ("faithfulness", "answerRelevancy", "contextRecall"):
                per_metric[metric].append(judged.get(metric))
        summaries[version] = {key: rate(values) for key, values in per_metric.items()}
        summaries[version]["cases"] = len(rows)
        summaries[version]["canonicalPathN"] = sum(row.get("retrievalLayer") == "CANONICAL" for row in rows)
        summaries[version]["evidenceFallbackN"] = sum(row.get("fallback") is True for row in rows)
    output = {
        "source": "evaluation-only in-process RagService traces",
        "metrics": summaries,
        "goldPolicy": "Only smoke labels with manualReviewRequired=false are scored for document/citation/refusal accuracy.",
        "expectedFactRecallDefinition": "strict whitespace-normalized expected-fact literal coverage in actual retrievedContext; custom proxy, not RAGAS Context Recall.",
        "ragasVersion": ragas.get("ragasVersion"),
        "limitations": [
            "Unreviewed expected document IDs are not used for Hit@3/MRR/Citation Accuracy.",
            "Smoke cases without reference answers use the frozen expectedFacts as RAGAS ContextRecall reference only; this is reported separately from custom Expected Fact Recall.",
        ],
    }
    (args.result_dir / "trace-summary.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summaries, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
