# Metadata Iteration 2B — Query Resolution After

The structured debug endpoint is `GET /api/test/query-resolution?query=...`. The complete machine-readable snapshots are in `query-resolution-after.json`.

| Case | Resolved departments | Resolved majors/programs | Matched entity and role | Final department filter | Correct Chunk rank | Classification |
|---|---|---|---|---|---|---|
| TRAG-064 | 现代工程与应用科学学院 | 光电信息科学与工程 | 光电信息类 → 光电信息科学与工程 (`TARGET`) | 现代工程与应用科学学院 strict | c87 #1, c88 #2 | PASS |
| TRAG-066 | 现代工程与应用科学学院 | 光电信息科学与工程 | 转光电 → 光电信息科学与工程 (`TARGET`) | 现代工程与应用科学学院 strict | c88 #2 | PASS |
| TRAG-067 | 现代工程与应用科学学院 | 光电系统信息材料实验班 | 光材 → 光电系统信息材料实验班 (`TARGET`) | 现代工程与应用科学学院 strict | c87 #1 | PASS |
| TRAG-073 | 电子科学与工程学院 | — | 电子学院 → 电子科学与工程学院 (`TARGET`) | 电子科学与工程学院 strict | c95 #1, c94 #2 | PARTIAL |
| TRAG-074 | 电子科学与工程学院 | — | 软院 → 软件学院 (`EXCLUDED`); 电子学院 → 电子科学与工程学院 (`TARGET`) | 电子科学与工程学院 strict only | c98 #1, c97 #3, c96 #4 | PASS |
| TRAG-075 | 电子科学与工程学院 | — | 电子学院 → 电子科学与工程学院 (`TARGET`) | 电子科学与工程学院 strict | c98 #1 | PASS |
| TRAG-076 | 电子科学与工程学院 | 电子信息类 | 电子专业 → 电子信息类 (`TARGET`) | 电子科学与工程学院 strict | c99 #2 | PARTIAL |
| TRAG-077 | 电子科学与工程学院 | 电子信息类 | 转电子 → 电子信息类 (`TARGET`) | 电子科学与工程学院 strict | c95 #1, c94 #2 | PASS |

## Safety behavior

- Longest non-overlapping match now determines both text expansion and structured resolved entities.
- `EXCLUDED` and `COMPARISON` entities may still be expanded in the normalized query, but never enter `resolvedDepartments` or `resolvedMajors`.
- Bare `电子` and `光电` are reported as `AMBIGUOUS`; they do not create a hard department filter.
- An alias span with multiple distinct canonical meanings is also marked `AMBIGUOUS` and does not create a hard filter.
- Year and experience flags remain the pre-existing implementation.
