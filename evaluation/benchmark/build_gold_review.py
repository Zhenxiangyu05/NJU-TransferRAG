#!/usr/bin/env python3
"""Create a conservative review sidecar without changing the frozen dataset."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / "evaluation" / "test-cases.json"
GOLD = Path(__file__).with_name("gold-labels.json")
REVIEW = Path(__file__).with_name("gold-review.md")


def main() -> None:
    cases = json.loads(DATASET.read_text(encoding="utf-8"))
    labels = []
    for case in cases:
        has_basis = bool(case.get("expectedFacts") and case.get("evidenceText"))
        labels.append({
            "caseId": case["caseId"],
            "answerable": None,
            "expectedDocumentIds": None,
            "referenceAnswer": None,
            "reviewStatus": "NEEDS_REVIEW" if has_basis else "NOT_SCORABLE",
            "expectedFactsAvailable": bool(case.get("expectedFacts")),
            "notes": ("未从 sourceFile 自动映射生产 Document ID；answerability 与参考答案待人工对照原文审核。"
                      if has_basis else "原始固定集缺少 expectedFacts 或 evidenceText，当前不可评分。"),
        })
    GOLD.write_text(json.dumps({
        "dataset": "evaluation/test-cases.json",
        "generatedBy": "build_gold_review.py",
        "policy": "Generated labels are never CONFIRMED; only human review may confirm them.",
        "cases": labels,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    lines = [
        "# Gold label review (82 fixed cases)", "",
        "`evaluation/test-cases.json` is frozen and was not changed. This sidecar contains no confirmed labels.",
        "For each row, verify answerability and source Document IDs against the actual Evidence document and original text.",
        "Write a concise reference answer only after that review. Change `reviewStatus` to `CONFIRMED` only after manual verification.",
        "Use `NOT_SCORABLE` only when the source or expected facts cannot support a reliable label.", "",
        "| Case | Source file | Category | Expected facts | Answerable | Document IDs | Reference answer | Status |",
        "|---|---|---|---:|---|---|---|---|",
    ]
    for case in cases:
        cells = [case["caseId"], case.get("sourceFile", ""), case.get("category", ""),
                 str(len(case.get("expectedFacts", []))), "TBD", "TBD", "TBD", "NEEDS_REVIEW"]
        lines.append("| " + " | ".join(value.replace("|", "\\|") for value in cells) + " |")
    lines += ["", "## Coverage at creation", "",
              f"- Cases: {len(cases)}", "- CONFIRMED: 0", "- NEEDS_REVIEW: "
              + str(sum(row["reviewStatus"] == "NEEDS_REVIEW" for row in labels)),
              "- NOT_SCORABLE: " + str(sum(row["reviewStatus"] == "NOT_SCORABLE" for row in labels)),
              "- Answerable confirmed: 0", "- Unanswerable confirmed: 0",
              "- expectedDocumentIds confirmed: 0", "- referenceAnswer confirmed: 0",
              "- Expected facts available in frozen dataset: "
              + str(sum(bool(case.get("expectedFacts")) for case in cases)), "",
              "Metric coverage for the 82-case set is currently Hit@3 0, MRR 0, Faithfulness 0, "
              "Answer Relevancy 0, Context Recall 0, Expected Fact Recall 0, Citation Accuracy 0, "
              "and Refusal Accuracy 0: no confirmed labels or full-set traces exist yet.", ""]
    REVIEW.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
