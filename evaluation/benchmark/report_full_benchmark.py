#!/usr/bin/env python3
"""Report one new, frozen full V1/V2 trace without making any model calls."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

try:
    from .metrics import (citation_accuracy, gold_expected_fact_recall, hit_at_k,
                          mean, percentile, reciprocal_rank, refusal_accuracy)
except ImportError:
    from metrics import (citation_accuracy, gold_expected_fact_recall, hit_at_k,
                         mean, percentile, reciprocal_rank, refusal_accuracy)

ROOT = Path(__file__).resolve().parents[2]
FREEZE = ROOT / "evaluation/benchmark/full-gold-freeze.json"
SUBSET = ROOT / "evaluation/benchmark/full-benchmark-cases.json"
GOLD = ROOT / "evaluation/benchmark/gold-labels.json"
DATASET = ROOT / "evaluation/test-cases.json"
METRICS = ("hitAt3", "mrr", "citationAccuracy", "refusalAccuracy", "answerRelevancy",
           "faithfulness", "contextRecall", "expectedFactRecall")
PAIRED = ("hitAt3", "mrr", "citationAccuracy", "answerRelevancy")
FAILURE_TYPES = ("RETRIEVAL_MISS", "LOW_RANK", "NOT_ANSWERABLE", "INCORRECT_REFUSAL",
                 "FAILED_TO_REFUSE", "WRONG_CITATION", "WRONG_YEAR", "WRONG_DEPARTMENT",
                 "UNSUPPORTED_CLAIM", "COMPOUND_QUERY_LIMITATION", "PROVIDER_FAILURE",
                 "OTHER", "MANUAL_SEMANTIC_REVIEW")
EVIDENCE_IDS = set([1, 2, 3, 4, 5, *range(7, 22)])


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def selected_ranking(row: dict[str, Any]) -> list[int] | None:
    if row.get("executionStatus") != "COMPLETED":
        return None
    if row.get("retrievalLayer") == "CANONICAL" and row.get("fallback") is False:
        candidates = row.get("evidenceEquivalentRanking") or []
    elif row.get("retrievalLayer") == "EVIDENCE":
        candidates = ((row.get("evidenceFallbackRanking") if row.get("fallback") else
                       row.get("rawRetrievedCandidates")) or [])
    else:
        candidates = []
    return [int(candidate["documentId"]) for candidate in candidates
            if candidate.get("documentId") is not None]


def is_refusal(answer: str | None) -> bool:
    compact = re.sub(r"\s+", "", answer or "")
    return any(token in compact for token in ("无法确定", "资料不足", "无法回答", "没有足够资料"))


def valid_evidence_citation(citations: list[int] | None,
                            expected: list[int] | None) -> bool | None:
    if not expected:
        return None
    actual = [int(document_id) for document_id in citations or []]
    return bool(citation_accuracy(actual, expected)) and all(
        document_id in EVIDENCE_IDS for document_id in actual)


def route(row: dict[str, Any]) -> str:
    if row.get("retrievalLayer") == "CANONICAL" and row.get("fallback") is False:
        return "CANONICAL_DIRECT"
    if row.get("fallback") is True and row.get("retrievalLayer") == "EVIDENCE":
        return "EVIDENCE_FALLBACK"
    if row.get("fallback") is False and is_refusal(row.get("answer")):
        return "REFUSAL_BEFORE_FALLBACK"
    return "OTHER"


def classify(row: dict[str, Any], label: dict[str, Any], ranking: list[int] | None) -> tuple[str, str | None]:
    if row.get("executionStatus") != "COMPLETED":
        category = "PROVIDER_FAILURE" if row.get("providerStage") else "OTHER"
        return "FAIL", category
    citations = row.get("finalCitationDocumentIds") or []
    if label["answerable"] is False:
        return ("PASS", None) if refusal_accuracy(False, row.get("answer"), citations) else ("FAIL", "FAILED_TO_REFUSE")
    expected = set(label["expectedDocumentIds"])
    relevant = [i for i, document_id in enumerate(ranking or [], start=1) if document_id in expected]
    if not relevant:
        return "FAIL", "RETRIEVAL_MISS"
    if relevant[0] > 3:
        return "FAIL", "LOW_RANK"
    if is_refusal(row.get("answer")):
        return "FAIL", "NOT_ANSWERABLE" if row.get("fallbackReason") == "NOT_ANSWERABLE" else "INCORRECT_REFUSAL"
    if not valid_evidence_citation(citations, list(expected)):
        return "FAIL", "WRONG_CITATION"
    return "MANUAL_REVIEW", "MANUAL_SEMANTIC_REVIEW"


def metric_mean(values: list[float | bool | None]) -> dict[str, Any]:
    scored = [float(value) for value in values
              if value is not None and math.isfinite(float(value))]
    return {"score": mean(scored), "n": len(scored)}


def paired_metric(v1: dict[str, dict[str, Any]], v2: dict[str, dict[str, Any]],
                  case_ids: list[str], key: str) -> dict[str, Any]:
    pairs = [(float(v1[c][key]), float(v2[c][key])) for c in case_ids
             if v1[c][key] is not None and v2[c][key] is not None
             and math.isfinite(float(v1[c][key])) and math.isfinite(float(v2[c][key]))]
    one = mean([a for a, _ in pairs])
    two = mean([b for _, b in pairs])
    return {"v1": one, "v2": two, "delta": two - one if one is not None and two is not None else None,
            "pairedN": len(pairs)}


def fmt(value: float | None, digits: int = 3) -> str:
    return "N/A" if value is None else f"{value:.{digits}f}"


def write_csv(path: Path, columns: list[str], rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def validate_inputs(result_dir: Path) -> tuple[list[str], dict[str, Any], dict[str, Any], dict[str, list[dict[str, Any]]], dict[str, Any]]:
    freeze, subset = load(FREEZE), load(SUBSET)
    if sha256(DATASET) != freeze["testCasesHash"] or sha256(GOLD) != freeze["goldLabelsHash"]:
        raise SystemExit("Gold or frozen test-cases changed after full benchmark freeze")
    case_ids = [item["caseId"] for item in subset["cases"]]
    if len(case_ids) != freeze["confirmed"] or len(case_ids) <= 24:
        raise SystemExit("Full benchmark list is not the frozen confirmed Gold set")
    cases = {item["caseId"]: item for item in json.loads(DATASET.read_text(encoding="utf-8"))}
    gold = {item["caseId"]: item for item in load(GOLD)["cases"]}
    if case_ids != [case["caseId"] for case in cases.values() if gold[case["caseId"]]["reviewStatus"] == "CONFIRMED"]:
        raise SystemExit("Full benchmark order differs from confirmed Gold")
    raw = {version: read_jsonl(result_dir / f"raw-{version.lower()}.jsonl") for version in ("V1", "V2")}
    for version, rows in raw.items():
        if [row["caseId"] for row in rows] != case_ids:
            raise SystemExit(f"{version} did not execute every frozen case exactly once in order")
        metadata = load(result_dir / f"trace-metadata-{version.lower()}.json")
        if metadata["caseCount"] != len(case_ids) or metadata["productionLogicVersion"] != freeze["productionLogicVersion"]:
            raise SystemExit(f"{version} trace metadata mismatch")
    ragas = load(result_dir / "ragas-full.json")
    if (ragas.get("ragasVersion"), ragas.get("judgeModel"), ragas.get("judgeEmbeddingModel"),
            ragas.get("temperature")) != ("0.4.3", "deepseek-v4-flash", "bge-m3", 0):
        raise SystemExit("Full benchmark RAGAS judge configuration mismatch")
    if [(r["caseId"], r["version"]) for r in ragas["scores"]] != [
            (r["caseId"], r["version"]) for v in ("V1", "V2") for r in raw[v]]:
        raise SystemExit("Full RAGAS results do not match the fresh V1/V2 trace order")
    return case_ids, cases, gold, raw, ragas


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path)
    args = parser.parse_args()
    case_ids, cases, gold, raw, ragas = validate_inputs(args.result_dir)
    judge = {(entry["version"], entry["caseId"]): entry for entry in ragas["scores"]}
    by_version: dict[str, dict[str, dict[str, Any]]] = {}
    aggregates: dict[str, Any] = {}
    for version in ("V1", "V2"):
        measured: dict[str, dict[str, Any]] = {}
        for row in raw[version]:
            case_id = row["caseId"]
            label = gold[case_id]
            ranking = selected_ranking(row)
            expected = label["expectedDocumentIds"] if label["answerable"] else None
            status, failure = classify(row, label, ranking)
            ragas_row = judge[(version, case_id)]
            measured[case_id] = {
                "caseId": case_id, "version": version, "category": cases[case_id].get("category"),
                "sourceAuthority": ("UNANSWERABLE" if not label["answerable"] else
                                    "OFFICIAL_PDF" if any(d in {7, 20} for d in expected) else "PERSONAL"),
                "answerable": label["answerable"], "route": route(row),
                "hitAt3": hit_at_k(ranking, expected), "mrr": reciprocal_rank(ranking, expected),
                "citationAccuracy": valid_evidence_citation(row.get("finalCitationDocumentIds"), expected),
                "refusalAccuracy": refusal_accuracy(label["answerable"], row.get("answer"),
                                                     row.get("finalCitationDocumentIds")),
                "expectedFactRecall": gold_expected_fact_recall(case_id, cases[case_id], label,
                                                                 row.get("retrievedContexts")),
                "answerRelevancy": ragas_row.get("answerRelevancy"),
                "faithfulness": ragas_row.get("faithfulness"),
                "contextRecall": ragas_row.get("contextRecall") if label.get("referenceAnswer") else None,
                "latencyMs": row.get("latencyMs"), "executionStatus": row.get("executionStatus"),
                "infraRetry": int(row.get("infraRetry") or 0), "providerStage": row.get("providerStage"),
                "e2e": status, "failureCategory": failure,
            }
        by_version[version] = measured
        summary = {metric: metric_mean([measured[case_id][metric] for case_id in case_ids])
                   for metric in METRICS}
        latency = [float(measured[c]["latencyMs"]) for c in case_ids
                   if measured[c]["latencyMs"] is not None]
        summary["latencyMs"] = {"mean": mean(latency), "p50": percentile(latency, 50),
                                "p95": percentile(latency, 95), "n": len(latency)}
        summary["e2e"] = dict(Counter(measured[c]["e2e"] for c in case_ids))
        summary["failures"] = dict(Counter(measured[c]["failureCategory"] for c in case_ids
                                           if measured[c]["failureCategory"]))
        summary["executionErrors"] = sum(measured[c]["executionStatus"] != "COMPLETED" for c in case_ids)
        summary["infraRetries"] = sum(measured[c]["infraRetry"] for c in case_ids)
        aggregates[version] = summary
    paired = {key: paired_metric(by_version["V1"], by_version["V2"], case_ids, key) for key in PAIRED}
    routing = dict(Counter(by_version["V2"][c]["route"] for c in case_ids))
    raw_by_key = {(version, row["caseId"]): row for version, rows in raw.items() for row in rows}
    def missing_reason(entry: dict[str, Any], metric: str) -> str | None:
        error = entry.get(metric + "Error")
        if (metric == "faithfulness" and error == "ValueError"
                and not raw_by_key[(entry["version"], entry["caseId"])].get("approvedContexts")):
            return "APPROVED_CONTEXT_UNAVAILABLE"
        return error

    judge_errors = {
        version: {
            metric: dict(Counter(missing_reason(entry, metric) for entry in ragas["scores"]
                                 if entry["version"] == version and missing_reason(entry, metric)))
            for metric in ("answerRelevancy", "faithfulness", "contextRecall")
        }
        for version in ("V1", "V2")
    }
    freeze = load(FREEZE)
    summary = {"freeze": freeze, "benchmarkSize": len(case_ids), "executed": {"V1": len(raw["V1"]),
               "V2": len(raw["V2"])}, "metrics": aggregates, "paired": paired, "v2Routing": routing,
               "ragas": {"version": "0.4.3", "judgeModel": "deepseek-v4-flash",
                         "embeddingModel": "bge-m3", "temperature": 0,
                         "missingReasons": judge_errors},
               "expectedFactRecallDefinition": "CUSTOM METRIC: literal coverage of resolvedExpectedFacts in actual retrievedContexts; not RAGAS Context Recall",
               "e2eDefinition": "Deterministic PASS requires evidence-relative refusal without citation; objective retrieval/citation failures are FAIL; remaining answerable cases require MANUAL_SEMANTIC_REVIEW.",
               "latencyDefinition": "In-process request wall clock in the isolated benchmark host; not a production SLA."}
    (args.result_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    case_rows = [by_version[v][c] for v in ("V1", "V2") for c in case_ids]
    write_csv(args.result_dir / "case-results.csv", list(case_rows[0]), case_rows)
    paired_rows = []
    for case_id in case_ids:
        row: dict[str, Any] = {"caseId": case_id, "answerable": gold[case_id]["answerable"]}
        for key in PAIRED:
            a, b = by_version["V1"][case_id][key], by_version["V2"][case_id][key]
            row.update({f"V1_{key}": a, f"V2_{key}": b,
                        f"delta_{key}": float(b) - float(a) if a is not None and b is not None else None})
        row["V1_e2e"], row["V2_e2e"] = by_version["V1"][case_id]["e2e"], by_version["V2"][case_id]["e2e"]
        paired_rows.append(row)
    write_csv(args.result_dir / "paired-comparison.csv", list(paired_rows[0]), paired_rows)

    lines = ["# Full 82-case V1/V2 benchmark", "",
             f"- Gold: {freeze['confirmed']}/{freeze['totalCases']} confirmed; "
             f"{freeze['answerable']} answerable, {freeze['unanswerable']} refusal; "
             f"{freeze['referenceAnswers']} reference answers.",
             f"- New trace: V1 {len(raw['V1'])}, V2 {len(raw['V2'])}; production logic "
             f"`{freeze['productionLogicVersion']}`.",
             "- Only canonical-first changed: V1=false; V2=true. Same frozen data, models, prompts, TopK and thresholds.",
             "- RAGAS 0.4.3: deepseek-v4-flash, bge-m3, temperature=0.", "",
             "| Metric | V1 (n) | V2 (n) | Paired delta (pairedN) |", "|---|---:|---:|---:|"]
    for key in METRICS:
        a, b = aggregates["V1"][key], aggregates["V2"][key]
        delta = paired.get(key)
        lines.append(f"| {key} | {fmt(a['score'])} ({a['n']}) | {fmt(b['score'])} ({b['n']}) | "
                     f"{fmt(delta['delta'])} ({delta['pairedN']}) |" if delta else
                     f"| {key} | {fmt(a['score'])} ({a['n']}) | {fmt(b['score'])} ({b['n']}) | N/A |")
    for key in ("mean", "p50", "p95"):
        a, b = aggregates["V1"]["latencyMs"], aggregates["V2"]["latencyMs"]
        lines.append(f"| latency {key} ms | {fmt(a[key], 1)} ({a['n']}) | "
                     f"{fmt(b[key], 1)} ({b['n']}) | {fmt(b[key]-a[key], 1)} ({len(case_ids)}) |")
    lines += ["", "## Deterministic E2E (same 82-case denominator)", "",
              "Positive answers with correct objective retrieval and citation remain MANUAL_REVIEW until semantic review."]
    for version in ("V1", "V2"):
        e = aggregates[version]["e2e"]
        lines.append(f"- {version}: PASS={e.get('PASS',0)}, FAIL={e.get('FAIL',0)}, "
                     f"MANUAL_REVIEW={e.get('MANUAL_REVIEW',0)}; paired denominator={len(case_ids)}.")
    lines += ["", "## Missing judge scores (not zero)", ""]
    for version in ("V1", "V2"):
        for metric in ("answerRelevancy", "faithfulness", "contextRecall"):
            errors = judge_errors[version][metric]
            lines.append(f"- {version} {metric}: {sum(errors.values())} "
                         f"({', '.join(f'{name}={count}' for name, count in errors.items()) or 'none'}).")
    lines += ["", "## Limitations", "",
              "- Expected Fact Recall is a CUSTOM METRIC on resolvedExpectedFacts and actual retrieved context; it is not RAGAS Context Recall.",
              "- Judge parsing/provider failures are missing values, never zero. Faithfulness requires actual approvedContext; Context Recall requires a confirmed reference answer.",
              "- Latency is from this isolated benchmark host, not a production SLA."]
    (args.result_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    failures = ["# Failure analysis", ""]
    for version in ("V1", "V2"):
        failures += [f"## {version}", ""]
        for category in FAILURE_TYPES:
            ids = [c for c in case_ids if by_version[version][c]["failureCategory"] == category]
            failures.append(f"- {category}: {len(ids)}" + (f" ({', '.join(ids)})" if ids else ""))
        failures.append("")
    (args.result_dir / "failures.md").write_text("\n".join(failures), encoding="utf-8")

    routing_lines = ["# V2 routing", ""]
    for key in ("CANONICAL_DIRECT", "EVIDENCE_FALLBACK", "REFUSAL_BEFORE_FALLBACK", "OTHER"):
        ids = [c for c in case_ids if by_version["V2"][c]["route"] == key]
        routing_lines.append(f"- {key}: {len(ids)}/{len(case_ids)} ({100*len(ids)/len(case_ids):.1f}%).")
        eligible = [c for c in ids if gold[c]["answerable"]]
        if len(eligible) >= 5:
            for key_metric in ("hitAt3", "citationAccuracy"):
                m = metric_mean([by_version["V2"][c][key_metric] for c in eligible])
                routing_lines.append(f"  - {key_metric}: {fmt(m['score'])} (n={m['n']}).")
        else:
            routing_lines.append(f"  - answerable subset n={len(eligible)}; too small for a stable rate.")
    (args.result_dir / "routing-analysis.md").write_text("\n".join(routing_lines) + "\n", encoding="utf-8")

    categories: dict[tuple[str, str], list[str]] = defaultdict(list)
    for case_id in case_ids:
        row = by_version["V1"][case_id]
        categories[(str(row["category"]), row["sourceAuthority"])].append(case_id)
    category_lines = ["# Category and source-authority analysis", "",
                      "| Category | Authority | Cases | V1 Hit@3 (n) | V2 Hit@3 (n) | V1 Citation (n) | V2 Citation (n) |",
                      "|---|---|---:|---:|---:|---:|---:|"]
    for (category, authority), ids in sorted(categories.items()):
        values = [metric_mean([by_version[v][c][m] for c in ids])
                  for m in ("hitAt3", "citationAccuracy") for v in ("V1", "V2")]
        displays = [f"{fmt(x['score'])} ({x['n']})" for x in values]
        category_lines.append(f"| {category} | {authority} | {len(ids)} | " + " | ".join(displays) + " |")
    (args.result_dir / "category-analysis.md").write_text("\n".join(category_lines) + "\n", encoding="utf-8")

    hit = paired["hitAt3"]
    cite = paired["citationAccuracy"]
    if hit["delta"] is not None and hit["delta"] > 0 and cite["delta"] is not None and cite["delta"] > 0:
        resume = (f"在{len(case_ids)}条冻结 Gold 样本上对 Raw Evidence 与 Canonical-first RAG 做控制变量评测；"
                  f"成对样本中 Hit@3 从 {hit['v1']:.1%} 到 {hit['v2']:.1%}（n={hit['pairedN']}），"
                  f"Evidence Citation Accuracy 从 {cite['v1']:.1%} 到 {cite['v2']:.1%}（n={cite['pairedN']}）。")
    else:
        resume = (f"建立{len(case_ids)}条冻结 Gold 样本的分层 RAG 评测，完成 Raw Evidence 与 "
                  "Canonical-first 的成对检索、引用、拒答和生成对照及失败归因。")
    (args.result_dir / "resume.md").write_text("# 简历表述\n\n" + resume + "\n", encoding="utf-8")
    interview = ("# 面试回答：你的 RAG 怎么评估？\n\n## 45秒\n\n"
                 f"我用{len(case_ids)}条已确认 Gold 的固定问题做 V1/V2 对照，只切换 canonical-first。"
                 f"检索看成对 Hit@3 和 MRR：Hit@3 为 {fmt(paired['hitAt3']['v1'])} 到 "
                 f"{fmt(paired['hitAt3']['v2'])}（n={paired['hitAt3']['pairedN']}）。"
                 f"可信度看 Evidence 引用和{freeze['unanswerable']}条拒答题，引用准确率成对为 "
                 f"{fmt(cite['v1'])} 到 {fmt(cite['v2'])}（n={cite['pairedN']}）。"
                 "生成用同一 RAGAS Judge 评估相关性、忠实度和有 reference 的 Context Recall；"
                 "解析失败记为缺失值，语义无法自动确认的案例继续人工复核。\n\n"
                 "## 2分钟追问\n\n"
                 "Gold 按当前冻结知识库 Evidence 定义，不可回答题以拒答且无引用为正确。"
                 "V2 的 Canonical 排名通过 EvidenceRef 映射到原 Evidence，不能把 Canonical 当最终 Citation。"
                 "检索命中和最终引用分别计分；Expected Fact Recall 是字面覆盖自定义指标。"
                 "所有结果保留失败案例，且延迟只代表本次隔离环境。\n")
    (args.result_dir / "interview-answer.md").write_text(interview, encoding="utf-8")
    print(json.dumps({"size": len(case_ids), "v1Executed": len(raw["V1"]),
                      "v2Executed": len(raw["V2"]), "pairedHitN": hit["pairedN"],
                      "pairedCitationN": cite["pairedN"], "routing": routing}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
