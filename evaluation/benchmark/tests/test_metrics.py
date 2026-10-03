import unittest

from evaluation.benchmark.metrics import (
    citation_accuracy,
    delta,
    hit_at_k,
    paired_classification,
    percentile,
    reciprocal_rank,
    render_summary_markdown,
    refusal_accuracy,
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
