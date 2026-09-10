# Year / Cohort Iteration 3B — Metadata After

Captured after the final Iteration 3B reindex and read-only consistency verification on 2026-09-09.

## Extraction and boundary rules

- Ordinary guide cohort metadata is extracted only from an explicit `YYYY级` expression.
- An ordinary `YYYY年`, a publication/import year, and digits in course names are not cohort evidence.
- A lightweight `CohortAwareTextChunker` divides reliable cohort sections before delegating text splitting to the existing `TextChunker`; the existing `TextChunker` itself was not rewritten.
- A cohort is inherited only within the same explicit section. A different `YYYY级` or a new top-level heading stops local inheritance; uncertain chunks remain `cohortYear = null`.
- A document-wide default is accepted only from a high-confidence guide title or an early explicit scope statement.
- `Document.year` remains the document version year (2026 for all four affected documents).
- No `factYear` or other time field was added.

## Final affected-document state

| Document | Title | Document.year | MySQL chunks | Qdrant points | cohortYear distribution | Consistency |
|---:|---|---:|---:|---:|---|---|
| 19 | 南京大学技术科学试验班新生生存指南 | 2026 | 34 | 34 | 2025: 34 | OK, 0 issues |
| 25 | 数理大类生存指北 | 2026 | 8 | 8 | 2025: 6; 2024: 2 | OK, 0 issues |
| 28 | 南京大学人文科学试验班求生指南 | 2026 | 8 | 8 | 2023: 7; null: 1 | OK, 0 issues |
| 29 | 地球科学与资源环境类生存指南 | 2026 | 13 | 13 | 2023: 1; null: 12 | OK, 0 issues |

Null cohort metadata is intentional where the text does not establish a reliable cohort. Consistency warnings about pre-existing null chunk department/major values are not MySQL/Qdrant mismatches.

## Reindex and idempotency

Only documents 19, 25, 28, and 29 were rebuilt/reindexed. A second index-only run produced the same one-to-one counts (34/34, 8/8, 8/8, and 13/13), with no duplicate chunks, duplicate points, or orphan points.

Official structured-policy document 6 was not reindexed. Its final state remains 62 MySQL chunks / 62 Qdrant points, status `OK`, 0 issues.

## Retrieval behavior

An explicit ordinary-guide cohort query now resolves `cohortYear` independently of `Document.year` and applies `cohortYear == requestedYear`. If the exact cohort search is empty, fallback searches only related untagged guide chunks; it does not treat `Document.year` as equivalent to cohort and excludes chunks tagged with another cohort.

