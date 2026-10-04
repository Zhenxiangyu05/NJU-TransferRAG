#!/usr/bin/env python3
"""Build a conservative, human-editable review pack for the frozen 82-case set."""

from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

try:
    from .gold_resolver import resolve_expected_facts
    from .metrics import gold_coverage
except ImportError:  # Support direct execution from this directory.
    from gold_resolver import resolve_expected_facts
    from metrics import gold_coverage

ROOT = Path(__file__).resolve().parents[2]
BENCHMARK = Path(__file__).resolve().parent
DATASET = ROOT / "evaluation" / "test-cases.json"
GOLD = BENCHMARK / "gold-labels.json"
REVIEW = BENCHMARK / "gold-review.md"
COVERAGE = BENCHMARK / "gold-coverage.json"
HISTORY_FILES = [
    ROOT / "evaluation" / "full-regression-after-metadata-year" / "evaluation-results.json",
    ROOT / "evaluation" / "evaluation-results.json",
]

# These are labels found in historical reports, not claims about current production.
HISTORICAL_CLASSES = {
    "PASS", "PARTIAL", "WRONG", "WRONG_SOURCE", "YEAR_MISMATCH",
    "DEPARTMENT_MISMATCH", "REFUSAL_FALSE_NEGATIVE", "HALLUCINATION", "SYSTEM_ERROR",
}


def _read_json(path: Path, default: Any) -> Any:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else default


def _normal_title(value: str) -> str:
    stem = Path(value).stem.casefold()
    return re.sub(r"[\s_（）()\-]+", "", stem)


def load_history() -> tuple[dict[str, dict[str, Any]], dict[str, list[dict[str, Any]]]]:
    """Return latest historical class per case and citation metadata keyed by normalized title."""
    outcomes: dict[str, dict[str, Any]] = {}
    docs_by_title: dict[str, dict[int, dict[str, Any]]] = defaultdict(dict)
    for path in HISTORY_FILES:
        rows = _read_json(path, [])
        for row in rows:
            case_id = row.get("caseId")
            if case_id and row.get("classification") in HISTORICAL_CLASSES:
                outcome = outcomes.setdefault(case_id, {"classifications": [], "reports": []})
                classification = row["classification"]
                if classification not in outcome["classifications"]:
                    outcome["classifications"].append(classification)
                report = path.relative_to(ROOT).as_posix()
                if report not in outcome["reports"]:
                    outcome["reports"].append(report)
            for source in row.get("sources", []):
                title = source.get("title")
                source_type = source.get("sourceType")
                document_id = source.get("documentId")
                if not title or not source_type or not isinstance(document_id, int):
                    continue
                key = _normal_title(title)
                docs_by_title[key][document_id] = {
                    "historicalDocumentId": document_id,
                    "title": title,
                    "sourceType": source_type,
                    "historicalOnly": True,
                }
    return outcomes, {key: list(value.values()) for key, value in docs_by_title.items()}


def _case_candidates(case: dict[str, Any], docs_by_title: dict[str, list[dict[str, Any]]]) -> list[dict[str, Any]]:
    key = _normal_title(case.get("sourceFile", ""))
    matched: list[dict[str, Any]] = []
    for title_key, docs in docs_by_title.items():
        # Exact stem is preferred; partial matching is limited to long normalized titles.
        if key and (key == title_key or (min(len(key), len(title_key)) >= 12 and
                                         (key in title_key or title_key in key))):
            matched.extend(docs)
    unique: dict[tuple[int, str], dict[str, Any]] = {}
    for candidate in matched:
        historical_id = candidate["historicalDocumentId"]
        # Current Canonical imports occupy IDs above the original Evidence set. Never
        # surface a stale 21+ ID as a candidate ID; title/type remain useful for lookup.
        current_id = historical_id if historical_id <= 20 else None
        item = {
            "documentId": current_id,
            "title": candidate["title"],
            "sourceType": candidate["sourceType"],
            "whyRelevant": "历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。",
            "idStatus": "historical-id-not-carried-forward" if current_id is None else "historical-id-candidate-needs-current-verification",
        }
        unique[(historical_id, candidate["title"])] = item
    return list(unique.values())


