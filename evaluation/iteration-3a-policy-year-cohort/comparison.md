# Year / Cohort Iteration 3A Comparison

## 1. Schema and metadata changes

`Chunk.cohortYear` (nullable integer) was added and is written to the Qdrant
`cohortYear` payload. The metadata consistency check now compares this field between
MySQL and Qdrant. Search/source DTOs expose it for diagnosis. Query resolution gained
`cycleYear`, `cohortYear`, `applicantStage`, and policy-context information while keeping
the existing `explicitYear`/`resolvedYear` compatibility fields.

No Answerability, generation prompt, authority ranking, EntityAlias, candidateTopK,
neighbor expansion, or threshold change was made.

## 2. StructuredPolicyChunker boundary behavior

The table's leading year is now parsed as a cohort boundary, including rows whose PDF
text contains only `year + quota` or `year` because repeated columns were omitted.
Each such row starts an independent policy record, so 2025级 and 2024级 rules no longer
share a Chunk. Split department labels are reconstructed from the nearest source lines
between cohort boundaries. If department/major cannot be supported reliably, it remains
null rather than being inferred from general knowledge.

## 3. Final time-field semantics

- `Document.year`: document version/publication context; doc6 remains 2026.
- `Chunk.policyYear`: transfer execution cycle; every valid doc6 policy row is 2026.
- `Chunk.cohortYear`: student cohort shown in the table, such as 2025级 or 2024级.
- `effectiveYear`: retained for compatibility; reindexed structured-policy rows use the
  cycle value 2026 and policy retrieval no longer treats it as the sole time meaning.

## 4. Stage resolution

Only in a high-confidence transfer-policy context:

- 大一 / 第一学年 → `FIRST_YEAR`
- 大二 / 第二学年 → `SECOND_YEAR`
- cycle 2026 + `FIRST_YEAR` → cohort 2025
- cycle 2026 + `SECOND_YEAR` → cohort 2024

Without a stage or explicit `xxxx级`, no cohort is derived. Academic-stage wording in
ordinary course-planning questions is not given policy semantics.

## 5. Retrieval filter change

An explicit policy cycle now uses `policyYear = cycleYear`. When cohort resolution is
high confidence, `cohortYear = resolvedCohortYear` is added. Without cohort/stage, only
the cycle is filtered, allowing both cohort rows into candidates. Non-policy retrieval
continues to use the existing `effectiveYear` path.

## 6. Reindex and consistency

Only official structured-policy document 6 was rebuilt/reindexed. The final state is
62 MySQL chunks and 62 Qdrant points. A second index-only execution remained 62/62.
Final consistency is `OK`, with 0 issues, no duplicate points, and no orphan points.
The 40 warnings are nullable structured fields unsupported by the extracted source,
not cross-store inconsistencies.

## 7. Tests

- Targeted Year/StructuredPolicy suite: **55 tests, 0 failures, 0 errors, 0 skipped**.
- Additional boundary regression after the split-department correction: **9 tests,
  0 failures, 0 errors, 0 skipped**.
- Final full Maven suite: **101 tests, 0 failures, 0 errors, 0 skipped**.

## 8. Smoke Set

Final: **15 total — PASS 11, REFUSAL_FALSE_NEGATIVE 4, SYSTEM_ERROR 0**.

Failures were TRAG-027, TRAG-034, TRAG-062, and TRAG-071. TRAG-071 is explicitly
nondeterministic Answerability behavior: the same case refused in Smoke but passed in
the later Year Regression run with correct evidence still in TopK. This is not a year
filter regression.

## 9. Year Regression Set

Final: **24 total — PASS 16, REFUSAL_FALSE_NEGATIVE 8, SYSTEM_ERROR 0**.
There were no PARTIAL, WRONG, WRONG_SOURCE, YEAR_MISMATCH, DEPARTMENT_MISMATCH, or
HALLUCINATION classifications in this run.

Failure allocation:

- 3B historical fact-year (deferred): TRAG-031, TRAG-041, TRAG-049.
- Generic guide/body cohort semantics (deferred): TRAG-034, TRAG-036, TRAG-048.
- Answerability after correct retrieval: TRAG-023, TRAG-027.
- Generation: 0 independently identified failures.
- Source Authority: 0 independently identified failures.
- Data Quality: 0 newly identified failures in this set.

## 10. Six primary targets

| Case | cycleYear | cohortYear | applicantStage | Final year filter | Correct Chunk rank | Result |
|---|---:|---:|---|---|---:|---|
| TRAG-015 | 2026 | — | — | `policyYear = 2026` | 1 (chunk 398, cohort 2025) | PASS |
| TRAG-016 | 2026 | — | — | `policyYear = 2026` | 1 (chunk 398, cohort 2025) | PASS |
| TRAG-018 | 2026 | — | — | `policyYear = 2026` | 1 (chunk 413, cohort 2025) | PASS |
| TRAG-019 | 2026 | — | — | `policyYear = 2026` | 1 (chunk 413, cohort 2025) | PASS |
| TRAG-028 | 2026 | 2025 | FIRST_YEAR | `policyYear = 2026 AND cohortYear = 2025` | 1 (chunk 404) | PASS |
| TRAG-072 | 2026 | 2025 | FIRST_YEAR | `policyYear = 2026 AND cohortYear = 2025` | 1 (chunk 427) | PASS |

All six moved from baseline failure to PASS. The 2026 cycle is no longer excluded by
2025/2024 cohort metadata, and first-year queries do not retrieve second-year rules.

## 11. Ambiguous no-stage cases

- TRAG-023: no cohort filter; both software rows entered TopK (2024 chunk 444 rank 1,
  2025 chunk 443 rank 2). Final Answerability refused: `REFUSAL_FALSE_NEGATIVE`.
- TRAG-025: no cohort filter; both computer rows entered TopK (2024 chunk 441 rank 1,
  2025 chunk 440 rank 2). The answer matched the expected facts: `PASS`.
- TRAG-027: no cohort filter; both software cohorts entered TopK at ranks 1 and 2.
  Final Answerability refused the combined evidence: `REFUSAL_FALSE_NEGATIVE`.

Thus Retrieval did not secretly force a cohort. The downstream choice/refusal for an
underspecified cohort remains future Answerability/Generation policy work.

## 12. Regression and acceptance

- Year Regression baseline-PASS regressions: **0**.
- Smoke observed one transient baseline-PASS refusal (TRAG-071), but it passed in the
  subsequent Year Regression run; evidence stayed in TopK. Classified as
  Answerability/nondeterministic, not a year regression.
- New WRONG / WRONG_SOURCE / YEAR_MISMATCH: **0 / 0 / 0**.
- No SYSTEM_ERROR occurred in either set.

Iteration 3A meets its acceptance target: official 2026 policy retrieval now separates
cycle and cohort, stage-constrained queries select the correct cohort, unspecified-stage
queries retain both cohorts, doc6 is consistent and idempotently indexed, and no new
year-related wrong answer was introduced. Iteration 3B has not been started.
