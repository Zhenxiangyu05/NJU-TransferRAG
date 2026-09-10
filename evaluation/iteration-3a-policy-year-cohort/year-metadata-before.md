# Year / Cohort Iteration 3A — Metadata Before

Captured before Iteration 3A changes on 2026-09-09.

## Document 6

- Document year: `2026`
- Source type / scope: `OFFICIAL` / `GLOBAL`
- MySQL chunks: 39
- Qdrant points: 39
- Existing metadata consistency status: `OK` (under the legacy field semantics)
- Existing consistency warnings: 44 (mostly nullable structured chunk department/major metadata)

## Confirmed legacy year semantics

The structured policy chunker currently treats the official table's `年级` column as
`Chunk.policyYear`. Qdrant then derives `effectiveYear` from that value. Consequently,
rows from the 2026 policy document are stored as follows:

| Legacy chunk | Row | MySQL policyYear | Qdrant effectiveYear |
|---|---:|---:|---:|
| 116 | 汉语言文学 / 2025 cohort | 2025 | 2025 |
| 125 | 数学类 / 2025 cohort | 2025 | 2025 |
| 133 | 电子信息类 / 2025 cohort | 2025 | 2025 |
| 139 | 计算机科学与技术 / 2025 cohort | 2025 | 2025 |
| 140 | 计算机科学与技术 / 2024 cohort | 2024 | 2024 |
| 142 | 软件工程 / 2025 cohort | 2025 | 2025 |
| 143 | 软件工程 / 2024 cohort | 2024 | 2024 |

This makes an explicit `2026年转专业` query build `effectiveYear = 2026`, excluding
the applicable 2025/2024 cohort rows.

## Mixed-cohort boundaries

- Legacy chunks 121/122 cover adjacent Social School policy rows; metadata is cleared
  because a record boundary contains another cohort row or department.
- Legacy chunks 135/136 show the same pattern around the Electronic Science policy
  rows.
- The pre-change parser protects against unsafe metadata by setting fields to null,
  but does not split the mixed cohort boundary into independently filterable records.

## Baseline invariant

No Document metadata is to be changed: `Document.year = 2026` remains the document
version/cycle context. Only structured policy chunk semantics and the corresponding
Qdrant payload/filtering are in scope.
