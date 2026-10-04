#!/usr/bin/env python3
"""Freeze every confirmed Gold case for one full, paired V1/V2 run."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / "evaluation/test-cases.json"
GOLD = ROOT / "evaluation/benchmark/gold-labels.json"
FREEZE = ROOT / "evaluation/benchmark/full-gold-freeze.json"
SUBSET = ROOT / "evaluation/benchmark/full-benchmark-cases.json"
TOOLING_VERSION = "full-benchmark-v1"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def payloads() -> tuple[dict, dict]:
    cases = json.loads(DATASET.read_text(encoding="utf-8"))
    gold = json.loads(GOLD.read_text(encoding="utf-8"))
    labels = {row["caseId"]: row for row in gold["cases"]}
    if len(cases) != 82 or set(labels) != {row["caseId"] for row in cases}:
        raise SystemExit("Gold/test-cases case IDs do not match the frozen 82-case set")
    selected = [row["caseId"] for row in cases
                if labels[row["caseId"]]["reviewStatus"] == "CONFIRMED"]
    if len(selected) < 75 or len(selected) <= 24:
        raise SystemExit(f"Full benchmark requires >=75 confirmed and >24 cases; found {len(selected)}")
    for case_id in selected:
        label = labels[case_id]
        ids = label.get("expectedDocumentIds")
        if label.get("answerable") is True and not ids:
            raise SystemExit(f"Missing Evidence ID: {case_id}")
        if label.get("answerable") is False and ids != []:
            raise SystemExit(f"Refusal Gold must have empty Evidence IDs: {case_id}")
    freeze = {
        "totalCases": len(cases),
        "confirmed": len(selected),
        "needsReview": len(cases) - len(selected),
        "answerable": sum(labels[c]["answerable"] is True for c in selected),
        "unanswerable": sum(labels[c]["answerable"] is False for c in selected),
        "referenceAnswers": sum(bool(labels[c].get("referenceAnswer")) for c in selected),
        "testCasesHash": sha256(DATASET),
        "goldLabelsHash": sha256(GOLD),
        "productionLogicVersion": gold["productionLogicVersion"],
        "benchmarkToolingVersion": TOOLING_VERSION,
    }
    subset = {
        "datasetVersion": "full-gold-confirmed-v1",
        "productionLogicVersion": gold["productionLogicVersion"],
        "benchmarkToolingVersion": TOOLING_VERSION,
        "fullBenchmarkCaseCount": len(selected),
        "cases": [{"caseId": case_id} for case_id in selected],
    }
    return freeze, subset


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Check an existing freeze without rewriting")
    args = parser.parse_args()
    expected_freeze, expected_subset = payloads()
    if args.verify:
        actual_freeze = json.loads(FREEZE.read_text(encoding="utf-8"))
        actual_subset = json.loads(SUBSET.read_text(encoding="utf-8"))
        for key, value in expected_freeze.items():
            if actual_freeze.get(key) != value:
                raise SystemExit(f"Gold freeze mismatch: {key}")
        if actual_subset != expected_subset:
            raise SystemExit("Full benchmark case list changed after freeze")
    else:
        if FREEZE.exists() or SUBSET.exists():
            raise SystemExit("Refusing to overwrite an existing full benchmark freeze")
        expected_freeze["frozenAtUtc"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
        FREEZE.write_text(json.dumps(expected_freeze, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        SUBSET.write_text(json.dumps(expected_subset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"confirmed": expected_freeze["confirmed"],
                      "answerable": expected_freeze["answerable"],
                      "unanswerable": expected_freeze["unanswerable"],
                      "referenceAnswers": expected_freeze["referenceAnswers"],
                      "verified": args.verify}))


if __name__ == "__main__":
    main()
