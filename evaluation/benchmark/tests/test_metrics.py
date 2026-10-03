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
            {"reviewStatus": "CONFIRMED", "answerable": True,
             "expectedDocumentIds": [2], "expectedFactsStatus": "SUPPORTED",
             "referenceAnswer": "x", "expectedFactsAvailable": True},
            {"reviewStatus": "NEEDS_REVIEW", "answerable": False,
             "expectedDocumentIds": [3], "referenceAnswer": "y", "expectedFactsAvailable": True},
            {"reviewStatus": "NOT_SCORABLE", "expectedFactsAvailable": False},
        ])
        self.assertEqual(result["answerableConfirmed"], 1)
        self.assertEqual(result["unanswerableConfirmed"], 0)
        self.assertEqual(result["expectedDocumentIdsConfirmed"], 1)
        self.assertEqual(result["referenceAnswerConfirmed"], 1)
        self.assertEqual(result["expectedFactsConfirmed"], 1)
        self.assertEqual(result["hitAt3Scorable"], 1)
        self.assertEqual(result["contextRecallScorable"], 1)
        self.assertEqual(result["notScorable"], 1)

    def test_gold_sidecar_covers_frozen_82_without_auto_confirmation(self):
        root = Path(__file__).resolve().parents[3]
        cases = json.loads((root / "evaluation/test-cases.json").read_text(encoding="utf-8"))
        sidecar = json.loads((root / "evaluation/benchmark/gold-labels.json").read_text(encoding="utf-8"))
        self.assertEqual(82, len(cases))
        self.assertEqual([row["caseId"] for row in cases], [row["caseId"] for row in sidecar["cases"]])
        self.assertTrue(all(row["reviewStatus"] != "CONFIRMED" for row in sidecar["cases"]))
        self.assertTrue(all({"caseId", "answerable", "expectedDocumentIds", "referenceAnswer",
                             "reviewStatus", "notes"}.issubset(row) for row in sidecar["cases"]))

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
