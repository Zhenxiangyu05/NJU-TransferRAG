import unittest
import json
from pathlib import Path

from evaluation.benchmark.metrics import (
    citation_accuracy,
    delta,
    hit_at_k,
    paired_classification,
    percentile,
    reciprocal_rank,
    render_summary_markdown,
    refusal_accuracy,
    evidence_equivalent_ranking,
    expected_fact_recall,
    gold_coverage,
    selected_ranking,
    summarize,
)


class BenchmarkMetricsTest(unittest.TestCase):
    def test_hit_at_3(self):
        self.assertEqual(hit_at_k([9, 4, 2], [2]), 1.0)
        self.assertEqual(hit_at_k([9, 4, 2, 1], [1]), 0.0)
        self.assertIsNone(hit_at_k([1], []))
        self.assertIsNone(hit_at_k(None, [1]))

    def test_mrr(self):
        self.assertEqual(reciprocal_rank([9, 4, 2], [4]), 0.5)
        self.assertEqual(reciprocal_rank([], [4]), 0.0)
        self.assertIsNone(reciprocal_rank([], None))
        self.assertIsNone(reciprocal_rank(None, [4]))

    def test_citation_accuracy_and_na(self):
        self.assertTrue(citation_accuracy([3, 20], [20]))
        self.assertFalse(citation_accuracy([3], [20]))
        self.assertIsNone(citation_accuracy([3], []))

    def test_refusal_accuracy_requires_no_citation(self):
        self.assertTrue(refusal_accuracy(False, "根据资料无法确定。", []))
        self.assertFalse(refusal_accuracy(False, "可以确定。", []))
        self.assertFalse(refusal_accuracy(False, "无法确定。", [7]))
        self.assertIsNone(refusal_accuracy(True, "无法确定。", []))

    def test_nearest_rank_percentiles(self):
        self.assertEqual(percentile([40, 10, 30, 20], 50), 20)
        self.assertEqual(percentile([40, 10, 30, 20], 95), 40)
        self.assertEqual(percentile([], 95), None)

    def test_delta_and_na(self):
        self.assertAlmostEqual(delta(0.4, 0.6), 0.2)
        self.assertIsNone(delta(None, 0.6))

    def test_v1_v2_comparison_and_manual_review_signal(self):
        v1 = {"answerableGold": False, "answer": "可以确定", "citationDocumentIds": []}
        v2 = {"answerableGold": False, "answer": "无法确定", "citationDocumentIds": []}
        self.assertEqual(paired_classification(v1, v2), "V1_FAIL_V2_PASS")
        self.assertEqual(paired_classification(v2, v1), "V1_PASS_V2_FAIL")
        self.assertEqual(paired_classification(v1, v1), "BOTH_FAIL")
        self.assertEqual(paired_classification(None, v2), "MISSING")

    def test_summary_preserves_na(self):
        result = summarize([{"latencyMs": 10, "hitAt3": None, "reciprocalRank": None}])
        self.assertEqual(result["latencyMs"]["p50"], 10)
        self.assertIsNone(result["hitAt3"]["score"])
        self.assertEqual(result["hitAt3"]["n"], 0)

    def test_canonical_evidence_projection_preserves_rank_and_deduplicates(self):
        ranking = [{"chunkId": 10}, {"chunkId": 20}, {"chunkId": 30}]
        refs = [{"canonicalChunkId": 10, "evidenceDocumentId": 7},
                {"canonicalChunkId": 20, "evidenceDocumentId": 3},
                {"canonicalChunkId": 20, "evidenceDocumentId": 7},
                {"canonicalChunkId": 30, "evidenceDocumentId": 9}]
        self.assertEqual(evidence_equivalent_ranking(ranking, refs), [7, 3, 9])

    def test_fallback_uses_evidence_ranking_not_canonical_projection(self):
        self.assertEqual(selected_ranking("EVIDENCE", True, [7, 3], [20, 3]), [20, 3])
        self.assertEqual(selected_ranking("CANONICAL", False, [7, 3], [20, 3]), [7, 3])
        self.assertIsNone(selected_ranking(None, None, None, None))

    def test_expected_fact_recall_uses_retrieved_context_only_and_na(self):
        self.assertEqual(expected_fact_recall(["学制4年", "总学分144"], ["学制4年；总学分144"]), 1.0)
        self.assertEqual(expected_fact_recall(["学制4年", "总学分144"], ["学制4年"]), 0.5)
        self.assertIsNone(expected_fact_recall(["事实"], []))
        self.assertIsNone(expected_fact_recall([], ["上下文事实"]))

    def test_gold_coverage_counts_only_confirmed_labels(self):
        result = gold_coverage([
            {"caseId": "A", "reviewStatus": "CONFIRMED", "answerable": True,
             "expectedDocumentIds": [2], "expectedFactsStatus": "SUPPORTED",
             "referenceAnswer": "x", "expectedFactsAvailable": True},
            {"caseId": "B", "reviewStatus": "NEEDS_REVIEW", "answerable": False,
             "expectedDocumentIds": [3], "referenceAnswer": "y", "expectedFactsAvailable": True},
            {"caseId": "C", "reviewStatus": "NOT_SCORABLE", "expectedFactsAvailable": False},
        ], {
            "A": {"expectedFacts": ["fact"]},
            "B": {"expectedFacts": ["fact"]},
            "C": {"expectedFacts": []},
        })
        self.assertEqual(result["answerableConfirmed"], 1)
        self.assertEqual(result["unanswerableConfirmed"], 0)
        self.assertEqual(result["expectedDocumentIdsConfirmed"], 1)
        self.assertEqual(result["referenceAnswerConfirmed"], 1)
        self.assertEqual(result["expectedFactsConfirmed"], 1)
        self.assertEqual(result["hitAt3Scorable"], 1)
        self.assertEqual(result["contextRecallScorable"], 1)
        self.assertEqual(result["notScorable"], 1)

    def test_full_gold_review_covers_frozen_82_and_preserves_interview_subset(self):
        root = Path(__file__).resolve().parents[3]
        cases = json.loads((root / "evaluation/test-cases.json").read_text(encoding="utf-8"))
        sidecar = json.loads((root / "evaluation/benchmark/gold-labels.json").read_text(encoding="utf-8"))
        subset = json.loads((root / "evaluation/benchmark/interview-benchmark-24.json")
                            .read_text(encoding="utf-8"))
        self.assertEqual(82, len(cases))
        self.assertEqual([row["caseId"] for row in cases], [row["caseId"] for row in sidecar["cases"]])
        confirmed = [row for row in sidecar["cases"] if row["reviewStatus"] == "CONFIRMED"]
        confirmed_ids = {row["caseId"] for row in confirmed}
        self.assertEqual(82, len(confirmed))
        self.assertEqual(71, sum(row["answerable"] is True for row in confirmed))
        self.assertEqual(11, sum(row["answerable"] is False for row in confirmed))
        self.assertEqual({row["caseId"] for row in cases}, confirmed_ids)
        self.assertEqual(0, sum(row["reviewStatus"] == "NEEDS_REVIEW" for row in sidecar["cases"]))
        subset_ids = {row["caseId"] for row in subset["cases"]}
        self.assertEqual(24, len(subset_ids))
        self.assertTrue(subset_ids < confirmed_ids)
        self.assertEqual("8c34ccdf16d283231ff972ca8860fff3baaa28a4", subset["productionLogicVersion"])
        confirmed_reference_ids = {row["caseId"] for row in confirmed if row.get("referenceAnswer")}
        self.assertEqual(20, len(confirmed_reference_ids))
        self.assertTrue({"TRAG-002", "TRAG-048", "TRAG-049"} <= confirmed_reference_ids)
        self.assertEqual(17, sum(row.get("reviewSource") == "manual_review_batch_1" for row in confirmed))
        self.assertEqual(7, sum(row.get("reviewSource") == "evidence_verified_accelerated_review"
                                for row in confirmed))
        self.assertEqual(58, sum(row.get("reviewSource") == "evidence_verified_full_review"
                                 for row in confirmed))
        self.assertTrue(all(row.get("reviewStatus") == "CONFIRMED"
                            for row in sidecar["cases"]
                            if row["caseId"] in {"TRAG-011", "TRAG-012", "TRAG-076"}))
        by_gold_id = {row["caseId"]: row for row in sidecar["cases"]}
        self.assertEqual(by_gold_id["TRAG-034"]["expectedFactsOverride"], [
            "智能科学与技术", "自动化（机器人方向）", "集成电路设计与集成系统", "数字经济",
        ])
        self.assertEqual(by_gold_id["TRAG-037"]["expectedFactsOverride"], [
            "该指南所列2025级自动化（机器人方向）大一下准入课包括数据结构与算法设计。",
            "该指南所列2025级自动化（机器人方向）大一下准入课包括机器人与自动化导论。",
        ])
        self.assertEqual(by_gold_id["TRAG-038"]["expectedFactsOverride"], [
            "该指南所列2025级集成电路设计与集成系统方向大一下准入课包括信息科学中的物理学（下）。",
            "该指南所列2025级集成电路设计与集成系统方向大一下准入课包括电路分析。",
        ])
        self.assertEqual(by_gold_id["TRAG-069"]["expectedFactsOverride"], [
            "2024年37报名30录取约81%", "2025年38报名、24接收，表列报录比63%。",
        ])
        self.assertEqual(by_gold_id["TRAG-082"]["expectedFactsOverride"], [
            "大气动力学以Navier-Stokes方程为核心",
            "大气物理缺少兼顾可靠和实用的第一性原理",
            "大气物理研究Navier-Stokes方程中的非绝热加热项和湍流混合项。",
        ])
        for case_id in ("TRAG-032", "TRAG-047"):
            self.assertIs(by_gold_id[case_id]["answerable"], False)
            self.assertEqual(by_gold_id[case_id]["expectedDocumentIds"], [])
            self.assertNotIn("expectedFactsOverride", by_gold_id[case_id])
        self.assertTrue(all({"caseId", "answerable", "expectedDocumentIds", "referenceAnswer",
                             "reviewStatus", "notes"}.issubset(row) for row in sidecar["cases"]))

        test_cases_by_id = {row["caseId"]: row for row in cases}
        coverage = gold_coverage(sidecar["cases"], test_cases_by_id)
        self.assertEqual({"hitAt3": 71, "mrr": 71, "citationAccuracy": 71,
                          "refusalAccuracy": 11, "contextRecall": 20,
                          "expectedFactRecall": 71}, coverage["scorable"])
        saved_coverage = json.loads((root / "evaluation/benchmark/gold-coverage.json").read_text(encoding="utf-8"))
        self.assertEqual(coverage["scorable"], saved_coverage["scorable"])
        self.assertEqual(coverage["confirmedTotal"], saved_coverage["confirmedTotal"])

    def test_render_includes_p95_and_n_a(self):
        result = summarize([{"latencyMs": 20, "hitAt3": None, "reciprocalRank": None}])
        md = render_summary_markdown({
            "baselineHead": "frozen",
            "caseIds": ["case-1"],
            "limitations": ["context unavailable"],
            "metrics": {"V1": result, "V2": result},
        })
        self.assertIn("P95 Latency (ms)", md)
        self.assertIn("N/A", md)


if __name__ == "__main__":
    unittest.main()
