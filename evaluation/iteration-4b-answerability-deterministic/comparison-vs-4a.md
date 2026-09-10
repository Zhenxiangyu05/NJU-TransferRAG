# Answerability Iteration 4B 与 4A 对比

## 1. 验收结论

Iteration 4B **不建议直接进入完整 82 题 Full Regression**。

虽然 semantic flip 从 4A 的 5/6 降至 1/6，负向保护全部保留，也没有发现新增 `WRONG`、`WRONG_SOURCE` 或 `HALLUCINATION`，但 7 个 confirmed target 仅 4 个在 27 题单次回归中回答成功，且其中 `TRAG-048` 仍不稳定；`TRAG-008`、`TRAG-060`、`TRAG-066` 仍误拒答。部分 flip 的消失来自“稳定地误拒答”，尚未满足 4B 的核心验收目标。

## 2. 27 题 Answerability Regression

调用完成 27/27，无 `TECHNICAL_ERROR`、无 `SYSTEM_ERROR`。

### 运行时响应

| 响应类型 | 数量 |
|---|---:|
| `ANSWERED` | 13 |
| `REFUSAL` | 10 |
| `AMBIGUOUS` | 4 |
| `TECHNICAL_ERROR` | 0 |
| `SYSTEM_ERROR` | 0 |

### 语义复核

| 结果 | 数量 | 说明 |
|---|---:|---|
| 满足预期 | 19 | 13 个正确回答、3 个预期歧义、3 个负向保护拒答 |
| `REFUSAL_FALSE_NEGATIVE` | 7 | `TRAG-003/008/031/037/060/062/066` |
| 未预期 `AMBIGUOUS` | 1 | `TRAG-029` |
| `WRONG` | 0 | 未发现 |
| `WRONG_SOURCE` | 0 | 未发现 |
| `HALLUCINATION` | 0 | 未发现重要无依据事实 |

所有 13 个实际生成回答均覆盖相应 expectedFacts，引用的学院、文档与内容相符。

## 3. 7 个 Confirmed Target

| caseId | 4B 单次回归 | 判断 |
|---|---|---|
| `TRAG-008` | `REFUSAL_FALSE_NEGATIVE` | Top1 含完整初试、复试形式，仍误拒答 |
| `TRAG-009` | PASS | 已改善；Stability 为 5/5 PASS |
| `TRAG-020` | PASS | 已改善并保持稳定；Stability 为 5/5 PASS |
| `TRAG-026` | PASS | 已改善；不在固定六题 Stability 中 |
| `TRAG-048` | PASS | 单次通过，但 Stability 仍为 3 PASS / 2 REFUSAL |
| `TRAG-060` | `REFUSAL_FALSE_NEGATIVE` | Top1 已给出平衡建议，仍被要求更具体策略 |
| `TRAG-066` | `REFUSAL_FALSE_NEGATIVE` | Top2 直接覆盖三个 expectedFacts，仍误拒答 |

按 27 题单次结果计算，4/7 target 回答成功，3/7 未修复。若要求“稳定修复”，目前只有 `TRAG-009`、`TRAG-020` 已由五次重复验证；`TRAG-048` 仍有 flip，`TRAG-026` 仅有单次结果。

## 4. Negative Guards

三个负向保护案例全部保留：

- `ARAG-N01`：缺精确日期和时间，拒答。
- `ARAG-N02`：缺精确分数线和录取最低分，拒答。
- `ARAG-N03`：组合问题只有课程证据、缺考试具体时间，整体拒答。

负向保护通过率为 3/3，没有因放宽规则而错误进入 Generation。

## 5. Stability 对比

每个 case 的五次 Retrieval Evidence Hash 均只有 1 个变体，说明对比期间 Top3 Evidence 稳定。

| caseId | 4A | 4B | 变化 |
|---|---|---|---|
| `TRAG-008` | 2 PASS / 3 REFUSAL，发生 flip | 0 PASS / 5 REFUSAL | 不再 flip，但稳定误拒答 |
| `TRAG-025` | 2 PASS / 3 REFUSAL，发生 flip | 5 AMBIGUOUS | 稳定且符合未指定 cohort 的产品语义 |
| `TRAG-048` | 4 PASS / 1 REFUSAL，发生 flip | 3 PASS / 2 REFUSAL | 仍 flip，PASS 次数下降 |
| `TRAG-009` | 3 PASS / 2 REFUSAL，发生 flip | 5 PASS | 明显改善 |
| `TRAG-020` | 5 PASS | 5 PASS | 保持稳定 |
| `TRAG-060` | 2 PASS / 3 REFUSAL，发生 flip | 0 PASS / 5 REFUSAL | 不再 flip，但稳定误拒答 |

汇总：

- 4A：18 PASS / 12 REFUSAL；5/6 case 发生 semantic flip。
- 4B：13 PASS / 12 REFUSAL / 5 AMBIGUOUS；1/6 case 发生 semantic flip。
- Evidence 变化：6/6 case 均为 1 个 Evidence 变体。
- `TECHNICAL_ERROR`：0。

flip 数下降 80%，但 4B 的 PASS 数从 18 降至 13。5 个新增 `AMBIGUOUS` 全部来自 `TRAG-025`，属于预期语义；除此之外，`TRAG-008` 和 `TRAG-060` 被稳定在错误拒答，`TRAG-048` 的波动没有消失。

## 6. PASS Guard 回归

10 个既有 PASS guard 中：

- 9 个继续正确回答。
- `TRAG-029` 变为 `AMBIGUOUS`。

`TRAG-029` 的 Top3 同时包含 2025 级与 2024 级信息管理学院规则，两套四门课程存在差异；问题只写“2026年”而没有给出 cohort。因此该结果在语义上是合理的澄清，但它仍是固定 PASS guard 的行为变化，报告中不将其隐藏为 PASS。

## 7. 剩余问题

- `TRAG-003`、`TRAG-031`、`TRAG-062`：4A 已确认主要是完整证据不在生产 Top3，属于 Evidence Window / Retrieval 范围，不应通过放宽 Answerability 修复。
- `TRAG-008`：证据包含完整列表，但 checker 仍认为不能完整支持，列表与年份表达判断仍过度保守。
- `TRAG-060`：证据已明确建议投入微积分II且平衡其他笔试机试内容，checker 额外要求“具体策略”，属于 aspect 语义膨胀。
- `TRAG-066`：正确证据位于 Top2 且直接覆盖问题，仍属于 checker false negative。
- `TRAG-037`：本次没有 provider 技术错误，但 Top3 仍未被判为完整覆盖，需要区分 Evidence Coverage 与问题措辞。
- `TRAG-048`：相同 Evidence、temperature=0 下仍翻转，说明 provider/model 的语义判断不能视为绝对确定。

## 8. 是否进入完整 82 题

当前不建议进入。建议先由用户决定是否保留 4B 作为下一轮实验基础；若继续，应针对 aspect 语义膨胀、明确完整列表以及稳定误拒答设计更窄的修正，并继续使用这 27 题和 30 次 Stability 验证。未经确认不运行完整 82 题。
