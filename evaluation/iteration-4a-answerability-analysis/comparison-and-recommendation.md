# Answerability Iteration 4A：结论与 4B 建议

## 1. 14 个案例重新分类

| 主因 | 数量 | caseId |
|---|---:|---|
| Checker false negative | 1 | TRAG-066 |
| Multi-source coverage | 0 | 无 |
| Structured output / parsing instability | 0 | 无 |
| Citation validation | 0 | 无 |
| Source pruning | 0 | 无 |
| Nondeterministic semantic judgement | 6 | TRAG-008、TRAG-009、TRAG-020、TRAG-026、TRAG-048、TRAG-060 |
| Underspecified query | 3 | TRAG-023、TRAG-025、TRAG-027 |
| 实际不是 Answerability | 4 | TRAG-003、TRAG-031、TRAG-037、TRAG-062 |


其中“真正发生 checker false negative”的可确认案例共 7 个：TRAG-008、TRAG-009、TRAG-020、TRAG-026、TRAG-048、TRAG-060、TRAG-066。前 6 个存在明确输出翻转，TRAG-066 是完整 S2 证据下的直接误拒绝。TRAG-037 的补充 trace 被 provider 429 污染，属于技术调用异常而非语义充分性判断，因此归入“实际不是 Answerability”，不计入 7 个。

TRAG-003、TRAG-031、TRAG-062 的完整证据均在 rank 4，而生产 `RagService` 只把 Top3 送给 Answerability；这 3 个不应继续计为 checker failure。此次没有找到纯粹的 Multi-source coverage 失败：原先怀疑的多 Source 案例要么完整证据在 Top3 外，要么单个 Source 已足够。

## 2. TRAG-023 / TRAG-025 / TRAG-027 的产品语义

三题均处于 2026 policy cycle 下存在 2025级/2024级两套规则、用户没有明确 applicantStage/cohort 的状态。合理行为不是 hard-code Ground Truth 中某一套，也不是统一返回“资料无法确定”。推荐：

1. 两套官方规则均完整召回时，同时按 cohort 标注返回两套规则，并提示用户确认自己属于哪一级；
2. 若任一 cohort 证据不完整，则返回明确的澄清问题；
3. 永远不要静默选取某一 cohort。

## 3. JSON、citation 与 source pruning

- 30 次稳定性调用中没有 JSON parse/schema 失败，也没有无效/空 citationId。
- 当前 structured output 只是 Prompt 约束，并非原生 response format，仍存在潜在解析风险。
- source pruning 确实存在：`selectEvidence` 只保留 checker citationIds。它不是这 14 个拒答案例的直接原因，但已与 TRAG-045、TRAG-056、TRAG-076 的 PARTIAL 关联。
- 技术异常会 fail-closed：TRAG-037 补充 trace 的 429 被转换为普通拒答，外部 HTTP 仍为 200，当前监控无法区分知识不足与 checker 故障。

## 4. 推荐的 Iteration 4B 最小方案

推荐采用“保留 Pre-generation Gate + 原子覆盖合同 + 判断/Source 选择解耦”的小步方案，不采用 Generate → Validate，也不复用 V2 的 FULL/PARTIAL/NONE 放宽。

最小改造范围：

1. Checker 使用独立 options，显式 `temperature=0`；provider 支持时启用原生 JSON Schema。
2. 输出改为 `decision`、固定 `requiredAspects` 的逐项 support、`missingAspects`、`evidenceCitationIds`、`reasonCode`；Java 根据所有 required aspects 是否被支持做最终决定。
3. `PARTIAL` 仍拒答；日期/数字/多子问题规则不放宽。
4. `AMBIGUOUS` 进入“多 cohort 标注回答或澄清”路径，不走通用拒答。
5. 区分 `INSUFFICIENT`、`PARSE_ERROR`、`MODEL_ERROR`；只对 transport/429/parse 做一次有界 retry，不对语义 insufficient 重试。
6. 记录安全的原始输出、parsed result、citation validation 和 decision reason，解决当前不可观测问题。
7. 4B 首个最小实验暂时保留现有 source pruning，避免把“判断逻辑变化”和“Generation 输入变化”同时上线。架构上应取消 checker 对 Source 的裁剪权，但应作为第二个独立开关验证：只有原 51 个 PASS guard、negative guard 以及 WRONG_SOURCE/HALLUCINATION 指标全部稳定后，才让 Generation 使用完整、已通过 Retrieval filter 的 Top3。

验收优先级：先保证 10 个 PASS guard 与 3 个 negative guard 全部稳定，再观察 7 个已确认 checker false negative；不能以单次 Pass Rate 提升换取 WRONG/WRONG_SOURCE/HALLUCINATION。

## 5. 不应期待 4B 解决的案例

- TRAG-003、TRAG-031、TRAG-062：完整证据不在生产 Top3，应交给 Retrieval/finalTopK 或证据窗口策略。
- TRAG-023、TRAG-025、TRAG-027：应走 cohort clarification/multi-cohort 产品语义。
- TRAG-037：需要 checker dependency error 可观测性和有界技术重试，不能用 Prompt 放宽解决。

## 6. Regression Set

`evaluation/answerability-regression-cases.json` 共 27 题：14 个当前目标、10 个 PASS guard、3 个 negative guard。negative guard 分别覆盖缺少具体日期时间、缺少具体数字、组合问题仅部分子问题有证据。
