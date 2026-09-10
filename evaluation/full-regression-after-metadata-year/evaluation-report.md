# TransferRAG 阶段性全量回归评测报告

## 1. 评测范围与运行状态

- 固定 Ground Truth：`evaluation/test-cases.json`，共 82 题；未新增、删除或修改任何题目及证据。
- 正式接口：`POST /api/rag/ask`，82 题串行执行并逐题原子保存。
- 诊断接口：全部 82 题均保存 `/api/test/search?topK=10` 结果。
- 运行前 doc6、doc13、doc14、doc19、doc25、doc28、doc29 consistency 均为 `OK`，0 issue。
- MySQL、Qdrant、Spring Boot、Ollama/bge-m3 均正常。
- 正式结果中 `SYSTEM_ERROR = 0`。
- 平均接口延迟：8498 ms；中位延迟：8326 ms。

## 2. 总体结果

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

- 当前 PASS：**51 / 82（62.20%）**。
- 相比 baseline 的 36 / 82（43.90%），增加 **15 个 PASS**，通过率提高 **18.30 个百分点**。
- False Refusal 从 38 降至 24，减少 14 个。
- 当前没有 `WRONG`、`WRONG_SOURCE`、`YEAR_MISMATCH`、`HALLUCINATION` 或 `SYSTEM_ERROR`。

## 3. 按问题类别统计

| 类别 | 测试数 | PASS | 非 PASS | 通过率 |
|---|---:|---:|---:|---:|
| 别名 | 1 | 1 | 0 | 100.00% |
| 培养方案 | 4 | 3 | 1 | 75.00% |
| 学习经验 | 7 | 6 | 1 | 85.71% |
| 时间敏感 | 1 | 1 | 0 | 100.00% |
| 明确事实 | 8 | 5 | 3 | 62.50% |
| 条件 | 6 | 4 | 2 | 66.67% |
| 组合问题 | 2 | 0 | 2 | 0.00% |
| 经验 | 5 | 1 | 4 | 20.00% |
| 综合生存指南 | 4 | 4 | 0 | 100.00% |
| 课程规划 | 15 | 8 | 7 | 53.33% |
| 转专业政策 | 16 | 11 | 5 | 68.75% |
| 面试/机试 | 12 | 6 | 6 | 50.00% |
| 风险经验 | 1 | 1 | 0 | 100.00% |


## 4. Baseline PASS 保留情况

- 原 36 个 PASS 中仍为 PASS：**33**。
- regression：**3**，分别为 TRAG-006、TRAG-008、TRAG-082。

| caseId | 当前状态 | 当前主根因 | 说明 |
|---|---|---|---|
| TRAG-006 | REFUSAL_FALSE_NEGATIVE | Source Authority | 查询被识别为官方政策查询并限定 policyYear，Ground Truth 的个人指南未进入 Top10。 |
| TRAG-008 | REFUSAL_FALSE_NEGATIVE | Answerability / Nondeterministic | 完整考核形式位于 Top1；原 baseline 同一问题曾 PASS，本次发生 Answerability 波动。 |
| TRAG-082 | PARTIAL | Generation | 回答给出了大气动力学以N-S方程为核心及大气物理缺少统一第一性原理，但遗漏非绝热加热和湍流混合等参数化问题。 |


## 5. 原失败修复情况

原 baseline 46 个非 PASS 中，有 **18 个转为 PASS**：TRAG-014、TRAG-015、TRAG-016、TRAG-018、TRAG-019、TRAG-024、TRAG-028、TRAG-029、TRAG-034、TRAG-041、TRAG-049、TRAG-061、TRAG-064、TRAG-067、TRAG-068、TRAG-072、TRAG-074、TRAG-075。

| 主要关联迭代 | 数量 | caseId |
|---|---:|---|
| Metadata 2A（数据与索引一致性） | 3 | TRAG-064、TRAG-067、TRAG-075 |
| Metadata 2B（Entity Resolution） | 5 | TRAG-024、TRAG-029、TRAG-061、TRAG-068、TRAG-074 |
| Year/Cohort 3A/3B | 9 | TRAG-015、TRAG-016、TRAG-018、TRAG-019、TRAG-028、TRAG-034、TRAG-041、TRAG-049、TRAG-072 |
| 其他自然改善 | 1 | TRAG-014 |


该归属按案例的主要修复机制互斥统计；部分案例可能同时受多项改造影响。

## 6. 当前剩余失败根因

| 主根因 | 数量 | caseId |
|---|---:|---|
| Answerability | 11 | TRAG-003、TRAG-009、TRAG-020、TRAG-023、TRAG-026、TRAG-027、TRAG-031、TRAG-037、TRAG-060、TRAG-062、TRAG-066 |
| Answerability / Nondeterministic | 3 | TRAG-008、TRAG-025、TRAG-048 |
| Generation | 6 | TRAG-032、TRAG-043、TRAG-045、TRAG-056、TRAG-076、TRAG-082 |
| Retrieval | 4 | TRAG-005、TRAG-036、TRAG-042、TRAG-080 |
| Source Authority | 4 | TRAG-006、TRAG-007、TRAG-073、TRAG-077 |
| Entity Resolution | 2 | TRAG-038、TRAG-046 |
| Data Quality / Missing Knowledge | 1 | TRAG-070 |


合并普通与波动型案例后，Answerability 共 14 个，是当前最大失败根因。所有 rootCause 均基于本次 TopK 和最终回答重新判断，未机械继承 baseline。

## 7. 已知重点案例复核

| caseId | 当前状态 | 正确 Chunk 排名 | 当前卡点 |
|---|---|---:|---|
| TRAG-023 | REFUSAL_FALSE_NEGATIVE | 1 | Answerability |
| TRAG-027 | REFUSAL_FALSE_NEGATIVE | 1 | Answerability |
| TRAG-031 | REFUSAL_FALSE_NEGATIVE | 4 | Answerability |
| TRAG-036 | REFUSAL_FALSE_NEGATIVE | Top10 未确定 | Retrieval |
| TRAG-062 | REFUSAL_FALSE_NEGATIVE | 4 | Answerability |
| TRAG-070 | PARTIAL | 1 | Data Quality / Missing Knowledge |
| TRAG-071 | PASS | Top10 未确定 | 无（本次 PASS） |
| TRAG-073 | REFUSAL_FALSE_NEGATIVE | Top10 未确定 | Source Authority |
| TRAG-074 | PASS | Top10 未确定 | 无（本次 PASS） |
| TRAG-076 | PARTIAL | 1 | Generation |


TRAG-071 与 TRAG-074 本次正式运行均为 PASS；它们此前出现过波动，但不计入本次失败。TRAG-023/027 的相关政策 cohort 均已进入 TopK，没有被强制选择错误 cohort。

## 8. 阶段结论

Metadata 与 Year/Cohort 改造带来了可观的整体提升，并消除了本次运行中的 `WRONG_SOURCE` 与 `YEAR_MISMATCH`。下一轮最值得优先处理 Answerability，尤其是“正确证据已在 Top1/Top4 仍拒答”和相同代码下结果波动的问题。当前不建议继续优先扩大 Metadata/Year 改造，也不建议先进行大规模 candidateTopK 或 Neighbor Expansion；这些模块在当前剩余失败中的覆盖面明显较小。
