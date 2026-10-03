"""Deterministic metrics for the NJU Compass RAG benchmark."""

from __future__ import annotations

import math
from typing import Any, Iterable


def hit_at_k(retrieved_ids: Iterable[int] | None,
             expected_ids: Iterable[int] | None,
             k: int = 3) -> float | None:
    expected = set(expected_ids or [])
    if not expected or retrieved_ids is None:
        return None
    retrieved = list(retrieved_ids or [])[:k]
    return float(any(item in expected for item in retrieved))


def reciprocal_rank(retrieved_ids: Iterable[int] | None,
                    expected_ids: Iterable[int] | None,
                    k: int = 3) -> float | None:
    expected = set(expected_ids or [])
    if not expected or retrieved_ids is None:
        return None
    for rank, item in enumerate(list(retrieved_ids or [])[:k], start=1):
        if item in expected:
            return 1.0 / rank
    return 0.0


def citation_accuracy(citation_ids: Iterable[int] | None,
                      expected_ids: Iterable[int] | None) -> bool | None:
    expected = set(expected_ids or [])
    if not expected:
        return None
    return bool(expected.intersection(citation_ids or []))


def refusal_accuracy(answerable_gold: bool | None,
                     answer: str | None,
                     citation_ids: Iterable[int] | None) -> bool | None:
    if answerable_gold is not False:
        return None
    normalized = (answer or "").replace(" ", "")
    refusal = any(token in normalized for token in
                  ("无法确定", "资料不足", "无法回答", "没有足够资料"))
    return refusal and not list(citation_ids or [])


def evidence_equivalent_ranking(canonical_rankings: Iterable[dict[str, Any]],
                               evidence_refs: Iterable[dict[str, Any]]) -> list[int]:
    """Project canonical candidates through their EvidenceRefs, preserving first rank."""
    refs_by_chunk: dict[int, list[int]] = {}
    for ref in evidence_refs:
        chunk_id, document_id = ref.get("canonicalChunkId"), ref.get("evidenceDocumentId")
        if chunk_id is not None and document_id is not None:
            refs_by_chunk.setdefault(int(chunk_id), []).append(int(document_id))
    projected: list[int] = []
    seen: set[int] = set()
    for candidate in canonical_rankings:
        for document_id in refs_by_chunk.get(int(candidate["chunkId"]), []):
            if document_id not in seen:
                seen.add(document_id)
                projected.append(document_id)
    return projected


def selected_ranking(retrieval_layer: str | None,
                     fallback: bool | None,
                     canonical_evidence_ranking: Iterable[int] | None,
                     evidence_ranking: Iterable[int] | None) -> list[int] | None:
    if retrieval_layer == "CANONICAL" and fallback is False:
        return list(canonical_evidence_ranking or [])
    if retrieval_layer == "EVIDENCE":
        return list(evidence_ranking or [])
    return None


def expected_fact_recall(expected_facts: Iterable[str] | None,
                         retrieved_contexts: Iterable[str] | None) -> float | None:
    """Strict deterministic literal coverage; deliberately not a semantic support judge."""
    facts = [" ".join(str(fact).split()) for fact in expected_facts or [] if str(fact).strip()]
    contexts = "\n".join(str(context) for context in retrieved_contexts or [])
    normalized_context = " ".join(contexts.split())
    if not facts or not contexts.strip():
        return None
    return sum(fact in normalized_context for fact in facts) / len(facts)


def gold_coverage(gold_rows: Iterable[dict[str, Any]]) -> dict[str, int]:
    rows = list(gold_rows)
    confirmed = [row for row in rows if row.get("reviewStatus") == "CONFIRMED"]
    answerable_with_docs = [row for row in confirmed if row.get("answerable") is True
                            and isinstance(row.get("expectedDocumentIds"), list)
                            and bool(row["expectedDocumentIds"])]
    reference_confirmed = [row for row in confirmed if bool(row.get("referenceAnswer"))]
    return {
        "cases": len(rows),
        "answerableConfirmed": sum(row.get("answerable") is True for row in confirmed),
        "unanswerableConfirmed": sum(row.get("answerable") is False for row in confirmed),
        "expectedDocumentIdsConfirmed": sum(row.get("expectedDocumentIds") is not None for row in confirmed),
        "expectedFactsConfirmed": sum(row.get("expectedFactsStatus") == "SUPPORTED" for row in confirmed),
        "referenceAnswerConfirmed": len(reference_confirmed),
        "hitAt3Scorable": len(answerable_with_docs),
        "mrrScorable": len(answerable_with_docs),
        "citationAccuracyScorable": len(answerable_with_docs),
        "refusalAccuracyScorable": sum(row.get("answerable") is False for row in confirmed),
        "contextRecallScorable": sum(row.get("expectedFactsStatus") == "SUPPORTED"
                                     for row in reference_confirmed),
        "expectedFactsAvailable": sum(bool(row.get("expectedFactsAvailable")) for row in rows),
        "notScorable": sum(row.get("reviewStatus") == "NOT_SCORABLE" for row in rows),
    }


def percentile(values: Iterable[float | int | None], percentile_value: float) -> float | None:
    ordered = sorted(float(value) for value in values if value is not None)
    if not ordered:
        return None
    if not 0 <= percentile_value <= 100:
        raise ValueError("percentile must be in [0, 100]")
    # Nearest-rank definition, stable for small smoke samples.
    rank = max(1, math.ceil((percentile_value / 100.0) * len(ordered)))
    return ordered[rank - 1]


