"""Resolve frozen test facts against optional, complete Gold fact overrides."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any


def resolve_expected_facts(case_id: str, test_case: Mapping[str, Any],
                           gold_label: Mapping[str, Any]) -> list[str]:
    """Return the full Gold fact list; an override replaces, never patches, dataset facts."""
    if "expectedFactsOverride" in gold_label:
        facts = gold_label["expectedFactsOverride"]
        source = "expectedFactsOverride"
    else:
        facts = test_case.get("expectedFacts", [])
        source = "test-cases.json expectedFacts"
    if not isinstance(facts, list) or any(not isinstance(fact, str) for fact in facts):
        raise ValueError(f"{case_id}: {source} must be an array of strings")
    return list(facts)


def is_confirmed(gold_label: Mapping[str, Any]) -> bool:
    return gold_label.get("reviewStatus") == "CONFIRMED"


def is_answerable_retrieval_scorable(gold_label: Mapping[str, Any]) -> bool:
    ids = gold_label.get("expectedDocumentIds")
    return (is_confirmed(gold_label) and gold_label.get("answerable") is True
            and isinstance(ids, list) and bool(ids))


def is_refusal_scorable(gold_label: Mapping[str, Any]) -> bool:
    return is_confirmed(gold_label) and gold_label.get("answerable") is False


def is_expected_fact_recall_scorable(gold_label: Mapping[str, Any],
                                     expected_facts: list[str]) -> bool:
    return (is_confirmed(gold_label) and gold_label.get("answerable") is True
            and bool(expected_facts))


def is_context_recall_scorable(gold_label: Mapping[str, Any]) -> bool:
    return (is_confirmed(gold_label) and gold_label.get("answerable") is True
            and isinstance(gold_label.get("referenceAnswer"), str)
            and bool(gold_label["referenceAnswer"].strip()))
