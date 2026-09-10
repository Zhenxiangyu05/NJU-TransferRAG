# Year / Cohort Iteration 3A — Metadata After

Final verification performed on 2026-09-09 after the final doc6 rebuild/reindex.

## Document 6 invariant

- `Document.year = 2026` (document version / transfer cycle context)
- `Document.department = 本科生院`
- `sourceType = OFFICIAL`
- `scope = GLOBAL`
- The Document row was not recreated or repurposed as a cohort.

## Final index state

- MySQL chunks: **62**
- Qdrant points: **62**
- Consistency status: **OK**
- Consistency issues: **0**
- Warnings: 40 nullable department/major fields where the PDF extraction does not
  support a reliable structured value; these are not MySQL/Qdrant mismatches.
- A second index-only run retained 62 points, confirming delete-and-replace indexing
  is idempotent and creates neither duplicates nor orphan points.

## Final structured metadata examples

| Current chunk | Policy row | policyYear | cohortYear | effectiveYear |
|---:|---|---:|---:|---:|
| 398 | 汉语言文学 / 2025级 | 2026 | 2025 | 2026 |
| 404 | 社会学院 / 2025级 | 2026 | 2025 | 2026 |
| 405 | 社会学院 / 2024级 | 2026 | 2024 | 2026 |
| 413 | 数学类 / 2025级 | 2026 | 2025 | 2026 |
| 427 | 电子信息类 / 2025级 | 2026 | 2025 | 2026 |
| 440 | 计算机科学与技术 / 2025级 | 2026 | 2025 | 2026 |
| 441 | 计算机科学与技术 / 2024级 | 2026 | 2024 | 2026 |
| 443 | 软件工程 / 2025级 | 2026 | 2025 | 2026 |
| 444 | 软件工程 / 2024级 | 2026 | 2024 | 2026 |

Rows beginning with a cohort year now form independent records even when the PDF
extract omits a repeated major name. Split two-line department labels are reconstructed
only from source text; values that remain unreliable stay null rather than being guessed.

## Query/filter verification

- `2026年 + 大一 + 转专业` resolves to cycle 2026, cohort 2025,
  `FIRST_YEAR`, and `policyYear = 2026 AND cohortYear = 2025`.
- `2026年 + 大二 + 转专业` resolves to cycle 2026, cohort 2024,
  `SECOND_YEAR`, and the corresponding two-field filter.
- `2026年 + 转专业` without a stage resolves only the cycle and filters only
  `policyYear = 2026`; it does not force a cohort.
- Non-policy questions retain the legacy `effectiveYear` behavior.

