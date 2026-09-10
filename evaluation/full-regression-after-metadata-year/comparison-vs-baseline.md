# 阶段性全量回归与 Baseline 对比

## 核心对比

| 状态 | Baseline | Current | 变化 |
|---|---:|---:|---:|
| PASS | 36 | 51 | +15 |
| REFUSAL_FALSE_NEGATIVE | 38 | 24 | -14 |
| PARTIAL | 4 | 7 | +3 |
| WRONG | 1 | 0 | -1 |
| WRONG_SOURCE | 1 | 0 | -1 |
| YEAR_MISMATCH | 2 | 0 | -2 |
| DEPARTMENT_MISMATCH | 0 | 0 | 0 |
| HALLUCINATION | 0 | 0 | 0 |
| SYSTEM_ERROR | 0 | 0 | 0 |

| 指标 | 结果 |
|---|---|
| 当前 PASS / 82 | **51 / 82（62.20%）** |
| 相比 36 / 82 的提升 | **+15 个 PASS，+18.30 个百分点** |
| False Refusal | **38 → 24（-14）** |
| 原 36 个 PASS 保留 | **33** |
| 原 36 个 PASS regression | **3：TRAG-006、TRAG-008、TRAG-082** |
| 原 46 个 failure 修复 | **18** |
| 新增 WRONG | **0**；当前 WRONG 为 0 |
| 新增 WRONG_SOURCE | **0**；当前 WRONG_SOURCE 为 0 |
| 新增 YEAR_MISMATCH | **0**；当前 YEAR_MISMATCH 为 0 |
| HALLUCINATION | **0** |
| SYSTEM_ERROR | **0** |

## Regression 明细

| caseId | Baseline | Current | rootCause | 说明 |
|---|---|---|---|---|
| TRAG-006 | PASS | REFUSAL_FALSE_NEGATIVE | Source Authority | 查询被识别为官方政策查询并限定 policyYear，Ground Truth 的个人指南未进入 Top10。 |
| TRAG-008 | PASS | REFUSAL_FALSE_NEGATIVE | Answerability / Nondeterministic | 完整考核形式位于 Top1；原 baseline 同一问题曾 PASS，本次发生 Answerability 波动。 |
| TRAG-082 | PASS | PARTIAL | Generation | 回答给出了大气动力学以N-S方程为核心及大气物理缺少统一第一性原理，但遗漏非绝热加热和湍流混合等参数化问题。 |


## 已修复案例

原失败转 PASS 共 18 个：TRAG-014、TRAG-015、TRAG-016、TRAG-018、TRAG-019、TRAG-024、TRAG-028、TRAG-029、TRAG-034、TRAG-041、TRAG-049、TRAG-061、TRAG-064、TRAG-067、TRAG-068、TRAG-072、TRAG-074、TRAG-075。

| 主要关联迭代 | 数量 | caseId |
|---|---:|---|
| Metadata 2A（数据与索引一致性） | 3 | TRAG-064、TRAG-067、TRAG-075 |
| Metadata 2B（Entity Resolution） | 5 | TRAG-024、TRAG-029、TRAG-061、TRAG-068、TRAG-074 |
| Year/Cohort 3A/3B | 9 | TRAG-015、TRAG-016、TRAG-018、TRAG-019、TRAG-028、TRAG-034、TRAG-041、TRAG-049、TRAG-072 |
| 其他自然改善 | 1 | TRAG-014 |


## 当前失败根因

| 主根因 | 数量 | caseId |
|---|---:|---|
| Answerability | 11 | TRAG-003、TRAG-009、TRAG-020、TRAG-023、TRAG-026、TRAG-027、TRAG-031、TRAG-037、TRAG-060、TRAG-062、TRAG-066 |
| Answerability / Nondeterministic | 3 | TRAG-008、TRAG-025、TRAG-048 |
| Generation | 6 | TRAG-032、TRAG-043、TRAG-045、TRAG-056、TRAG-076、TRAG-082 |
| Retrieval | 4 | TRAG-005、TRAG-036、TRAG-042、TRAG-080 |
| Source Authority | 4 | TRAG-006、TRAG-007、TRAG-073、TRAG-077 |
| Entity Resolution | 2 | TRAG-038、TRAG-046 |
| Data Quality / Missing Knowledge | 1 | TRAG-070 |


## 十二项结论

1. 当前 PASS 为 **51 / 82（62.20%）**。
2. 相比 36 / 82 增加 **15 个 PASS**，通过率增加 **18.30 个百分点**。
3. False Refusal 从 **38 降至 24**。
4. 原 36 个 PASS 保留 **33 个**。
5. 原 46 个 failure 修复 **18 个**。
6. 没有新增 `WRONG`，且当前 `WRONG = 0`。
7. 没有新增 `WRONG_SOURCE`，且当前 `WRONG_SOURCE = 0`。
8. 没有新增 `YEAR_MISMATCH`，且当前 `YEAR_MISMATCH = 0`。
9. 本次没有 `HALLUCINATION`。
10. 当前最大失败根因是 Answerability：普通 11 个，波动型 3 个，合计 14 个。
11. 下一轮优先建议优化 Answerability 的证据充分性判定与确定性，但必须以 regression guard 保护原 PASS，避免重演 Iteration 1 的回归。
12. Metadata consistency 与 Year/Cohort 已不值得继续作为首要优化方向；大规模 Retrieval 扩容也暂非最高优先级。Source Authority 的4个案例和缺失知识TRAG-070应分别单独处理。
