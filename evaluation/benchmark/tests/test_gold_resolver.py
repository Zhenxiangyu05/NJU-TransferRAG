import unittest

from evaluation.benchmark.gold_resolver import (
    is_answerable_retrieval_scorable,
    is_context_recall_scorable,
    is_expected_fact_recall_scorable,
    is_refusal_scorable,
    resolve_expected_facts,
)
from evaluation.benchmark.metrics import gold_coverage, gold_expected_fact_recall


class GoldResolverTest(unittest.TestCase):
    def test_missing_override_uses_frozen_test_case_facts(self):
        self.assertEqual(
            resolve_expected_facts("TRAG-X", {"expectedFacts": ["frozen"]}, {}),
            ["frozen"],
        )

    def test_override_completely_replaces_frozen_facts(self):
        self.assertEqual(
            resolve_expected_facts(
                "TRAG-X", {"expectedFacts": ["old-1", "old-2"]},
                {"expectedFactsOverride": ["new"]},
            ),
            ["new"],
        )

    def test_complete_override_preserves_keep_facts_with_modified_fact(self):
        approved = ["KEEP fact A", "modified fact B", "KEEP fact C"]
        resolved = resolve_expected_facts(
            "TRAG-X", {"expectedFacts": ["old A", "old B", "old C"]},
            {"expectedFactsOverride": approved},
        )
        self.assertEqual(resolved, approved)
        self.assertEqual(len(resolved), 3)

    def test_expected_fact_scorer_uses_resolved_complete_override(self):
        label = {"reviewStatus": "CONFIRMED", "answerable": True,
                 "expectedFactsOverride": ["KEEP A", "modified B", "KEEP C"]}
        score = gold_expected_fact_recall(
            "TRAG-X", {"expectedFacts": ["old A", "old B", "old C"]}, label,
            ["KEEP A; modified B; KEEP C"],
        )
        self.assertEqual(score, 1.0)

    def test_expected_fact_scorer_does_not_score_unanswerable_history(self):
        label = {"reviewStatus": "CONFIRMED", "answerable": False,
                 "expectedDocumentIds": [], "expectedFactsStatus": "NEEDS_FIX"}
        self.assertIsNone(gold_expected_fact_recall(
            "TRAG-X", {"expectedFacts": ["historical fact"]}, label,
            ["historical fact"],
        ))

    def test_empty_override_is_valid_and_does_not_fall_back(self):
        self.assertEqual(
            resolve_expected_facts(
                "TRAG-X", {"expectedFacts": ["historical"]},
                {"expectedFactsOverride": []},
            ),
            [],
        )

    def test_invalid_override_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "expectedFactsOverride"):
            resolve_expected_facts("TRAG-X", {}, {"expectedFactsOverride": "not-an-array"})

    def test_unanswerable_is_refusal_only_not_answerable_metrics(self):
        label = {
            "reviewStatus": "CONFIRMED", "answerable": False,
            "expectedDocumentIds": [], "expectedFactsStatus": "NEEDS_FIX",
        }
        self.assertFalse(is_answerable_retrieval_scorable(label))
        self.assertTrue(is_refusal_scorable(label))
        self.assertFalse(is_expected_fact_recall_scorable(label, ["historical fact"]))
        self.assertFalse(is_context_recall_scorable({**label, "referenceAnswer": "old"}))

    def test_answerable_retrieval_requires_confirmed_and_nonempty_evidence_ids(self):
        label = {"reviewStatus": "CONFIRMED", "answerable": True, "expectedDocumentIds": [7]}
        self.assertTrue(is_answerable_retrieval_scorable(label))
        self.assertFalse(is_answerable_retrieval_scorable(
            {**label, "reviewStatus": "NEEDS_REVIEW"}))
        self.assertFalse(is_answerable_retrieval_scorable(
            {**label, "expectedDocumentIds": []}))

    def test_context_recall_requires_confirmed_answerable_reference(self):
        label = {"reviewStatus": "CONFIRMED", "answerable": True, "referenceAnswer": "answer"}
        self.assertTrue(is_context_recall_scorable(label))
        self.assertFalse(is_context_recall_scorable({**label, "referenceAnswer": None}))
        self.assertFalse(is_context_recall_scorable({**label, "answerable": False}))

    def test_coverage_counts_only_confirmed_metric_eligible_labels(self):
        gold = [
            {"caseId": "A", "reviewStatus": "CONFIRMED", "answerable": True,
             "expectedDocumentIds": [7], "expectedFactsStatus": "SUPPORTED",
             "referenceAnswer": "ref", "expectedFactsAvailable": True},
            {"caseId": "B", "reviewStatus": "CONFIRMED", "answerable": True,
             "expectedDocumentIds": [], "expectedFactsStatus": "SUPPORTED",
             "referenceAnswer": None, "expectedFactsAvailable": True},
            {"caseId": "C", "reviewStatus": "CONFIRMED", "answerable": False,
             "expectedDocumentIds": [], "expectedFactsStatus": "NEEDS_FIX",
             "referenceAnswer": None, "expectedFactsAvailable": True},
            {"caseId": "D", "reviewStatus": "NEEDS_REVIEW", "answerable": False,
             "expectedDocumentIds": [99], "expectedFactsAvailable": True},
        ]
        test_cases = {
            "A": {"expectedFacts": ["a"]},
            "B": {"expectedFacts": ["b"]},
            "C": {"expectedFacts": ["historical c"]},
            "D": {"expectedFacts": ["unreviewed d"]},
        }
        coverage = gold_coverage(gold, test_cases)
        self.assertEqual(coverage["confirmedTotal"], 3)
        self.assertEqual(coverage["confirmedAnswerable"], 2)
        self.assertEqual(coverage["confirmedUnanswerable"], 1)
        self.assertEqual(coverage["scorable"], {
            "hitAt3": 1, "mrr": 1, "citationAccuracy": 1,
            "refusalAccuracy": 1, "contextRecall": 1,
            "expectedFactRecall": 2,
        })

    def test_legacy_sidecar_without_override_remains_compatible(self):
        legacy_label = {"caseId": "TRAG-X", "reviewStatus": "CONFIRMED", "answerable": True,
                        "expectedDocumentIds": [7]}
        coverage = gold_coverage([legacy_label], {"TRAG-X": {"expectedFacts": ["legacy fact"]}})
        self.assertEqual(coverage["scorable"]["expectedFactRecall"], 1)


if __name__ == "__main__":
    unittest.main()
