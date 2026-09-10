# Metadata Iteration 2B — Query Resolution Before

Captured before Iteration 2B implementation. The running baseline did not expose a structured query-resolution endpoint, so the values below are derived from the current `QueryRewriteService` implementation and the four persisted `entity_alias` rows.

| Case | Entity expression | Baseline match | Baseline department filter | Finding |
|---|---|---|---|---|
| TRAG-064 | 光电信息类 | `光电` → 光电系统信息材料实验班 (`PROGRAM`, department null) | none | Short substring match; no canonical department |
| TRAG-066 | 转光电 | `光电` → 光电系统信息材料实验班 (`PROGRAM`, department null) | none | Short substring match; no canonical department |
| TRAG-067 | 光材 | none | none | Alias missing |
| TRAG-073 | 电子学院 | none | none | Alias missing |
| TRAG-074 | 软院之外、电子学院 | `软院` → 软件学院 (`DEPARTMENT`, department null) | none | No exclusion role; target entity missing |
| TRAG-075 | 电子学院 | none | none | Alias missing |
| TRAG-076 | 电子专业 | none | none | Program-to-department mapping missing |
| TRAG-077 | 转电子 | none | none | Contextual program expression missing |

Additional baseline observations:

- Longest-match selection affected rewritten text only; every substring match was added to `matchedEntities` before overlap resolution.
- Retrieval derived strict departments from `matchedEntities`, so excluded/comparison entities could not be represented safely.
- Bare `电子` and `光电` had no ambiguity model.
- Existing alias rows before 2B: 光电系统信息材料实验班/光电 (department null), 软件学院/软院 (department null), 现代工程学院/现工 (department null), 汉语言文学/汉语言 (文学院).
