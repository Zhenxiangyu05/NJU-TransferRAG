# Year / Cohort Iteration 3B — Comparison

## 1. Scope and implementation

Iteration 3B added conservative historical-cohort support for ordinary personal/community guides. It reused the existing `Chunk.cohortYear` and Qdrant `cohortYear` payload and added no schema field. The new lightweight `CohortAwareTextChunker` recognizes only explicit `YYYY级`, establishes cohort-aware section boundaries, and delegates final size-based splitting to the existing `TextChunker`. The 3A `StructuredPolicyChunker` behavior was not changed.

Query resolution now recognizes explicit `YYYY级` outside policy queries. Retrieval for these ordinary-guide queries filters by `cohortYear`, not `Document.year`/`effectiveYear`; a no-exact-hit fallback accepts only untagged related guide chunks and does not admit a different tagged cohort.

## 2. Reindex and consistency

Only ordinary-guide documents 19, 25, 28, and 29 were rebuilt and reindexed. Their final one-to-one MySQL/Qdrant counts are respectively 34/34, 8/8, 8/8, and 13/13. A repeated index-only pass preserved the same counts, so the operation is idempotent. All four consistency reports are `OK` with 0 issues. Document 6 was not reindexed and remains `OK` at 62/62 with 0 issues.

`Document.year` remains 2026 for the affected documents. Chunk cohorts now reflect only text-supported student cohorts: doc19 = 2025; doc25 = 2025 and 2024; doc28 = 2023 plus one untagged chunk; doc29 = one 2023 chunk and twelve untagged chunks.

## 3. Test results

- Targeted cohort/query/retrieval tests: 65 tests, 0 failures, 0 errors, 0 skipped.
- Full Maven test suite: 110 tests, 0 failures, 0 errors, 0 skipped.
- This includes all six 3A structured-policy chunker tests.

## 4. Evaluation summary

| Set | Total | PASS | REFUSAL_FALSE_NEGATIVE | Other failures | SYSTEM_ERROR | Pass rate |
|---|---:|---:|---:|---:|---:|---:|
| Smoke | 15 | 14 | 1 | 0 | 0 | 93.33% |
| Year Regression | 24 | 19 | 5 | 0 | 0 | 79.17% |

Smoke's only failure is TRAG-062. The correct evidence was available, so this is not a historical-cohort metadata failure.

## 5. Historical-cohort targets

| Case | Question | Document.year | Resolved cohort | Target chunk cohort | Final time filter | Correct chunk | Rank | Classification |
|---|---|---:|---:|---:|---|---:|---:|---|
| TRAG-031 | 2023级人文大类110人的分流和转专业人数如何分布？ | 2026 | 2023 | 2023 | `cohortYear = 2023` | 504 | 1 | REFUSAL_FALSE_NEGATIVE |
| TRAG-041 | 地学大类2023级分流去向人数是多少？ | 2026 | 2023 | 2023 | `cohortYear = 2023` | 518 | 1 | PASS |
| TRAG-049 | 2024级数理大类计划分流比例和人数如何分配？ | 2026 | 2024 | 2024 | `cohortYear = 2024` | 497 | 1 | PASS |
| TRAG-034 | 2025级起技术科学试验班主要有哪四个分流方向？ | 2026 | 2025 | 2025 | `cohortYear = 2025` | 463 | 1 | PASS |
| TRAG-036 | 2025级技科分流加权排名主要参考哪些课程？ | 2026 | 2025 | 2025 | `cohortYear = 2025` | 463 | 1 | REFUSAL_FALSE_NEGATIVE |
| TRAG-048 | 2025级数理大类包含哪些学院方向？ | 2026 | 2025 | 2025 | `cohortYear = 2025` | 495 | 2 | PASS |

All six targets now resolve the intended cohort and retrieve the correct chunk in TopK. TRAG-031 and TRAG-036 still refuse despite rank-1 correct evidence; they are classified as Answerability failures, not year metadata or retrieval failures.

## 6. 3A policy regression guard

TRAG-015, TRAG-016, TRAG-018, TRAG-019, TRAG-028, and TRAG-072 all PASS. The 3A policy cycle/cohort behavior is stable.

For policy questions without a sufficiently explicit cohort/stage, TRAG-023 and TRAG-027 retrieve both relevant policy cohorts but Answerability refuses. TRAG-025 passes. No wrong cohort was silently forced.

## 7. Regressions and remaining failures

- Smoke baseline-PASS regressions: 0.
- Year Regression baseline-PASS regressions: 1, TRAG-071. It passed in the Smoke run but refused in the later Year run with correct evidence still in TopK, indicating Answerability nondeterminism rather than a cohort regression.
- New `WRONG`: 0.
- New `YEAR_MISMATCH`: 0.
- New `WRONG_SOURCE`: 0.

Remaining Year Regression failures are:

- Answerability after correct historical-cohort retrieval: TRAG-031, TRAG-036.
- Underspecified policy cohort/stage followed by Answerability refusal: TRAG-023, TRAG-027.
- Answerability/nondeterministic regression with correct evidence: TRAG-071.

No evaluated failure required `factYear`; no such field was added. Source Authority and generation were not changed in this iteration.

## 8. Acceptance conclusion

Iteration 3B meets its historical-cohort metadata and retrieval objective: explicit ordinary-guide cohorts are separated from document version years, affected indexes are consistent and idempotent, all six target documents' correct chunks reach TopK, and 3A policy behavior has no regression. The remaining failures are downstream Answerability/cohort-ambiguity behavior and are intentionally left for later iterations.
