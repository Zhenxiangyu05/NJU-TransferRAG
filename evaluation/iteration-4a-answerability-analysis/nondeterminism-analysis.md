# Answerability 稳定性分析

## 1. 方法

对 TRAG-008、TRAG-025、TRAG-048、TRAG-009、TRAG-020、TRAG-060 各连续调用正式 `/api/rag/ask` 5 次。每次调用前独立执行 Top3 诊断，并对 rank、documentId、chunkId、score 和 content 计算证据哈希。未修改代码、索引或数据。

## 2. 结果

| caseId | sameEvidence | run1 | run2 | run3 | run4 | run5 | PASS | REFUSAL | varianceCause |
|---|---|---|---|---|---|---|---:|---:|---|
| TRAG-008 | 是 | REFUSAL | REFUSAL | REFUSAL | PASS | PASS | 2 | 3 | checker 对相同证据的年份/覆盖语义判断变化 |
| TRAG-025 | 是 | PASS | REFUSAL | PASS | REFUSAL | REFUSAL | 2 | 3 | checker 对相同证据的年份/覆盖语义判断变化 |
| TRAG-048 | 是 | PASS | REFUSAL | PASS | PASS | PASS | 4 | 1 | checker 对相同证据的年份/覆盖语义判断变化 |
| TRAG-009 | 是 | REFUSAL | PASS | PASS | REFUSAL | PASS | 3 | 2 | checker 对相同证据的年份/覆盖语义判断变化 |
| TRAG-020 | 是 | PASS | PASS | PASS | PASS | PASS | 5 | 0 | 五次内部稳定，但与正式 Full Regression 结果相反 |
| TRAG-060 | 是 | PASS | REFUSAL | REFUSAL | PASS | REFUSAL | 2 | 3 | checker 对相同证据的年份/覆盖语义判断变化 |


## 3. 严重程度

- 30 次运行共 18 次 PASS、12 次 REFUSAL，拒答比例为 40%。
- 6 个 case 的五次证据集合均完全一致。
- 5/6 个 case 在五次内部直接翻转；TRAG-020 五次均 PASS，但正式 Full Regression 曾拒答，因此 6/6 在“正式运行 + 重复运行”范围内都出现过翻转。
- 所有可成功解析的 `answerable=true` 结果，其 citationIds 均稳定：TRAG-008/009/020/025/060 使用 S1，TRAG-048 使用 S2。
- 30 次稳定性调用未观察到 JSON parse 或 citation validation 错误。
- 额外 trace 中 TRAG-037 出现一次 provider `429`；异常被静默转换为普通拒答，说明当前 False Refusal 指标中可能混入不可见的 checker 依赖错误。

## 4. 模型配置

- 实际模型：`deepseek-v4-flash`。
- `temperature`：未显式配置，不能认定为 0。
- `top_p`：未显式配置。
- structured output：`BeanOutputConverter` 仅把 JSON Schema 文本写进 Prompt，不是 provider 原生强制 `response_format`。
- timeout/retry：`AnswerabilityService` 未显式配置专用 timeout 或 retry；异常直接 fail-closed。
- Answerability 与最终 Generation 共用同一个 `ChatModel`。

即使后续显式设置 `temperature=0`，provider 的并行计算、模型服务版本、负载均衡和浮点实现仍可能带来微小非确定性；但当前未显式设置 temperature，使输出波动更没有约束。
