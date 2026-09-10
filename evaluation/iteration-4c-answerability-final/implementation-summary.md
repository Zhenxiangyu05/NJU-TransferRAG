# Answerability Iteration 4C 实现与验收摘要

## 1. 实现范围

Iteration 4C 仅调整 Answerability 的语义约束与确定性校验，没有修改 Retrieval、Generation Prompt、Source Authority、Entity Resolution、Year/Cohort、Chunker、threshold、MySQL 或 Qdrant。

生产代码改动集中在：

- `src/main/java/com/yu/transferrag/service/AnswerabilityService.java`

测试代码改动集中在：

- `src/test/java/com/yu/transferrag/service/AnswerabilityServiceTest.java`

主要实现内容：

- 每个 aspect 增加 `questionAnchor`，Java 校验其必须来自问题原文。
- Prompt 禁止自行增加时间比例、每日计划、实施步骤、更深层原因或额外完整性要求。
- 建议类和原因类问题按用户实际询问的抽象层级判断证据。
- 年度文档 metadata、`effectiveYear` 与文档上下文可以共同支持年份；正文明确冲突时仍 fail-closed。
- 列表完整性只覆盖用户要求的列表范围。
- `AMBIGUOUS` 必须携带可验证的 `ambiguityConflict`：至少两个合法 citation、至少两个不同 `candidateValues`，并具有非空 `missingQualifier`。
- 保留 4B 的 `SUFFICIENT / INSUFFICIENT / AMBIGUOUS / TECHNICAL_ERROR`、Java deterministic aggregation、`temperature=0`、structured aspects、citation validation、technical retry 和 Source Pruning。

## 2. 测试结果

- 定向测试：25 tests，0 failures，0 errors，0 skipped。
- Maven 全量测试：120 tests，0 failures，0 errors，0 skipped。

恢复后只修正了 `AnswerabilityServiceTest` 中可变参数辅助方法的重载歧义，以及一条 retry 测试的 `questionAnchor` 测试输入；没有再次修改 4C 生产语义。

## 3. 27 题小评测

执行结果：

| 指标 | 数量 |
|---|---:|
| Total | 27 |
| 符合预期 | 19 |
| PASS | 19 |
| REFUSAL_FALSE_NEGATIVE | 7 |
| PARTIAL | 1 |
| AMBIGUOUS runtime | 3 |
| SYSTEM_ERROR / TECHNICAL_ERROR | 0 |
| WRONG | 0 |
| WRONG_SOURCE | 0 |
| HALLUCINATION | 0 |

结果文件为 `answerability-regression-results.json`。每个 case 完成后均采用临时文件校验后替换的方式原子保存，最终 JSON 共 27 条且可完整解析。

## 4. 目标案例

| caseId | 4C 结果 | 结论 |
|---|---|---|
| `TRAG-008` | `REFUSAL_FALSE_NEGATIVE` | 未修复；Top1 仍含完整初试、复试形式证据 |
| `TRAG-060` | `PASS` | 已修复；回答覆盖微积分II投入与笔试机试准备之间的平衡 |
| `TRAG-066` | `REFUSAL_FALSE_NEGATIVE` | 未修复；目标文档直接证据已在 Top3 |
| `TRAG-029` | `REFUSAL_FALSE_NEGATIVE` | 未保持合理 `AMBIGUOUS`；核心课程列表确有 cohort 冲突，但系统返回普通知识不足 |

`TRAG-023`、`TRAG-025`、`TRAG-027` 均继续返回合理 `AMBIGUOUS`。

三个 negative guards 均继续拒答，保护结果为 3/3。

## 5. 其它变化

- `TRAG-009` 从 4B 的 PASS 变为 PARTIAL：回答覆盖重新熟悉材料和可能围绕材料追问，但遗漏“表达应简明务实”。
- `TRAG-003`、`TRAG-031`、`TRAG-062` 继续受既知 Evidence Window / Retrieval 问题影响，本轮没有通过放宽 Answerability 处理。
- 4C 与 4B 的 27 个 case，其 Top3 `documentId / chunkId / contentSha256` 均一致，说明本次结果变化不是 Retrieval 或索引变化造成的。

## 6. Stability 与 Full Regression

由于小评测未同时满足 `TRAG-008`、`TRAG-060`、`TRAG-066` 修复以及 `TRAG-029` 保持合理 AMBIGUOUS 的门槛：

- 30 次 Stability：未执行，`stability-results.json` 为有效空数组。
- semantic flip：4C 无可计算结果，不能声称优于或等于 4B 的 1/6。
- 最终 82 题 Full Regression：未执行。
- `INTERNSHIP_FREEZE`：未达到。

本轮到此停止，不继续新的架构迭代。
