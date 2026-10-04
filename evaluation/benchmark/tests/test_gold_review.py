import unittest

from evaluation.benchmark.build_gold_review import build_artifacts
from evaluation.benchmark.sync_gold_review import apply_decisions, parse_human_decisions


class GoldReviewWorkflowTest(unittest.TestCase):
    def test_generated_candidates_never_become_confirmed_or_expose_canonical_id(self):
        cases = [{
            "caseId": "TRAG-001", "question": "2026级甲专业学制几年？",
            "sourceFile": "甲培养方案.pdf", "department": "甲学院", "sourceYear": 2026,
            "category": "培养方案", "expectedFacts": ["学制四年"], "evidenceText": "学制四年",
        }]
        old_gold = {"cases": []}
        history = {"TRAG-001": {"classification": "PASS", "report": "history.json"}}
        docs = {
            "甲培养方案": [
                {"historicalDocumentId": 7, "title": "甲培养方案", "sourceType": "OFFICIAL_PDF"},
                {"historicalDocumentId": 23, "title": "甲培养方案", "sourceType": "PERSONAL"},
            ]
        }
        gold, review, coverage = build_artifacts(cases, old_gold, history, docs)
        row = gold["cases"][0]
        self.assertEqual(row["reviewStatus"], "NEEDS_REVIEW")
        self.assertIsNone(row["answerable"])
        self.assertIsNone(row["expectedDocumentIds"])
        self.assertEqual(row["suggestedExpectedDocumentIds"], [7])
        self.assertEqual([item["documentId"] for item in row["candidateEvidenceDocuments"]], [7, None])
        self.assertIn("Human Decision:", review)
        self.assertEqual(coverage["scorable"]["hitAt3"], 0)

    def test_regeneration_preserves_evidence_verified_confirmation(self):
        cases = [{"caseId": "TRAG-001", "question": "2026级甲专业学制几年？",
                  "sourceFile": "甲培养方案.pdf", "department": "甲学院", "sourceYear": 2026,
                  "category": "培养方案", "expectedFacts": ["学制四年"], "evidenceText": "学制四年"}]
        verified = {"caseId": "TRAG-001", "answerable": True, "expectedDocumentIds": [7],
                    "expectedFactsStatus": "SUPPORTED", "reviewStatus": "CONFIRMED",
                    "reviewSource": "evidence_verified_accelerated_review",
                    "verifiedEvidence": [{"documentId": 7, "documentRole": "EVIDENCE",
                                          "sourceType": "OFFICIAL_PDF"}],
                    "reviewerNotes": "direct source verification"}
        gold, _, _ = build_artifacts(cases, {"cases": [verified]}, {}, {})
        row = gold["cases"][0]
        self.assertEqual(row["reviewStatus"], "CONFIRMED")
        self.assertEqual(row["expectedDocumentIds"], [7])
        self.assertEqual(row["reviewSource"], "evidence_verified_accelerated_review")
        self.assertEqual(row["verifiedEvidence"], verified["verifiedEvidence"])

    def test_regeneration_preserves_full_review_refusal_with_audited_scope(self):
        cases = [{"caseId": "TRAG-008", "question": "2026年考试形式？",
                  "sourceFile": "指南.pdf", "department": "甲学院", "sourceYear": 2026,
                  "category": "面试/机试", "expectedFacts": ["形式"], "evidenceText": "历史资料"}]
        reviewed = {"caseId": "TRAG-008", "answerable": False, "expectedDocumentIds": [],
                    "expectedFactsStatus": "NEEDS_FIX", "reviewStatus": "CONFIRMED",
                    "reviewSource": "evidence_verified_full_review", "verifiedEvidence": [],
                    "reviewedEvidenceScope": {"documentRole": "EVIDENCE", "documentIds": [1, 2]},
                    "reviewerNotes": "Only previous-year evidence exists."}
        gold, _, coverage = build_artifacts(cases, {"cases": [reviewed]}, {}, {})
        self.assertEqual(gold["cases"][0]["reviewStatus"], "CONFIRMED")
        self.assertEqual(gold["cases"][0]["expectedDocumentIds"], [])
        self.assertEqual(gold["cases"][0]["reviewedEvidenceScope"], reviewed["reviewedEvidenceScope"])
        self.assertEqual(coverage["scorable"]["refusalAccuracy"], 1)

    def test_review_document_partitions_cases_and_has_reference_categories(self):
        cases = [
            {"caseId": "TRAG-001", "question": "学制几年？", "sourceFile": "甲.pdf",
             "department": "甲学院", "sourceYear": 2026, "category": "明确事实",
             "expectedFacts": ["四年"], "evidenceText": "学制四年。"},
            {"caseId": "TRAG-002", "question": "为何这么安排又要如何申请？", "sourceFile": "乙.pdf",
             "department": "乙学院", "sourceYear": 2026, "category": "组合问题",
             "expectedFacts": ["事实"], "evidenceText": "原文。"},
        ]
        gold, review, _ = build_artifacts(cases, {"cases": []}, {}, {})
        self.assertEqual({row["reviewBatch"] for row in gold["cases"]}, {"Batch 1", "Batch 4"})
        self.assertIn("## TRAG-001", review)
        self.assertIn("## TRAG-002", review)
        self.assertNotIn("UNANSWERABLE", gold["referenceAnswerRecommendedCaseIds"])

    def test_regenerating_review_preserves_unapplied_human_fields(self):
        cases = [{"caseId": "TRAG-001", "question": "学制几年？", "sourceFile": "甲.pdf",
                 "department": "甲学院", "sourceYear": 2026, "category": "明确事实",
                 "expectedFacts": ["四年"], "evidenceText": "学制四年。"}]
        previous_review = """## TRAG-001
Human Decision:
answerable: true
expectedDocumentIds: [7]
expectedFactsStatus: SUPPORTED
referenceAnswer: HUMAN_SENTINEL
reviewStatus: CONFIRMED
"""
        _, review, _ = build_artifacts(cases, {"cases": []}, {}, {}, previous_review)
        self.assertIn("referenceAnswer: HUMAN_SENTINEL", review)

    def test_sync_reads_only_explicit_human_decisions(self):
        markdown = """## TRAG-001

Human Decision:
answerable: true
expectedDocumentIds: [7]
expectedFactsStatus: SUPPORTED
referenceAnswer: 根据官方资料，学制为四年。
reviewStatus: CONFIRMED
"""
        decisions = parse_human_decisions(markdown)
        gold = {"cases": [{"caseId": "TRAG-001", "reviewStatus": "NEEDS_REVIEW"}]}
        updated, count = apply_decisions(gold, decisions)
        self.assertEqual(count, 1)
        self.assertEqual(updated["cases"][0]["reviewStatus"], "CONFIRMED")
        self.assertEqual(updated["cases"][0]["expectedDocumentIds"], [7])

    def test_sync_rejects_answerable_without_evidence_document(self):
        gold = {"cases": [{"caseId": "TRAG-001", "reviewStatus": "NEEDS_REVIEW"}]}
        decisions = {"TRAG-001": {
            "reviewStatus": "CONFIRMED", "answerable": "true", "expectedDocumentIds": "[]",
            "expectedFactsStatus": "SUPPORTED",
        }}
        with self.assertRaisesRegex(ValueError, "requires at least one"):
            apply_decisions(gold, decisions)


if __name__ == "__main__":
    unittest.main()
