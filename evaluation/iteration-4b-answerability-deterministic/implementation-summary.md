# Answerability Iteration 4B 实现说明

## 1. 改造范围

本轮保持原有主链路不变：

`Retrieval → Relevance Gate → Answerability → Generation`

没有切换到 Generate → Validate，也没有取消 Source Pruning。没有修改 Retrieval ranking、candidateTopK、Neighbor Expansion、Entity Resolution、Year/Cohort、Chunker、Source Authority、Generation Prompt、Qdrant metadata、数据库数据或 0.55 threshold。

本轮涉及的代码文件：

- `src/main/java/com/yu/transferrag/dto/AnswerabilityResult.java`
- `src/main/java/com/yu/transferrag/service/AnswerabilityService.java`
- `src/main/java/com/yu/transferrag/service/RagService.java`
- `src/test/java/com/yu/transferrag/service/AnswerabilityServiceTest.java`
- `src/test/java/com/yu/transferrag/service/RagServiceTest.java`

## 2. AnswerabilityResult

`AnswerabilityResult` 不再以 LLM 直接给出的整体 boolean 作为最终裁决，而是记录以下四种状态：

- `SUFFICIENT`
- `INSUFFICIENT`
- `AMBIGUOUS`
- `TECHNICAL_ERROR`

同时保留经过 Java 校验后的 aspect 列表和 `evidenceCitationIds`。`answerable()` 仅在状态为 `SUFFICIENT` 时返回 true。

## 3. LLM 的职责

LLM 只输出：

- 用户明确要求回答的核心 `aspects`
- 每个 aspect 的 `supported`
- 每个 supported aspect 的 `citationIds`
- aspect 级原因
- 是否存在多套互斥规则及其歧义原因

LLM 不再输出最终 `answerable=true/false`。提示词明确要求不过度拆分问题、精确信息必须有直接证据、完整列表可判支持、“包括但不限于”不能冒充完整列表，并允许多个 Source 联合覆盖不同 aspect。

## 4. Java 确定性汇总

Java 汇总规则如下：

1. 结构化输出缺少 aspects、aspect 名称或 supported 字段，视为技术失败并进入有限重试。
2. `ambiguity=true` 时返回 `AMBIGUOUS`，不进入 Generation。
3. 任一必须 aspect 为 `supported=false`，返回 `INSUFFICIENT`。
4. supported aspect 没有引用、引用为空或引用不存在，fail-closed 为 `INSUFFICIENT`。
5. 所有必须 aspect 均 supported 且引用合法时返回 `SUFFICIENT`。
6. `evidenceCitationIds` 由所有 supported aspect 的 citationIds 按出现顺序去重取并集，继续用于现有 Source Pruning。

## 5. Temperature 与结构化输出

Answerability checker 从当前 `ChatModel` 的 provider 默认选项派生专用配置，只覆盖 `temperature=0`；Generation 的配置未改变。实际 provider 接受该参数。

`temperature=0` 只能降低采样随机性，不能保证语义判断绝对确定。30 次 Stability 中 `TRAG-048` 仍发生翻转，验证了这一限制。

## 6. 技术错误处理

模型调用、timeout、429、空响应和结构化解析失败最多额外重试一次。语义性 `INSUFFICIENT` 和非法 citation 不重试。

连续技术失败返回 `TECHNICAL_ERROR`，`RagService` 返回“当前问答服务暂时不可用，请稍后重试。”，不再冒充“根据当前知识库资料无法确定。”。响应不包含 provider、API key 或内部异常堆栈。

27 题 Regression 和 30 次 Stability 中均未出现 `TECHNICAL_ERROR` 或 `SYSTEM_ERROR`。单元测试已覆盖连续 429、第二次重试成功和连续结构化解析失败。

## 7. AMBIGUOUS 行为

当 Sources 同时包含不同 cohort/年级的互斥完整规则，且问题没有给出适用 cohort 时，返回 `AMBIGUOUS`。当前产品响应要求用户补充年级或入学年份，不静默选择其中一套，也不使用知识不足文案。

`TRAG-023`、`TRAG-025`、`TRAG-027` 均按预期进入该状态。

## 8. 测试结果

- Answerability 与编排层定向测试：19 tests，0 failures，0 errors，0 skipped。
- 最终 Maven 全量测试：114 tests，0 failures，0 errors，0 skipped。

定向测试覆盖完整单证据、多 Source 联合覆盖、日期缺失、数字缺失、多子问题部分覆盖、完整列表、cohort 歧义、技术重试、非法引用、无效结构化输出、引用并集和三类用户响应分流。
