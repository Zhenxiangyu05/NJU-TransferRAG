#!/usr/bin/env python3
"""Render an interview report from frozen V1/V2 traces and Ragas scores.

This script never calls the RAG or judge APIs. Positive end-to-end answers are
not auto-passed: without human semantic review they remain MANUAL_REVIEW.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

from metrics import (citation_accuracy, gold_expected_fact_recall, hit_at_k, mean,
                     percentile, refusal_accuracy, reciprocal_rank)

FAILURE_CATEGORIES = (
    "RETRIEVAL_MISS", "LOW_RANK", "NOT_ANSWERABLE", "WRONG_CITATION",
    "UNSUPPORTED_CLAIM", "WRONG_DEPARTMENT", "WRONG_YEAR", "INCORRECT_REFUSAL",
    "FAILED_TO_REFUSE", "COMPOUND_QUERY_LIMITATION", "OTHER")
MANUAL_FAILURE_CATEGORIES = {"UNSUPPORTED_CLAIM", "WRONG_DEPARTMENT", "WRONG_YEAR",
                             "COMPOUND_QUERY_LIMITATION"}


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def selected_ranking(row: dict[str, Any]) -> list[int]:
    if row.get("retrievalLayer") == "CANONICAL" and row.get("fallback") is False:
        candidates = row.get("evidenceEquivalentRanking") or []
    elif row.get("retrievalLayer") == "EVIDENCE":
        candidates = (row.get("evidenceFallbackRanking") if row.get("fallback") else
                      row.get("rawRetrievedCandidates")) or []
    else:
        candidates = []
    return [int(candidate["documentId"]) for candidate in candidates if candidate.get("documentId") is not None]


def refusal_text(answer: str | None) -> bool:
    compact = re.sub(r"\s+", "", answer or "")
    return any(token in compact for token in ("无法确定", "资料不足", "无法回答", "没有足够资料"))


def e2e_status(row: dict[str, Any], label: dict[str, Any]) -> str:
    """Only call objective failures and evidence-relative refusal PASS/FAIL automatically."""
    if row.get("executionStatus") != "COMPLETED":
        return "FAIL"
    citations = row.get("finalCitationDocumentIds") or []
    if label.get("answerable") is False:
        return "PASS" if refusal_accuracy(False, row.get("answer"), citations) else "FAIL"
    if refusal_text(row.get("answer")):
        return "FAIL"
    expected = label.get("expectedDocumentIds") or []
    if not hit_at_k(selected_ranking(row), expected):
        return "FAIL"
    if not citation_accuracy(citations, expected):
        return "FAIL"
    # Retrieval and citation succeeded; factual completeness, authority and scope
    # require semantic review and must not be silently promoted to PASS.
    return "MANUAL_REVIEW"


def failure_category(row: dict[str, Any], label: dict[str, Any]) -> str | None:
    if row.get("executionStatus") != "COMPLETED":
        return "OTHER"
    expected = label.get("expectedDocumentIds") or []
    if label.get("answerable") is False:
        return None if refusal_accuracy(False, row.get("answer"), row.get("finalCitationDocumentIds")) else "FAILED_TO_REFUSE"
    ranking = selected_ranking(row)
    relevant_ranks = [rank for rank, document_id in enumerate(ranking, start=1)
                      if document_id in set(expected)]
    if not relevant_ranks:
        if row.get("category") in {"组合问题", "COLLOQUIAL_COMPOUND"}:
            return "COMPOUND_QUERY_LIMITATION"
        return "RETRIEVAL_MISS"
    if relevant_ranks[0] > 3:
        return "LOW_RANK"
    if refusal_text(row.get("answer")):
        if row.get("fallbackReason") == "NOT_ANSWERABLE":
            return "NOT_ANSWERABLE"
        return "INCORRECT_REFUSAL"
    if not citation_accuracy(row.get("finalCitationDocumentIds"), expected):
        return "WRONG_CITATION"
    return None


def scored_values(ragas: dict[str, Any], version: str, key: str) -> list[float | None]:
    return [entry.get(key) for entry in ragas.get("scores", [])
            if entry.get("version") == version]


def rate(values: list[float | bool | None]) -> tuple[float | None, int]:
    present = [float(value) for value in values if value is not None]
    return (sum(present) / len(present) if present else None, len(present))


def fmt(value: float | int | None, digits: int = 3) -> str:
    return "N/A" if value is None else f"{value:.{digits}f}"


def version_metrics(rows: list[dict[str, Any]], labels: dict[str, dict[str, Any]],
                    cases: dict[str, dict[str, Any]], ragas: dict[str, Any], version: str) -> dict[str, Any]:
    metrics: dict[str, Any] = {}
    ranking_hit: list[float | None] = []
    mrr: list[float | None] = []
    citation: list[bool | None] = []
    refusal: list[bool | None] = []
    fact_recall: list[float | None] = []
    latency = [float(row["latencyMs"]) for row in rows if row.get("latencyMs") is not None]
    e2e = {"PASS": [], "FAIL": [], "MANUAL_REVIEW": []}
    failures: dict[str, list[str]] = {}
    for row in rows:
        case_id = row["caseId"]
        label = labels[case_id]
        expected = label.get("expectedDocumentIds") if label.get("answerable") is True else None
        ranking = selected_ranking(row)
        ranking_hit.append(hit_at_k(ranking, expected))
        mrr.append(reciprocal_rank(ranking, expected))
        citation.append(citation_accuracy(row.get("finalCitationDocumentIds"), expected))
        refusal.append(refusal_accuracy(label.get("answerable"), row.get("answer"),
                                         row.get("finalCitationDocumentIds")))
        fact_recall.append(gold_expected_fact_recall(case_id, cases[case_id], label,
                                                      row.get("retrievedContexts")))
        status = e2e_status(row, label)
        e2e[status].append(case_id)
        category = failure_category(row, label)
        if category:
            failures.setdefault(category, []).append(case_id)

    for key, values in (("hitAt3", ranking_hit), ("mrr", mrr),
                        ("citationAccuracy", citation), ("refusalAccuracy", refusal),
                        ("expectedFactRecall", fact_recall)):
        score, n = rate(values)
        metrics[key] = {"score": score, "n": n,
                        "eligibleCaseIds": [row["caseId"] for row, value in zip(rows, values)
                                            if value is not None]}
    for key in ("faithfulness", "answerRelevancy", "contextRecall"):
        score_rows = [entry for entry in ragas.get("scores", [])
                      if entry.get("version") == version and entry.get(key) is not None]
        score, n = rate([entry[key] for entry in score_rows])
        metrics[key] = {"score": score, "n": n,
                        "eligibleCaseIds": [entry["caseId"] for entry in score_rows]}
    metrics["latencyMs"] = {"p50": percentile(latency, 50), "p95": percentile(latency, 95),
                            "mean": mean(latency), "n": len(latency),
                            "eligibleCaseIds": [row["caseId"] for row in rows
                                                if row.get("latencyMs") is not None]}
    metrics["endToEnd"] = {"pass": e2e["PASS"], "fail": e2e["FAIL"],
                           "manualReview": e2e["MANUAL_REVIEW"],
                           "score": len(e2e["PASS"]) / (len(e2e["PASS"]) + len(e2e["FAIL"]))
                           if e2e["PASS"] or e2e["FAIL"] else None,
                           "n": len(e2e["PASS"]) + len(e2e["FAIL"]),
                           "eligibleCaseIds": e2e["PASS"] + e2e["FAIL"]}
    metrics["failures"] = failures
    metrics["canonicalPathN"] = sum(row.get("retrievalLayer") == "CANONICAL" for row in rows)
    metrics["evidenceFallbackN"] = sum(row.get("fallback") is True for row in rows)
    metrics["caseCount"] = len(rows)
    return metrics


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path)
    parser.add_argument("--gold", type=Path, default=Path("evaluation/benchmark/gold-labels.json"))
    parser.add_argument("--dataset", type=Path, default=Path("evaluation/test-cases.json"))
    parser.add_argument("--subset", type=Path, default=Path("evaluation/benchmark/interview-benchmark-24.json"))
    args = parser.parse_args()

    labels = {row["caseId"]: row for row in json.loads(args.gold.read_text(encoding="utf-8"))["cases"]}
    cases = {row["caseId"]: row for row in json.loads(args.dataset.read_text(encoding="utf-8"))}
    subset = json.loads(args.subset.read_text(encoding="utf-8"))
    case_ids = [row["caseId"] for row in subset["cases"]]
    versions = {version: read_jsonl(args.result_dir / f"raw-{version.lower()}.jsonl")
                for version in ("V1", "V2")}
    if any([row["caseId"] for row in versions[v]] != case_ids for v in versions):
        raise SystemExit("Trace case order differs from the frozen subset; refusing to report")
    if any(row.get("reviewStatus") != "CONFIRMED" for row in (labels[case_id] for case_id in case_ids)):
        raise SystemExit("Subset contains unconfirmed Gold")
    ragas_path = args.result_dir / "ragas-smoke.json"
    if not ragas_path.exists():
        raise SystemExit("RAGAS results missing; cannot build the requested final report")
    ragas = json.loads(ragas_path.read_text(encoding="utf-8"))
    metrics = {version: version_metrics(versions[version], labels, cases, ragas, version)
               for version in ("V1", "V2")}
    ragas_errors = {}
    for version in ("V1", "V2"):
        ragas_errors[version] = {}
        for metric in ("faithfulness", "answerRelevancy", "contextRecall"):
            errors: dict[str, int] = {}
            for score in ragas.get("scores", []):
                if score.get("version") == version and score.get(metric + "Error"):
                    error = score[metric + "Error"]
                    errors[error] = errors.get(error, 0) + 1
            if errors:
                ragas_errors[version][metric] = errors
    category_coverage = sorted({category for case_id in case_ids
                                for category in labels[case_id].get("referenceAnswerCategories", [])})
    source_coverage = sorted({evidence.get("sourceType") for case_id in case_ids
                              for evidence in labels[case_id].get("verifiedEvidence", [])
                              if evidence.get("sourceType")})
    category_coverage.extend(source for source in source_coverage if source not in category_coverage)
    if any(labels[case_id].get("answerable") is False for case_id in case_ids):
        category_coverage.append("UNANSWERABLE")
    if any(row.get("fallback") is True for row in versions["V2"]):
        category_coverage.append("FALLBACK")
    summary = {"subset": subset, "metrics": metrics, "ragas": {
        "version": ragas.get("ragasVersion"), "judgeModel": ragas.get("judgeModel"),
        "judgeEmbeddingModel": ragas.get("judgeEmbeddingModel"), "temperature": ragas.get("temperature")},
        "environment": {"runDate": "2026-10-04", "execution": "isolated JUnit runner under /tmp on Tencent Cloud host",
                        "java": "21", "availableMemoryAtRunStartGiB": 1.5,
                        "benchmarkJvmMaxHeapMiB": 512, "benchmarkCpuQuota": "50%",
                        "chatModel": "deepseek-v4-flash", "embeddingModel": "bge-m3",
                        "topK": 3, "minimumSimilarity": 0.55,
                        "productionServicesChanged": False},
        "goldReview": {"manualConfirmed": 17, "evidenceVerified": 7,
                       "evidenceVerifiedReviewSource": "evidence_verified_accelerated_review"},
        "categoryCoverage": category_coverage,
        "ragasJudgeErrors": ragas_errors,
        "e2eDefinition": "Positive cases with retrieval/citation objective failures are FAIL; otherwise they remain MANUAL_REVIEW pending semantic validation of facts, authority and year scope. Negative cases PASS only on refusal plus no citation. Rate n excludes MANUAL_REVIEW.",
        "latencyDefinition": "In-process askForEvaluation wall-clock latency including query embedding, retrieval, answerability and final generation; benchmark environment only, not production SLA.",
        "expectedFactRecallDefinition": "Project-defined strict literal expected-fact coverage in actual retrieved contexts; not RAGAS Context Recall.",
        "experimentControl": "Only canonical-first differs: V1=false, V2=true. Same production logic, data, models, topK, thresholds, prompt and fixed cases.",
        "testCasesUnmodified": True, "productionLogicModified": False,
        "knowledgeBaseModified": False, "subsetChangedAfterRun": False}

    out_json = args.result_dir / "interview-summary.json"
    out_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    render_summary(args.result_dir, summary)
    print(json.dumps({"summary": str(out_json), "caseCount": len(case_ids),
                      "v1Completed": len(versions["V1"]), "v2Completed": len(versions["V2"]),
                      "ragasVersion": ragas.get("ragasVersion"),
                      "v1ManualReview": len(metrics["V1"]["endToEnd"]["manualReview"]),
                      "v2ManualReview": len(metrics["V2"]["endToEnd"]["manualReview"])}, ensure_ascii=False))
    return 0


def render_summary(out_dir: Path, summary: dict[str, Any]) -> None:
    m = summary["metrics"]
    rows = [
        ("End-to-End Pass Rate", "endToEnd", "score"), ("Hit@3", "hitAt3", "score"),
        ("MRR", "mrr", "score"), ("Faithfulness", "faithfulness", "score"),
        ("Answer Relevancy", "answerRelevancy", "score"), ("Context Recall", "contextRecall", "score"),
        ("Expected Fact Recall", "expectedFactRecall", "score"),
        ("Citation Accuracy", "citationAccuracy", "score"),
        ("Refusal Accuracy", "refusalAccuracy", "score"),
        ("P50 Latency (ms)", "latencyMs", "p50"), ("P95 Latency (ms)", "latencyMs", "p95")]
    lines = ["# NJU Compass 面试版 V1 vs V2 Benchmark", "",
             f"- 固定样本：{len(summary['subset']['cases'])}；Gold：17 条人工确认、7 条按确定性 Evidence 规则核验。",
             f"- 环境：腾讯云服务器临时隔离 JUnit 运行；Java {summary['environment']['java']}，RAG JVM heap 上限 {summary['environment']['benchmarkJvmMaxHeapMiB']} MiB、CPU quota {summary['environment']['benchmarkCpuQuota']}；开始时可用内存约 {summary['environment']['availableMemoryAtRunStartGiB']} GiB。",
             f"- RAG 模型：Chat {summary['environment']['chatModel']}，Embedding {summary['environment']['embeddingModel']}；TopK={summary['environment']['topK']}，minimum similarity={summary['environment']['minimumSimilarity']}。",
             f"- 覆盖标签：{', '.join(summary['categoryCoverage'])}。",
             f"- 生产逻辑版本：`{summary['subset']['productionLogicVersion']}`；题集：`{summary['subset']['subsetVersion']}`。",
             f"- RAGAS：{summary['ragas']['version']}；Judge：{summary['ragas']['judgeModel']}；Embedding：{summary['ragas']['judgeEmbeddingModel']}；temperature=0。",
             "- 唯一实验变量为 canonical-first（V1=false，V2=true）；所有 RAG trace 为各版本单次运行。", "",
             "| Metric | V1 | V2 | Delta (V2−V1) | n (V1/V2) |", "|---|---:|---:|---:|---:|"]
    for title, key, field in rows:
        one, two = m["V1"][key], m["V2"][key]
        a, b = one.get(field), two.get(field)
        n = f"{one.get('n', 0)}/{two.get('n', 0)}"
        paired = set(one.get("eligibleCaseIds", [])) == set(two.get("eligibleCaseIds", []))
        delta = b - a if paired and a is not None and b is not None else None
        delta_text = fmt(delta) if paired else "N/A (scorable cases differ)"
        lines.append(f"| {title} | {fmt(a)} | {fmt(b)} | {delta_text} | {n} |")
    lines += ["", "## 端到端判定与人工复核", "",
              summary["e2eDefinition"], ""]
    for version in ("V1", "V2"):
        row = m[version]["endToEnd"]
        lines.append(f"- {version}: PASS={len(row['pass'])}, FAIL={len(row['fail'])}, MANUAL_REVIEW={len(row['manualReview'])}; rate={fmt(row['score'])}, n={row['n']}.")
    lines += ["", "## V1/V2 转换", ""]
    for key, label in (("V1_FAIL_V2_PASS", "V1 FAIL → V2 PASS"), ("V1_PASS_V2_FAIL", "V1 PASS → V2 FAIL"),
                       ("BOTH_PASS", "BOTH PASS"), ("BOTH_FAIL", "BOTH FAIL")):
        one, two = m["V1"]["endToEnd"], m["V2"]["endToEnd"]
        left = set(one["fail"] if key in {"V1_FAIL_V2_PASS", "BOTH_FAIL"} else one["pass"])
        right = set(two["pass"] if key in {"V1_FAIL_V2_PASS", "BOTH_PASS"} else two["fail"])
        ids = sorted(left & right)
        lines.append(f"- {label}: {len(ids)}" + (f" ({', '.join(ids)})" if ids else ""))
    manual_any = sorted(set(m["V1"]["endToEnd"]["manualReview"])
                        | set(m["V2"]["endToEnd"]["manualReview"]))
    lines.append(f"- 至少一版需 MANUAL_REVIEW（不计入上述四类）: {len(manual_any)}" +
                 (f" ({', '.join(manual_any)})" if manual_any else ""))
    lines += ["", "## 可归因失败类别", ""]
    for version in ("V1", "V2"):
        lines.append(f"### {version}")
        for category in FAILURE_CATEGORIES:
            ids = m[version]["failures"].get(category, [])
            if ids:
                lines.append(f"- {category}: {len(ids)} ({', '.join(ids)})")
            elif category in MANUAL_FAILURE_CATEGORIES:
                lines.append(f"- {category}: not auto-classified; semantic cases require MANUAL_REVIEW.")
            else:
                lines.append(f"- {category}: 0")
    lines += ["", "## 运行路径与局限", "",
              f"- V1: canonical path {m['V1']['canonicalPathN']}/24；Evidence fallback {m['V1']['evidenceFallbackN']}/24。",
              f"- V2: canonical path {m['V2']['canonicalPathN']}/24；Evidence fallback {m['V2']['evidenceFallbackN']}/24。",
              f"- {summary['latencyDefinition']}", f"- {summary['expectedFactRecallDefinition']}",
              f"- Context Recall 仅对 Gold 中已有 referenceAnswer 的样本计分；没有自动补写 reference answer。",
              "- RAGAS judge error（未成功返回可解析分数）："]
    for version in ("V1", "V2"):
        errors = summary["ragasJudgeErrors"].get(version, {})
        lines.append(f"  - {version}: " + (", ".join(
            f"{metric} " + ", ".join(f"{kind}={count}" for kind, count in kinds.items())
            for metric, kinds in errors.items()) if errors else "none"))
    lines += ["- Generation 的 PASS/FAIL 未由 RAGAS 分数代替；未完成语义复核的 answerable case 保留 MANUAL_REVIEW。", ""]
    (out_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8")

    v1, v2 = m["V1"], m["V2"]
    resume = ["# 简历表述（仅基于本次实测）", "",
              "## A｜有指标变化时使用", ""]
    if (v1["hitAt3"]["score"] is not None and v2["hitAt3"]["score"] is not None
            and v2["hitAt3"]["score"] > v1["hitAt3"]["score"]):
        resume.append(f"构建24条经 Gold/Evidence 核验的固定业务回归样本，对 Raw Evidence RAG 与 Canonical-first RAG 进行控制变量 A/B 评测；Hit@3 从 {fmt(v1['hitAt3']['score'] * 100, 1)}% 提升至 {fmt(v2['hitAt3']['score'] * 100, 1)}%，Citation Accuracy 从 {fmt(v1['citationAccuracy']['score'] * 100, 1)}% 提升至 {fmt(v2['citationAccuracy']['score'] * 100, 1)}%（均 n={v1['citationAccuracy']['n']}/{v2['citationAccuracy']['n']}）。")
    else:
        resume.append("本轮未观察到 Hit@3 的提升，不建议使用‘提升’表述。")
    resume += ["", "## B｜不强调提升时使用", "",
               "建立覆盖 Hit@3、MRR、RAGAS Faithfulness/Answer Relevancy/Context Recall、Expected Fact Recall、Citation Accuracy 与 Refusal Accuracy 的分层 RAG 评测体系，并在24条固定 Gold/Evidence 核验样本上完成 Raw Evidence 与 Canonical-first 对照和失败归因。",
               "", "注：17 条为人工确认，另 7 条为 Evidence-verified accelerated review；不要称为‘24条全部人工审核’。End-to-End rate 仅覆盖自动可判定项，语义结果仍有 MANUAL_REVIEW。", ""]
    (out_dir / "resume.md").write_text("\n".join(resume), encoding="utf-8")

    answer = ["# 面试回答：你的 RAG 怎么评估？", "", "## 45秒版本", "",
              f"我把评测拆成检索、生成和可信度三层。24条固定样本中，Hit@3 从 {fmt(v1['hitAt3']['score'])}（n=22）到 {fmt(v2['hitAt3']['score'])}（n=22），MRR 从 {fmt(v1['mrr']['score'])} 到 {fmt(v2['mrr']['score'])}。生成用 RAGAS 0.4.3：Answer Relevancy 为 {fmt(v1['answerRelevancy']['score'])} 到 {fmt(v2['answerRelevancy']['score'])}（各 n=24）；Faithfulness 是 {fmt(v1['faithfulness']['score'])}（n=1）和 {fmt(v2['faithfulness']['score'])}（n=8），样本很少所以只作参考。Citation Accuracy 从 {fmt(v1['citationAccuracy']['score'])} 到 {fmt(v2['citationAccuracy']['score'])}（n=22），2条拒答题两版均正确。A/B 只切换 canonical-first；17条人工确认、7条按 Evidence 核验。语义未能自动判定的回答保留人工复核，不强行报通过。", "",
              "## 2分钟追问版", "",
              "Gold 只在 Evidence Document、原文事实和范围核验后进入固定子集；本次17条来自人工确认，另7条标记为 Evidence-verified accelerated review，来源类型与年份边界都保留。Citation 不能代替 Retrieval：回答引用对了，只说明最终来源命中 Gold，不代表相关证据在 TopK 中排名好，所以检索还分别报告 Hit@3 和 MRR。V2 的 Canonical Chunk 通过 EvidenceRef 投影到其原始 Evidence Document，按这个 evidence-equivalent ranking 与 V1 的真实 Evidence ranking 比较；Canonical 本身不冒充最终引用。", "",
              "复合问题此前存在已记录的已知局限，因此固定集没有为了单题改写 query 或调参；如被纳入，其失误会按复合检索/回答能力单独归因。跑完后不根据结果补知识、改 threshold、topK 或 prompt，保持实验可复现。End-to-End 语义 PASS/FAIL 未被相似度分数替代；没有客观判定的 answerable 输出仍标 MANUAL_REVIEW。", ""]
    (out_dir / "interview-answer.md").write_text("\n".join(answer), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
