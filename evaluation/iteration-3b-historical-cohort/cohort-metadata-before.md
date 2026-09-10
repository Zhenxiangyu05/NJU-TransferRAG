# Year / Cohort Iteration 3B — Metadata Before

Captured before Iteration 3B changes on 2026-09-09.

## Confirmed target documents

| Document | Title | Document.year | Source type | Existing chunks | Chunks with cohortYear |
|---:|---|---:|---|---:|---:|
| 19 | 南京大学技术科学试验班新生生存指南 | 2026 | COMMUNITY | 7 | 0 |
| 25 | 数理大类生存指北 | 2026 | PERSONAL | 3 | 0 |
| 28 | 南京大学人文科学试验班求生指南 | 2026 | PERSONAL | 7 | 0 |
| 29 | 地球科学与资源环境类生存指南 | 2026 | PERSONAL | 5 | 0 |

Document years are retained as imported document-version metadata and are not changed
to match historical student cohorts.

## Ground-truth cohort expressions

- Document 28 explicitly states `2023级` for the 110-person humanities allocation.
- Document 29 explicitly states `2023级` for the geoscience allocation counts.
- Document 25 explicitly labels the allocation plan `2024级`; the same guide has a
  `2025级` document/title context, so fixed-size chunking currently mixes two cohort
  meanings in its first chunk.
- Document 19 states that its scope is primarily `2025级` and repeats `2025级` in the
  distribution/ranking section.

Ordinary `2023年`/`2024年` mentions are not treated as cohort evidence.

## Baseline retrieval failure

TRAG-031, TRAG-041, and TRAG-049 resolve the number in `YYYY级` through the legacy
year path and filter by `effectiveYear`. Because their Documents are versioned 2026,
the correct chunks are excluded before vector ranking. TRAG-034, TRAG-036, and
TRAG-048 also have explicit cohort wording but no chunk-level cohort metadata.

## 3A invariant

Official structured-policy document 6 is `62 MySQL chunks / 62 Qdrant points`, status
`OK`, with 0 issues. It is not part of the 3B reindex set.
