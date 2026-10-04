import unittest

from evaluation.benchmark.report_full_benchmark import paired_metric, valid_evidence_citation


class FullReportTest(unittest.TestCase):
    def test_paired_delta_uses_only_same_valid_cases(self):
        left = {"A": {"hitAt3": 0.0}, "B": {"hitAt3": 1.0}, "C": {"hitAt3": None}}
        right = {"A": {"hitAt3": 1.0}, "B": {"hitAt3": None}, "C": {"hitAt3": 1.0}}
        result = paired_metric(left, right, ["A", "B", "C"], "hitAt3")
        self.assertEqual(result, {"v1": 0.0, "v2": 1.0, "delta": 1.0, "pairedN": 1})

    def test_canonical_document_does_not_count_as_final_evidence_citation(self):
        self.assertTrue(valid_evidence_citation([20], [20]))
        self.assertFalse(valid_evidence_citation([20, 28], [20]))
        self.assertFalse(valid_evidence_citation([28], [20]))
        self.assertIsNone(valid_evidence_citation([], []))
