# Metadata Iteration 2B — Comparison

## Scope and implementation

Iteration 2B changed only EntityAlias data, entity/query resolution, the handoff of resolved departments to the existing retrieval filter, a read-only diagnostic endpoint, and focused tests.

EntityAlias changed from 4 rows to 13 rows:

- Corrected `光电系统信息材料实验班 / 光材 / PROGRAM` and assigned canonical department `现代工程与应用科学学院`.
- Added canonical department to `软件学院 / 软院`.
- Canonicalized `现代工程学院 / 现工` to `现代工程与应用科学学院`, and added `现代工程学院` and `现工院` aliases.
- Added `计算机科学与技术 / MAJOR → 计算机学院`.
- Added high-confidence contextual mappings for `光电信息类` and `转光电` to `光电信息科学与工程 → 现代工程与应用科学学院`.
- Added `电子学院 → 电子科学与工程学院`.
- Added `电子信息类 / PROGRAM → 电子科学与工程学院`, including contextual forms `电子专业` and `转电子`.
- Removed the old unconditional `光电 → 光电系统信息材料实验班` mapping. No unconditional `电子` mapping was added.

The new structured result retains backward-compatible fields and adds `resolvedEntities`, `departments`, `majors`, and `ambiguousEntities`. Each resolved entity records its matched text and role (`TARGET`, `EXCLUDED`, `COMPARISON`, or `AMBIGUOUS`). Retrieval now consumes the structured target department list directly; ranking, threshold, year strategy, and fallback behavior were not changed.

## Longest-match and role behavior

Previously, substring matches were added to `matchedEntities` before longest-match selection, so a shorter alias could still leak into filtering. Now all candidate spans are collected first, longest non-overlapping spans are selected, and only those selected spans contribute resolved entities.

For `软院之外，电子学院...`, `软院` is preserved in normalized text but marked `EXCLUDED`; only `电子科学与工程学院` enters the strict filter. Prefix forms `除了X`, `不同于X`, and `相比X` are also covered. Ambiguous bare `电子`/`光电` never hard-filter.

## Tests

- Entity Resolution focused tests: 19/19 passed.
- QueryRewrite + Retrieval focused tests: 36/36 passed.
- Full Maven suite: 93/93 passed; 0 failures, 0 errors, 0 skipped.
- No SYSTEM_ERROR occurred in any evaluation set.

## Evaluation summary

| Set | Total | PASS | Refusal FN | PARTIAL | WRONG_SOURCE | SYSTEM_ERROR |
|---|---:|---:|---:|---:|---:|---:|
| Smoke | 15 | 12 | 2 | 0 | 1 | 0 |
| Metadata Regression | 22 | 16 | 3 | 3 | 0 | 0 |
| Entity/Metadata-related Retrieval Regression | 17 | 10 | 5 | 1 | 1 | 0 |

Smoke non-PASS cases are all pre-existing, out-of-scope failures: TRAG-034 (Year/cohort), TRAG-062 (retrieval/evidence coverage), and TRAG-027 (source authority). No baseline PASS guard regressed.

Metadata Regression non-PASS cases:

- TRAG-015, TRAG-016, TRAG-025: Year/cohort filtering; intentionally unchanged.
- TRAG-070: Ground Truth document remains unindexed; answer is partial.
- TRAG-073: correct doc14 chunks rank #1/#2, but generation omitted the possible subject areas.
- TRAG-076: correct chunk99 ranks #2, but generation says “四选一” instead of the Ground Truth “5门中至少1门” and omits the explicit 鼓楼 detail.

The additional retrieval run reproduced an Answerability/nondeterministic refusal on TRAG-074 even though the same case passed in Metadata Regression and the correct doc14 chunks ranked #1/#3/#4. This is not an Entity Resolution regression.

## Target-case outcome

All 8 target questions now resolve to the intended canonical department, and every expected target chunk is visible in TopK. Six cases pass end-to-end: TRAG-064, TRAG-066, TRAG-067, TRAG-074, TRAG-075, TRAG-077. TRAG-073 and TRAG-076 are PARTIAL due to generation/evidence coverage after correct retrieval.

TRAG-072, an additional retrieval-regression case using `转电子`, improved from baseline `YEAR_MISMATCH` to PASS. TRAG-070 deliberately remains ambiguous on bare `电子` and does not hard-filter; its missing Ground Truth document remains an Index/Data Quality task.

## Unchanged areas and next work

No Answerability, prompt, year/cohort strategy, retrieval ranking, candidateTopK, neighbor expansion, threshold, embedding, chunking, Document/Chunk metadata, or Qdrant point was modified. doc13/doc14 metadata from 2A remained untouched.

Remaining work belongs to later iterations:

- Year/cohort: TRAG-015, TRAG-016, TRAG-025, TRAG-034.
- Retrieval/evidence coverage: TRAG-062.
- Source authority: TRAG-027.
- Answerability nondeterminism: the extra-run TRAG-074 refusal.
- Generation/evidence coverage: TRAG-073 and TRAG-076.
- Index/Data Quality: TRAG-070 Ground Truth document is still missing.
