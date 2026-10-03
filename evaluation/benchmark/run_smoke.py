#!/usr/bin/env python3
"""Phase A: run the fixed six-case smoke against isolated V1 and V2 URLs."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from metrics import (citation_accuracy, hit_at_k, paired_classification,
                     reciprocal_rank, refusal_accuracy, render_summary_markdown, summarize)
try:
    from .gold_resolver import resolve_expected_facts
except ImportError:  # Support direct script execution.
    from gold_resolver import resolve_expected_facts


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CASES = ROOT / "evaluation" / "test-cases.json"
DEFAULT_SMOKE = ROOT / "evaluation" / "smoke-gold.json"
DEFAULT_EXISTING_SMOKE = ROOT / "evaluation" / "smoke-cases.json"
RESULTS = ROOT / "evaluation" / "results"


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def post_question(base_url: str, question: str, timeout: int) -> tuple[int | None, dict[str, Any] | None, str | None, float]:
    payload = json.dumps({"question": question}, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/api/rag/ask",
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
            return response.status, json.loads(body), None, (time.perf_counter() - started) * 1000
    except urllib.error.HTTPError as exc:
        body = exc.read()
        try:
            parsed = json.loads(body)
        except (json.JSONDecodeError, UnicodeDecodeError):
            parsed = None
        return exc.code, parsed, f"HTTP_ERROR_{exc.code}", (time.perf_counter() - started) * 1000
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        return None, None, type(exc).__name__, (time.perf_counter() - started) * 1000


def extract_ids(sources: Any) -> list[int]:
    ids: list[int] = []
    for source in sources or []:
        value = source.get("documentId") if isinstance(source, dict) else None
        if value is not None and int(value) not in ids:
            ids.append(int(value))
    return ids


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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--v1-url", required=True, help="Base URL for the isolated canonical-first=false service")
    parser.add_argument("--v2-url", required=True, help="Base URL for the canonical-first=true service")
    parser.add_argument("--cases", type=Path, default=DEFAULT_CASES)
    parser.add_argument("--gold", type=Path, default=DEFAULT_SMOKE)
    parser.add_argument("--smoke-cases", type=Path, default=DEFAULT_EXISTING_SMOKE)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--routing-diagnostics", type=Path, default=None,
                        help="Optional ordered, query-free log extract; one record per request for each version")
    parser.add_argument("--timeout", type=int, default=180)
    args = parser.parse_args()

    dataset = {row["caseId"]: row for row in read_json(args.cases)}
    smoke_assets = {row["caseId"]: row for row in read_json(args.smoke_cases)}
    gold = read_json(args.gold)
    selected = []
    for metadata in gold["cases"]:
        case_id = metadata["caseId"]
        test_case = dataset.get(case_id) or smoke_assets.get(case_id) or {}
        row = dict(test_case)
        row.update(metadata)
        row["expectedFacts"] = resolve_expected_facts(case_id, test_case, metadata)
        if not row.get("question"):
            raise SystemExit(f"No question found for {case_id}")
        selected.append(row)
    if len(selected) > 6:
        raise SystemExit("Smoke is limited to at most six cases")

    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_dir = args.output or (RESULTS / stamp)
    output_dir.mkdir(parents=True, exist_ok=False)
    by_version: dict[str, list[dict[str, Any]]] = {"V1": [], "V2": []}
    urls = {"V1": args.v1_url, "V2": args.v2_url}

    # Paired requests are serialized to avoid provider concurrency/load confounds.
    for case in selected:
        question = case["question"]
        for version in ("V1", "V2"):
            status, response, error, elapsed = post_question(urls[version], question, args.timeout)
            sources = response.get("sources", []) if isinstance(response, dict) else []
            citation_ids = extract_ids(sources)
            answer = response.get("answer") if isinstance(response, dict) else None
            expected_ids = case.get("expectedDocumentIds")
            row = {
                "caseId": case["caseId"],
                "categories": case.get("categories", []),
                "version": version,
                "query": question,
                "answerableGold": case.get("answerable"),
                "expectedFacts": case.get("expectedFacts", []),
                "retrievalLayer": None,
                "fallback": None,
                "fallbackReason": None,
                "retrievedDocumentIds": None,
                "retrievalDiagnosticsAvailable": False,
                "citationDocumentIds": citation_ids,
                "sourceTypes": [source.get("sourceType") for source in sources if isinstance(source, dict)],
                "answer": answer,
                "retrievedContexts": None,
                "contextAvailability": "NOT_EXPOSED_BY_PRODUCTION_API",
                "expectedDocumentIds": expected_ids,
                "hitAt3": hit_at_k(None, expected_ids),
                "reciprocalRank": reciprocal_rank(None, expected_ids),
                "citationCorrect": citation_accuracy(citation_ids, expected_ids),
                "refusalCorrect": refusal_accuracy(case.get("answerable"), answer, citation_ids),
                "faithfulness": None,
                "answerRelevancy": None,
                "contextRecall": None,
                "wrongDepartment": None,
                "wrongPolicyYear": None,
                "wrongCohortYear": None,
                "unsupportedClaim": None,
                "latencyMs": round(elapsed, 2),
                "httpStatus": status,
                "error": error,
                "manualReview": case.get("manualReviewRequired", True),
                "knownLimitation": case.get("knownLimitation"),
                "evidenceFallback": None,
                "canonicalHit": None,
            }
            row["failureCategory"] = failure_category(row)
            by_version[version].append(row)

    if args.routing_diagnostics:
        diagnostics = read_json(args.routing_diagnostics)
        for version, rows in by_version.items():
            entries = diagnostics.get(version)
            if not isinstance(entries, list) or len(entries) != len(rows):
                raise SystemExit(f"routing diagnostics for {version} must have exactly {len(rows)} entries")
            for row, entry in zip(rows, entries):
                if entry.get("caseId") != row["caseId"]:
                    raise SystemExit(f"routing diagnostic order mismatch for {version}/{row['caseId']}")
                row["retrievalLayer"] = entry.get("retrievalLayer")
                row["fallback"] = entry.get("fallbackTriggered")
                row["fallbackReason"] = entry.get("fallbackReason")
                row["canonicalCandidateCount"] = entry.get("canonicalCandidateCount")
                row["evidenceCandidateCount"] = entry.get("evidenceCandidateCount")
                row["evidenceFallback"] = (row["retrievalLayer"] == "EVIDENCE" and row["fallback"] is True)
                row["canonicalHit"] = (None if row["answerableGold"] is not True else
                                        row["retrievalLayer"] == "CANONICAL" and row["fallback"] is False)
                row["failureCategory"] = failure_category(row)

    for version, rows in by_version.items():
        with (output_dir / f"raw-{version.lower()}.jsonl").open("w", encoding="utf-8") as handle:
            for row in rows:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")

    v1_by_id = {row["caseId"]: row for row in by_version["V1"]}
    v2_by_id = {row["caseId"]: row for row in by_version["V2"]}
    comparison = []
    for case in selected:
        one, two = v1_by_id[case["caseId"]], v2_by_id[case["caseId"]]
        comparison.append({
            "caseId": case["caseId"],
            "V1_HTTP": one["httpStatus"],
            "V2_HTTP": two["httpStatus"],
            "V1_CitationDocs": ";".join(map(str, one["citationDocumentIds"])),
            "V2_CitationDocs": ";".join(map(str, two["citationDocumentIds"])),
            "V1_LatencyMs": one["latencyMs"],
            "V2_LatencyMs": two["latencyMs"],
            "V1vsV2": paired_classification(one, two),
            "ManualReview": case.get("manualReviewRequired", True),
            "KnownLimitation": case.get("knownLimitation"),
        })
    with (output_dir / "comparison.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(comparison[0].keys()))
        writer.writeheader()
        writer.writerows(comparison)

    summaries = {version: summarize(rows) for version, rows in by_version.items()}
    (output_dir / "retrieval-summary.json").write_text(json.dumps({
        "note": "Raw canonical/evidence TopK IDs are not exposed by the current production ask API; Hit@3 and MRR are N/A, not inferred from final citations.",
        "V1": summaries["V1"]["hitAt3"],
        "V2": summaries["V2"]["hitAt3"],
        "MRR": {"V1": summaries["V1"]["mrr"], "V2": summaries["V2"]["mrr"]},
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "generation-summary.json").write_text(json.dumps({
        "V1": summaries["V1"], "V2": summaries["V2"],
        "ragas": {"version": "0.4.3", "judgeModel": None, "judgeEmbeddingModel": None,
                  "temperature": 0, "status": "PENDING_PHASE_B"},
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    summary = {
        "benchmark": "NJU Compass V1 vs V2 RAG smoke",
        "startedAtUtc": stamp,
        "baselineHead": "8c34ccdf16d283231ff972ca8860fff3baaa28a4",
        "versions": {"V1": {"canonicalFirst": False, "baseUrl": args.v1_url},
                     "V2": {"canonicalFirst": True, "baseUrl": args.v2_url}},
        "controls": {"dataset": args.cases.name, "gold": args.gold.name,
                     "orderedSequentialRequests": True, "maxCases": 6},
        "caseIds": [row["caseId"] for row in selected],
        "metrics": summaries,
        "limitations": [
            "Production API does not expose raw retrieved document IDs or actual generation contexts; query-free ordered service-log diagnostics are attached for routing fields in this smoke.",
            "Final Evidence Citation IDs are not treated as raw retrieval rankings.",
            "RAGAS faithfulness/context recall remain N/A until actual retrieved/approved contexts and reliable references are available.",
            "Citation overlap is a deterministic citation proxy, not full factual answer correctness; all answerable smoke outcomes need manual review.",
        ],
    }
    (output_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "summary.md").write_text(render_summary_markdown(summary), encoding="utf-8")
    failures = [row for rows in by_version.values() for row in rows if row.get("failureCategory")]
    (output_dir / "failures.md").write_text("# Smoke failures\n\n" + ("\n".join(
        f"- {row['version']} {row['caseId']}: {row['failureCategory']}" for row in failures) or "- None"), encoding="utf-8")
    print(output_dir)
    print(json.dumps({version: summaries[version] for version in summaries}, ensure_ascii=False))
    return 0 if all(row.get("httpStatus") == 200 for rows in by_version.values() for row in rows) else 2


if __name__ == "__main__":
    sys.exit(main())
