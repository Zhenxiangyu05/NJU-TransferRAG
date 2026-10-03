#!/usr/bin/env python3
"""Apply explicitly entered Human Decision fields to the Gold sidecar."""

from __future__ import annotations

import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
GOLD = HERE / "gold-labels.json"
REVIEW = HERE / "gold-review.md"
FACT_STATUSES = {"SUPPORTED", "NEEDS_FIX", "AMBIGUOUS"}


def _parse_ids(value: str) -> list[int]:
    if not value.strip():
        raise ValueError("expectedDocumentIds is required when confirming a case")
    parsed = json.loads(value)
    if not isinstance(parsed, list) or any(not isinstance(item, int) or item <= 0 for item in parsed):
        raise ValueError("expectedDocumentIds must be a JSON array of positive integer Evidence IDs")
    if len(parsed) != len(set(parsed)):
        raise ValueError("expectedDocumentIds cannot contain duplicates")
    return parsed


def parse_human_decisions(markdown: str) -> dict[str, dict[str, str]]:
    sections = re.split(r"(?m)^## (TRAG-\d+)\s*$", markdown)
    decisions: dict[str, dict[str, str]] = {}
    for index in range(1, len(sections), 2):
        case_id, body = sections[index], sections[index + 1]
        marker = re.search(r"(?m)^Human Decision:\s*$", body)
        if not marker:
            continue
        fields: dict[str, str] = {}
        for line in body[marker.end():].lstrip("\r\n").splitlines():
            if not line.strip():
                break
            field = re.match(r"^(answerable|expectedDocumentIds|expectedFactsStatus|referenceAnswer|reviewStatus):\s*(.*)$", line)
            if field:
                fields[field.group(1)] = field.group(2).strip()
        decisions[case_id] = fields
    return decisions


def apply_decisions(gold: dict, decisions: dict[str, dict[str, str]]) -> tuple[dict, int]:
    by_id = {row["caseId"]: row for row in gold.get("cases", [])}
    applied = 0
    for case_id, fields in decisions.items():
        status = fields.get("reviewStatus", "")
        if status != "CONFIRMED":
            continue
        row = by_id.get(case_id)
        if row is None:
            raise ValueError(f"unknown case id: {case_id}")
        answerable_value = fields.get("answerable", "").lower()
        if answerable_value not in {"true", "false"}:
            raise ValueError(f"{case_id}: answerable must be true or false")
        answerable = answerable_value == "true"
        document_ids = _parse_ids(fields.get("expectedDocumentIds", ""))
        if answerable and not document_ids:
            raise ValueError(f"{case_id}: answerable=true requires at least one verified Evidence Document ID")
        if not answerable and document_ids:
            raise ValueError(f"{case_id}: answerable=false requires expectedDocumentIds: []")
        facts_status = fields.get("expectedFactsStatus", "").upper()
        if facts_status not in FACT_STATUSES:
            raise ValueError(f"{case_id}: expectedFactsStatus must be one of {sorted(FACT_STATUSES)}")
        row.update({
            "answerable": answerable,
            "expectedDocumentIds": document_ids,
            "expectedFactsStatus": facts_status,
            "referenceAnswer": fields.get("referenceAnswer", "") or None,
            "reviewStatus": "CONFIRMED",
            "notes": "Human Decision fields manually confirmed; re-verify Evidence source metadata before scoring.",
        })
        applied += 1
    return gold, applied


def main() -> None:
    gold = json.loads(GOLD.read_text(encoding="utf-8"))
    decisions = parse_human_decisions(REVIEW.read_text(encoding="utf-8"))
    updated, count = apply_decisions(gold, decisions)
    GOLD.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Applied human-confirmed decisions: {count}; no model/provider calls were made.")


if __name__ == "__main__":
    main()