def assign_batches(cases: list[dict[str, Any]], candidates: dict[str, list[dict[str, Any]]],
                   history: dict[str, dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    """Prioritize easy direct facts, then experience/policy, ambiguity, and hard cases."""
    groups: dict[str, list[dict[str, Any]]] = {f"Batch {i}": [] for i in range(1, 5)}
    for case in cases:
        category = case.get("category", "")
        query = case.get("question", "")
        fact_count = len(case.get("expectedFacts") or [])
        is_compound = category in {"组合问题"} or any(token in query for token in ("又", "以及", "并且", "同时", "分别"))
        is_experience = category in {"经验", "学习经验", "面试/机试", "风险经验"}
        is_policy = category in {"转专业政策", "条件"}
        is_year_dept_scoped = bool(case.get("sourceYear") and case.get("department"))
        hist = set(history.get(case["caseId"], {}).get("classifications", []))
        if is_compound or category in {"组合问题", "别名"} or hist.intersection({"WRONG", "WRONG_SOURCE", "YEAR_MISMATCH", "REFUSAL_FALSE_NEGATIVE"}) and not candidates.get(case["caseId"]):
            groups["Batch 4"].append(case)
        elif is_experience or is_policy:
            groups["Batch 2"].append(case)
        elif is_year_dept_scoped and category in {"明确事实", "培养方案", "时间敏感", "课程规划"} and fact_count <= 4:
            groups["Batch 1"].append(case)
        else:
            groups["Batch 3"].append(case)

    # Balance groups toward the requested ~20 cases without disturbing priority order.
    # Move only the least ambiguous overflow from batches 1-3 into the next review lane.
    for source, target in (("Batch 1", "Batch 3"), ("Batch 2", "Batch 3"), ("Batch 3", "Batch 4")):
        while len(groups[source]) > 22:
            groups[target].insert(0, groups[source].pop())
    return groups


def _reference_recommendations(cases: list[dict[str, Any]], history: dict[str, dict[str, Any]]) -> dict[str, list[str]]:
    # Deliberately includes both prior successes and known failure classes; this is a
    # human reference-writing shortlist, not a claim that a case has passed.
    wanted = {
        "TRAG-001": ["FACT", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-002": ["FACT", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-006": ["POLICY", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-009": ["EXPERIENCE", "DEPARTMENT_SCOPED"],
        "TRAG-015": ["POLICY", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-017": ["POLICY", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-024": ["POLICY", "YEAR_SCOPED", "DEPARTMENT_SCOPED", "COLLOQUIAL"],
        "TRAG-027": ["COMPOUND", "POLICY"],
        "TRAG-028": ["YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-031": ["FACT", "YEAR_SCOPED"],
        "TRAG-043": ["EXPERIENCE", "DEPARTMENT_SCOPED"],
        "TRAG-045": ["EXPERIENCE", "COLLOQUIAL"],
        "TRAG-048": ["FACT", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-049": ["FACT", "YEAR_SCOPED"],
        "TRAG-056": ["EXPERIENCE"],
        "TRAG-061": ["EXPERIENCE", "FALLBACK"],
        "TRAG-062": ["EXPERIENCE", "COLLOQUIAL"],
        "TRAG-067": ["EXPERIENCE", "YEAR_SCOPED"],
        "TRAG-070": ["EXPERIENCE", "FALLBACK"],
        "TRAG-072": ["POLICY", "YEAR_SCOPED", "DEPARTMENT_SCOPED"],
        "TRAG-073": ["POLICY", "YEAR_SCOPED", "FALLBACK"],
        "TRAG-076": ["FACT", "DEPARTMENT_SCOPED"],
        "TRAG-077": ["COMPOUND", "POLICY", "EXPERIENCE"],
        "TRAG-080": ["EXPERIENCE", "DEPARTMENT_SCOPED"],
    }
    ids = {case["caseId"] for case in cases}
    return {case_id: labels for case_id, labels in wanted.items() if case_id in ids}


def compute_coverage(cases: list[dict[str, Any]], labels: list[dict[str, Any]],
                    reference_ids: set[str]) -> dict[str, Any]:
    coverage = gold_coverage(labels, {case["caseId"]: case for case in cases})
    coverage.update({
        "productionLogicVersion": "8c34ccdf16d283231ff972ca8860fff3baaa28a4",
        "benchmarkToolingVersion": "ae69fae6958fdfc5041677ea4f073d55fce45241",
        "totalCases": len(cases),
        "referenceAnswersConfirmed": sum(
            row.get("caseId") in reference_ids
            and isinstance(row.get("referenceAnswer"), str)
            and bool(row["referenceAnswer"].strip())
            for row in labels if row.get("reviewStatus") == "CONFIRMED"
        ),
        "targets": {"answerableAndExpectedDocumentIds": len(cases), "referenceAnswers": "20-30"},
    })
    return coverage


def _human_decisions(markdown: str) -> dict[str, dict[str, str]]:
    decisions: dict[str, dict[str, str]] = {}
    sections = re.split(r"(?m)^## (TRAG-\d+)\s*$", markdown)
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
                fields[field.group(1)] = field.group(2).rstrip()
        decisions[case_id] = fields
    return decisions


def preserve_human_decisions(template: str, existing: str) -> str:
    saved = _human_decisions(existing)
    sections = re.split(r"(?m)(^## TRAG-\d+\s*$)", template)
    output = [sections[0]]
    fields = ("answerable", "expectedDocumentIds", "expectedFactsStatus", "referenceAnswer", "reviewStatus")
    for index in range(1, len(sections), 2):
        heading, body = sections[index], sections[index + 1]
        case_id = heading.removeprefix("## ").strip()
        if case_id in saved:
            values = saved[case_id]
            lines = ["Human Decision:"] + [
                f"{field}: {values[field]}" if values.get(field, "") else f"{field}:"
                for field in fields
            ]
            body = re.sub(r"(?m)^Human Decision:[ \t]*\r?\n(?:(?:answerable|expectedDocumentIds|expectedFactsStatus|referenceAnswer|reviewStatus):[^\r\n]*\r?\n?)+",
                          "\n".join(lines) + "\n", body, count=1)
        output.extend([heading, body])
    return "".join(output)


def build_artifacts(cases: list[dict[str, Any]], old_gold: dict[str, Any],
                    history: dict[str, dict[str, Any]], docs_by_title: dict[str, list[dict[str, Any]]],
                    existing_review: str = "") -> tuple[dict[str, Any], str, dict[str, Any]]:
    old_rows = {row["caseId"]: row for row in old_gold.get("cases", [])}
    candidate_by_case = {case["caseId"]: _case_candidates(case, docs_by_title) for case in cases}
    batches = assign_batches(cases, candidate_by_case, history)
    batch_by_id = {case["caseId"]: name for name, rows in batches.items() for case in rows}
    reference = _reference_recommendations(cases, history)
    labels: list[dict[str, Any]] = []
    for case in cases:
        previous = old_rows.get(case["caseId"], {})
        # Preserve explicit, provenance-backed confirmations only; generated suggestions never promote status.
        review_source = previous.get("reviewSource")
        manually_confirmed = (previous.get("reviewStatus") == "CONFIRMED"
                              and review_source == "manual_review_batch_1")
        evidence_review_source = review_source in {
            "evidence_verified_accelerated_review", "evidence_verified_full_review"
        }
        verified_evidence = previous.get("verifiedEvidence")
        reviewed_scope = previous.get("reviewedEvidenceScope")
        evidence_verified_answerable = (
            previous.get("reviewStatus") == "CONFIRMED" and evidence_review_source
            and previous.get("answerable") is True
            and isinstance(previous.get("expectedDocumentIds"), list)
            and bool(previous["expectedDocumentIds"])
            and isinstance(verified_evidence, list) and bool(verified_evidence)
        )
        evidence_verified_unanswerable = (
            previous.get("reviewStatus") == "CONFIRMED" and evidence_review_source
            and previous.get("answerable") is False
            and previous.get("expectedDocumentIds") == []
            and verified_evidence == []
            and isinstance(reviewed_scope, dict)
            and reviewed_scope.get("documentRole") == "EVIDENCE"
            and isinstance(reviewed_scope.get("documentIds"), list)
            and bool(reviewed_scope["documentIds"])
            and bool(previous.get("reviewerNotes"))
        )
        confirmed = manually_confirmed or evidence_verified_answerable or evidence_verified_unanswerable
        label = {
            "caseId": case["caseId"],
            "answerable": previous.get("answerable") if confirmed else None,
            "expectedDocumentIds": previous.get("expectedDocumentIds") if confirmed else None,
            "expectedFactsStatus": previous.get("expectedFactsStatus") if confirmed else None,
            "referenceAnswer": previous.get("referenceAnswer") if confirmed else None,
            "reviewStatus": "CONFIRMED" if confirmed else "NEEDS_REVIEW",
            "expectedFactsAvailable": bool(case.get("expectedFacts")),
            "suggestedAnswerable": "true (review required)" if case.get("expectedFacts") and case.get("evidenceText") else "uncertain",
            "suggestedExpectedDocumentIds": sorted({doc["documentId"] for doc in candidate_by_case[case["caseId"]]
                                                      if doc["documentId"] is not None}),
            "candidateEvidenceDocuments": candidate_by_case[case["caseId"]],
            "reviewBatch": batch_by_id[case["caseId"]],
            "referenceAnswerRecommended": case["caseId"] in reference,
            "referenceAnswerCategories": reference.get(case["caseId"], []),
            "historicalOutcome": history.get(case["caseId"]),
            "notes": "所有历史来源仅作候选线索；请核验当前 DocumentRole=EVIDENCE、ID 与原文。生成建议不会自动填入 Gold，也不会标记 CONFIRMED。",
        }
        if confirmed:
            for field in ("expectedFactsOverride", "reviewerNotes", "reviewSource", "reviewedAt",
                          "verifiedEvidence", "reviewedEvidenceScope", "notes"):
                if field in previous:
                    label[field] = previous[field]
        labels.append(label)

    coverage = compute_coverage(cases, labels, set(reference))
    lines = [
        "# Gold label review — frozen 82-case set", "",
        "Production Logic Version: `8c34ccdf16d283231ff972ca8860fff3baaa28a4`",
        "Benchmark Tooling Version: `ae69fae6958fdfc5041677ea4f073d55fce45241` (evaluation-only tracing; not a new RAG version).", "",
        "`evaluation/test-cases.json` remains frozen. Candidate labels are suggestions only. Verify each candidate against the current MySQL Document metadata and original Evidence; never use a Canonical Document as a final source ID.",
        "Gold `expectedFactsOverride` is optional and, when present, completely replaces the frozen test case's `expectedFacts` array; it is never an index patch.",
        "Edit only the `Human Decision` fields in this file, then run `python evaluation/benchmark/sync_gold_review.py` to validate and apply human decisions to `gold-labels.json`. Generated suggestions are never confirmed.", "",
        "## Coverage", "",
        f"- Total: {coverage['totalCases']}",
        f"- Confirmed total / answerable / unanswerable: {coverage['confirmedTotal']} / {coverage['confirmedAnswerable']} / {coverage['confirmedUnanswerable']}",
        f"- Confirmed expectedDocumentIds / expectedFacts: {coverage['expectedDocumentIdsConfirmed']} / {coverage['expectedFactsConfirmed']}",
        f"- Confirmed reference answers: {coverage['referenceAnswersConfirmed']} (target 20–30)",
        f"- Scorable n — Hit@3 {coverage['scorable']['hitAt3']}, MRR {coverage['scorable']['mrr']}, Citation {coverage['scorable']['citationAccuracy']}, Refusal {coverage['scorable']['refusalAccuracy']}, Context Recall {coverage['scorable']['contextRecall']}, Expected Fact Recall {coverage['scorable']['expectedFactRecall']}",
        "- Full review target: all 82 cases decided; answerable cases have Evidence IDs and refusals have empty IDs. Reference answers cover only the selected 20–30.", "",
        "## Reference-answer shortlist", "",
        "Write concise, evidence-bounded answers only after verifying the original Evidence. The list intentionally includes historical PASS and failure classes to reduce cherry-picking. Historical outcomes are context, not current results.", "",
        "| Case | Suggested reference coverage | Historical outcome |",
        "|---|---|---|",
    ]
    for case_id, categories in reference.items():
        outcomes = ", ".join(history.get(case_id, {}).get("classifications", [])) or "not found"
        lines.append(f"| {case_id} | {', '.join(categories)} | {outcomes} |")
    lines += ["", "UNANSWERABLE Gold is evidence-relative: confirmed negative cases are scorable for Refusal Accuracy only and do not enter retrieval, citation, expected-fact, or context-recall metrics. The separate `SMOKE-NEG-001` remains outside this 82-case set.", ""]

    for batch_name, batch_cases in batches.items():
        lines += [f"# {batch_name} — {len(batch_cases)} cases", ""]
        for case in batch_cases:
            case_id = case["caseId"]
            label = next(row for row in labels if row["caseId"] == case_id)
            lines += [f"## {case_id}", "", f"Query: {case.get('question', '')}",
                      f"Source file: {case.get('sourceFile', '')}",
                      f"Category: {case.get('category', '')}",
                      "Existing Expected Facts:"]
            facts = resolve_expected_facts(case_id, case, label)
            lines.extend(f"- {fact}" for fact in facts) if facts else lines.append("- (none; human review required)")
            lines += ["", "Candidate Evidence Documents:"]
            if label["candidateEvidenceDocuments"]:
                for doc in label["candidateEvidenceDocuments"]:
                    doc_id = (str(doc["documentId"]) if doc["documentId"] is not None
                              else "待当前库核验（历史 ID 不沿用）")
                    lines.append(f"- Document ID: {doc_id}; Title: {doc['title']}; sourceType: {doc['sourceType']}; why relevant: {doc['whyRelevant']}")
            else:
                lines.append("- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。")
            historical_outcomes = ", ".join((label["historicalOutcome"] or {}).get("classifications", [])) or "not found"
            lines += [f"Historical outcome (not current benchmark result): {historical_outcomes}",
                      f"Suggested answerable: {label['suggestedAnswerable']}",
                      f"Suggested expectedDocumentIds: {json.dumps(label['suggestedExpectedDocumentIds'], ensure_ascii=False)} (no auto-mapped current IDs)",
                      f"Reference answer required: {'YES' if label['referenceAnswerRecommended'] else 'NO'}" +
                      (f" — {', '.join(label['referenceAnswerCategories'])}" if label['referenceAnswerRecommended'] else ""),
                      f"Current reviewStatus: {label['reviewStatus']}", "", "Human Decision:",
                      "answerable:", "expectedDocumentIds:", "expectedFactsStatus:",
                      "referenceAnswer:", f"reviewStatus: {label['reviewStatus']}", ""]
    review = preserve_human_decisions("\n".join(lines), existing_review)
    return {"dataset": "evaluation/test-cases.json", "generatedBy": "build_gold_review.py",
            "policy": "Generated suggestions are never CONFIRMED. Confirmation requires recorded manual review or explicit Evidence verification; refusals require an audited Evidence scope.",
            "expectedFactsOverrideSemantics": "complete_replacement",
            "productionLogicVersion": "8c34ccdf16d283231ff972ca8860fff3baaa28a4",
            "benchmarkToolingVersion": "ae69fae6958fdfc5041677ea4f073d55fce45241",
            "referenceAnswerRecommendedCaseIds": list(reference), "cases": labels}, review, coverage


def main() -> None:
    cases = _read_json(DATASET, [])
    old_gold = _read_json(GOLD, {})
    existing_review = REVIEW.read_text(encoding="utf-8") if REVIEW.exists() else ""
    history, docs_by_title = load_history()
    gold, review, coverage = build_artifacts(cases, old_gold, history, docs_by_title, existing_review)
    GOLD.write_text(json.dumps(gold, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    REVIEW.write_text(review, encoding="utf-8")
    COVERAGE.write_text(json.dumps(coverage, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts = Counter(row["reviewStatus"] for row in gold["cases"])
    print(f"cases={len(cases)} NEEDS_REVIEW={counts['NEEDS_REVIEW']} CONFIRMED={counts['CONFIRMED']} NOT_SCORABLE={counts['NOT_SCORABLE']}")
    print("batch_sizes=" + json.dumps(Counter(row["reviewBatch"] for row in gold["cases"]), ensure_ascii=False))
    print(f"reference_answer_recommendations={len(gold['referenceAnswerRecommendedCaseIds'])}")


if __name__ == "__main__":
    main()
