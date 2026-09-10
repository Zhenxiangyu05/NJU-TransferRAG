# Answerability Iteration 4C 与 4B 对比

## 1. 总体结论

4C 未通过小评测验收。它修复了 `TRAG-060` 的 aspect semantic expansion，但没有修复 `TRAG-008` 和 `TRAG-066`，同时 `TRAG-029` 从合理 AMBIGUOUS 变为普通知识不足拒答，`TRAG-009` 从 PASS 变为 PARTIAL。

因此本轮没有运行 30 次 Stability，也没有运行最终 82 题 Full Regression。

## 2. 27 题结果对比

| 指标 | 4B | 4C | 变化 |
|---|---:|---:|---:|
| 符合预期 | 19/27 | 19/27 | 0 |
| runtime ANSWER | 13 | 14 | +1 |
| runtime AMBIGUOUS | 4 | 3 | -1 |
| runtime REFUSAL | 10 | 10 | 0 |
| REFUSAL_FALSE_NEGATIVE | 7 | 7 | 0 |
| PARTIAL | 0 | 1 | +1 |
| SYSTEM_ERROR / TECHNICAL_ERROR | 0 | 0 | 0 |

4C 的符合预期总数没有提升：`TRAG-060` 的一项收益被 `TRAG-009` 的 PARTIAL 回归抵消。

## 3. 三个 4C 目标

| caseId | 4B | 4C | 是否修复 |
|---|---|---|---|
| `TRAG-008` | `REFUSAL_FALSE_NEGATIVE` | `REFUSAL_FALSE_NEGATIVE` | 否 |
| `TRAG-060` | `REFUSAL_FALSE_NEGATIVE` | `PASS` | 是 |
| `TRAG-066` | `REFUSAL_FALSE_NEGATIVE` | `REFUSAL_FALSE_NEGATIVE` | 否 |

三个目标仅修复 1 个，不满足必须全部修复的验收条件。

## 4. AMBIGUOUS 行为

- `TRAG-023`：保持合理 AMBIGUOUS。
- `TRAG-025`：保持合理 AMBIGUOUS。
- `TRAG-027`：保持合理 AMBIGUOUS。
- `TRAG-029`：4B 为合理 AMBIGUOUS，4C 变为 REFUSAL。其 2024 级与 2025 级第四门课程不同，冲突位于用户要求的完整列表中，因此 4C 行为不符合产品要求。

这表明新的结构化冲突校验保住了三个主要 cohort 案例，但未在 `TRAG-029` 上稳定形成合法 `ambiguityConflict`。

## 5. Regression 与安全指标

- 三个 negative guards：3/3 保留。
- `WRONG`：0。
- `WRONG_SOURCE`：0。
- `YEAR_MISMATCH`：0。
- `HALLUCINATION`：0。
- `SYSTEM_ERROR`：0。
- 新的明显行为回归：`TRAG-009` 从 PASS 变为 PARTIAL；`TRAG-029` 从合理 AMBIGUOUS 变为普通拒答。

所有 27 个 case 的 Top3 证据身份均与 4B 相同，因而上述变化可归于 Answerability 语义行为，而非 Retrieval、metadata 或索引漂移。

## 6. Stability 与最终冻结

4C 小评测已明确失败，按预设门槛停止：

- 30 次 Stability 未运行，semantic flip 未测量。
- 4B 的 semantic flip 仍为历史参照 1/6，不能作为 4C 结果。
- 82 题最终回归未运行。
- 无 `PASS/82` 新结果。
- 当前不能标记 `INTERNSHIP_FREEZE`。

本报告只记录结果，不继续修改 Answerability，也不启动下一轮架构优化。