def mean(values: Iterable[float | int | None]) -> float | None:
    present = [float(value) for value in values if value is not None]
    return sum(present) / len(present) if present else None


def delta(v1: float | None, v2: float | None) -> float | None:
    return None if v1 is None or v2 is None else v2 - v1


def pass_fail(row: dict[str, Any]) -> bool | None:
    answerable = row.get("answerableGold")
    answer = (row.get("answer") or "").replace(" ", "")
    if answerable is False:
        return refusal_accuracy(False, row.get("answer"), row.get("citationDocumentIds"))
    if answerable is True and row.get("expectedDocumentIds"):
        return citation_accuracy(row.get("citationDocumentIds"), row["expectedDocumentIds"])
    return None


def paired_classification(v1_row: dict[str, Any] | None,
                          v2_row: dict[str, Any] | None) -> str:
    if v1_row is None or v2_row is None:
        return "MISSING"
    v1, v2 = pass_fail(v1_row), pass_fail(v2_row)
    if v1 is True and v2 is True:
        return "BOTH_PASS"
    if v1 is False and v2 is True:
        return "V1_FAIL_V2_PASS"
    if v1 is True and v2 is False:
        return "V1_PASS_V2_FAIL"
    if v1 is False and v2 is False:
        return "BOTH_FAIL"
    return "NOT_SCORABLE"


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    latencies = [row.get("latencyMs") for row in rows]
    hits = [row.get("hitAt3") for row in rows]
    mrrs = [row.get("reciprocalRank") for row in rows]
    citations = [row.get("citationCorrect") for row in rows]
    refusals = [row.get("refusalCorrect") for row in rows]
    canonical = [row.get("canonicalHit") for row in rows]
    fallbacks = [row.get("evidenceFallback") for row in rows]

    def bool_rate(items: list[Any]) -> tuple[float | None, int]:
        known = [item for item in items if item is not None]
        return (sum(bool(item) for item in known) / len(known) if known else None, len(known))

    citation_rate, citation_n = bool_rate(citations)
    refusal_rate, refusal_n = bool_rate(refusals)
    canonical_rate, canonical_n = bool_rate(canonical)
    fallback_rate, fallback_n = bool_rate(fallbacks)
    return {
        "cases": len(rows),
        "hitAt3": {"score": mean(hits), "n": sum(value is not None for value in hits)},
        "mrr": {"score": mean(mrrs), "n": sum(value is not None for value in mrrs)},
        "faithfulness": {"score": None, "n": 0, "reason": "actual retrieved/approved context is not returned by the production API"},
        "answerRelevancy": {"score": None, "n": 0, "reason": "RAGAS Phase B not run"},
        "contextRecall": {"score": None, "n": 0, "reason": "no actual retrieved context or reference answer"},
        "citationAccuracy": {"score": citation_rate, "n": citation_n},
        "refusalAccuracy": {"score": refusal_rate, "n": refusal_n},
        "latencyMs": {
            "p50": percentile(latencies, 50),
            "p95": percentile(latencies, 95),
            "mean": mean(latencies),
            "n": sum(value is not None for value in latencies),
        },
        "canonicalHitRate": {"score": canonical_rate, "n": canonical_n},
        "evidenceFallbackRate": {"score": fallback_rate, "n": fallback_n},
    }


def render_summary_markdown(summary: dict[str, Any]) -> str:
    metrics_by_version = summary["metrics"]
    rows = [
        ("Hit@3", "hitAt3", "score"),
        ("MRR", "mrr", "score"),
        ("Faithfulness", "faithfulness", "score"),
        ("Answer Relevancy", "answerRelevancy", "score"),
        ("Context Recall", "contextRecall", "score"),
        ("Citation Accuracy", "citationAccuracy", "score"),
        ("Refusal Accuracy", "refusalAccuracy", "score"),
        ("P50 Latency (ms)", "latencyMs", "p50"),
        ("P95 Latency (ms)", "latencyMs", "p95"),
    ]
    lines = [
        "# NJU Compass V1 vs V2 smoke", "",
        f"Frozen baseline: `{summary['baselineHead']}`", "",
        "| Metric | V1 | V2 | Delta (V2-V1) | N |",
        "|---|---:|---:|---:|---:|",
    ]
    for label, key, field in rows:
        one, two = metrics_by_version["V1"][key], metrics_by_version["V2"][key]
        a, b = one.get(field), two.get(field)
        d = delta(a, b)
        n = min(one.get("n", 0), two.get("n", 0))
        fmt = lambda value: "N/A" if value is None else f"{value:.4f}" if isinstance(value, float) else str(value)
        lines.append(f"| {label} | {fmt(a)} | {fmt(b)} | {fmt(d)} | {n} |")
    lines += ["", "## V2 routing", "",
              f"- Canonical Hit Rate: {metrics_by_version['V2']['canonicalHitRate']['score']}",
              f"- Evidence Fallback Rate: {metrics_by_version['V2']['evidenceFallbackRate']['score']}", "",
              "## Smoke cases", "",
              ", ".join(summary["caseIds"]), "",
              "## Limitations", ""]
    lines.extend(f"- {item}" for item in summary.get("limitations", []))
    return "\n".join(lines) + "\n"
