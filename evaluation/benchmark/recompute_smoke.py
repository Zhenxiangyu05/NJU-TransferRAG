#!/usr/bin/env python3
"""Recompute derived metrics from saved raw smoke results without RAG calls."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from typing import Any

from metrics import (citation_accuracy, hit_at_k, paired_classification,
                     reciprocal_rank, refusal_accuracy, render_summary_markdown, summarize)


def failure_category(row: dict[str, Any]) -> str | None:
    if row.get("error"):
        return "OTHER"
    if row.get("answerableGold") is False:
        return None if row.get("refusalCorrect") else "FAILED_TO_REFUSE"
    normalized = (row.get("answer") or "").replace(" ", "")
    if row.get("answerableGold") is True and any(token in normalized for token in
                                                  ("无法确定", "资料不足", "无法回答")):
        return "NOT_ANSWERABLE"
    if row.get("expectedDocumentIds") and row.get("citationCorrect") is False:
        return "WRONG_CITATION"
    return None


def load_rows(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path)
    parser.add_argument("--routing-diagnostics", type=Path)
    args = parser.parse_args()
    by_version = {version: load_rows(args.result_dir / f"raw-{version.lower()}.jsonl") for version in ("V1", "V2")}
    diagnostics = json.loads(args.routing_diagnostics.read_text(encoding="utf-8")) if args.routing_diagnostics else None
    for version, rows in by_version.items():
        if diagnostics is not None:
            entries = diagnostics.get(version)
            if not isinstance(entries, list) or len(entries) != len(rows):
                raise SystemExit(f"routing diagnostics for {version} must have exactly {len(rows)} entries")
        else:
            entries = [None] * len(rows)
        for row, entry in zip(rows, entries):
            if entry is not None:
                if entry.get("caseId") != row["caseId"]:
                    raise SystemExit(f"routing diagnostic order mismatch for {version}/{row['caseId']}")
                row["retrievalLayer"] = entry.get("retrievalLayer")
                row["fallback"] = entry.get("fallbackTriggered")
                row["fallbackReason"] = entry.get("fallbackReason")
                row["canonicalCandidateCount"] = entry.get("canonicalCandidateCount")
                row["evidenceCandidateCount"] = entry.get("evidenceCandidateCount")
                row["evidenceFallback"] = row["retrievalLayer"] == "EVIDENCE" and row["fallback"] is True
                row["canonicalHit"] = (None if row["answerableGold"] is not True else
                                        row["retrievalLayer"] == "CANONICAL" and row["fallback"] is False)
            expected = row.get("expectedDocumentIds")
            row["hitAt3"] = hit_at_k(row.get("retrievedDocumentIds"), expected)
            row["reciprocalRank"] = reciprocal_rank(row.get("retrievedDocumentIds"), expected)
            row["citationCorrect"] = citation_accuracy(row.get("citationDocumentIds"), expected)
            row["refusalCorrect"] = refusal_accuracy(row.get("answerableGold"), row.get("answer"),
                                                     row.get("citationDocumentIds"))
            row["failureCategory"] = failure_category(row)
        (args.result_dir / f"raw-{version.lower()}.jsonl").write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")

    summaries = {version: summarize(rows) for version, rows in by_version.items()}
    old = json.loads((args.result_dir / "summary.json").read_text(encoding="utf-8"))
    old["limitations"] = [
        ("Production API does not expose raw retrieved document IDs or actual generation contexts; "
         "query-free ordered service-log diagnostics are attached for routing fields in this smoke."),
        "Final Evidence Citation IDs are not treated as raw retrieval rankings.",
        "RAGAS faithfulness/context recall remain N/A until actual retrieved/approved contexts and reliable references are available.",
        "Citation overlap is a deterministic citation proxy, not full factual answer correctness; all answerable smoke outcomes need manual review.",
    ]
    (args.result_dir / "summary.json").write_text(json.dumps(old, ensure_ascii=False, indent=2), encoding="utf-8")
    retrieval = {
        "note": "Raw retrieval-ranked document IDs are not exposed by the production ask API; Hit@3 and MRR remain N/A.",
        "V1": summaries["V1"]["hitAt3"], "V2": summaries["V2"]["hitAt3"],
        "MRR": {"V1": summaries["V1"]["mrr"], "V2": summaries["V2"]["mrr"]},
        "routingDiagnosticsAttached": diagnostics is not None,
    }
    (args.result_dir / "retrieval-summary.json").write_text(json.dumps(retrieval, ensure_ascii=False, indent=2), encoding="utf-8")
    generation = json.loads((args.result_dir / "generation-summary.json").read_text(encoding="utf-8"))
    generation["V1"], generation["V2"] = summaries["V1"], summaries["V2"]
    ragas_path = args.result_dir / "ragas-smoke.json"
    if ragas_path.exists():
        ragas = json.loads(ragas_path.read_text(encoding="utf-8"))
        ragas["judgeEvaluationCount"] = sum(row.get("answerRelevancy") is not None for row in ragas["scores"])
        ragas["judgeEvaluationsRequested"] = len(ragas["scores"])
        ragas_path.write_text(json.dumps(ragas, ensure_ascii=False, indent=2), encoding="utf-8")
        for version in ("V1", "V2"):
            version_scores = [row for row in ragas["scores"] if row["version"] == version]
            for metric_name in ("faithfulness", "answerRelevancy", "contextRecall"):
                scores = [row.get(metric_name) for row in version_scores if row.get(metric_name) is not None]
                generation[version][metric_name] = {
                    "score": sum(scores) / len(scores) if scores else None,
                    "n": len(scores),
                    "reason": None if scores else "not scoreable with fields exposed by the production API",
                }
        generation["ragas"] = {"version": ragas["ragasVersion"], "judgeModel": ragas["judgeModel"],
                               "judgeEmbeddingModel": ragas["judgeEmbeddingModel"],
                               "temperature": ragas["temperature"], "status": "COMPLETED",
                               "judgeEvaluations": ragas["judgeEvaluationCount"]}
        old["metrics"] = generation
    else:
        old["metrics"] = generation
    (args.result_dir / "generation-summary.json").write_text(json.dumps(generation, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.result_dir / "summary.json").write_text(json.dumps(old, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.result_dir / "summary.md").write_text(render_summary_markdown(old), encoding="utf-8")
    failures = [row for rows in by_version.values() for row in rows if row.get("failureCategory")]
    (args.result_dir / "failures.md").write_text("# Smoke failures\n\n" + ("\n".join(
        f"- {row['version']} {row['caseId']}: {row['failureCategory']}" for row in failures) or "- None"), encoding="utf-8")
    print(json.dumps({version: {"cases": summary["cases"], "citationAccuracy": summary["citationAccuracy"],
                                "refusalAccuracy": summary["refusalAccuracy"], "latencyMs": summary["latencyMs"],
                                "canonicalHitRate": summary["canonicalHitRate"],
                                "evidenceFallbackRate": summary["evidenceFallbackRate"]}
                      for version, summary in summaries.items()}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
