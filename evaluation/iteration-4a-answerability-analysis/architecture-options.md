# Answerability 架构方案分析

## 1. 当前职责是否过重

是。当前单次 checker 同时承担：判断证据充分性、解释组合问题完整性、选择 citationIds、决定是否允许生成。其 citationIds 还直接决定最终 Generation 能看到哪些 Sources。一个自由生成的 boolean/list 同时控制“是否回答”和“拿什么回答”，导致语义波动、source pruning 与 fail-closed 异常互相耦合。

## 2. 方案 A：保留现有 Pre-generation Gate

链路：`Retrieval → Answerability → Generation`。

优点：改动最小；保留 fail-closed；现有测试结构可复用；延迟和调用次数不增加。

风险：自由 boolean 仍可能波动；checker 同时选 Source；多事实覆盖没有确定性验证；provider 异常仍可能伪装成资料不足。

预计改善：显式 temperature、原生 JSON Schema、技术错误单独处理可降低部分 TRAG-008/009/020/026/048/060 波动，但不能解决 TRAG-003/031/062 的 Top3 缺证据，也不应强行放行 TRAG-023/025/027。

## 3. 方案 B：Answerability 只判断，不裁剪 Source

链路：`Retrieval Top3 → Answerability decision → Generation 使用完整 Top3`。

优点：职责分离；避免 checker 漏选一个 citation 导致最终回答 PARTIAL；可直接改善 TRAG-045/056/076 类裁剪问题；checker citationIds 可保留为审计信息而不控制上下文。

风险：Generation 会看到相关性较弱的候选，可能增加 WRONG_SOURCE；必须继续依赖已有 department/year/authority filter，并用 negative/pass guards 验证。它本身不减少 checker 的 false boolean。

判断：比当前架构更清晰，但不应单独作为 4B 的唯一变化；应和严格的原子覆盖决策一起实施，不能复用 V2 的“部分证据也放行”。

## 4. 方案 C：Generate → Validate

链路：`Retrieval → 生成候选答案 → Claim/Citation Validation → 返回、裁剪或拒答`。

优点：validator 面对具体 claim，判断任务比抽象地预测“能否回答”更明确；天然适合逐 claim 引用检查；可能减少 pre-gate 误拒答。

风险：在验证前已经生成潜在幻觉；通常至少两次 LLM 调用，token、延迟和成本明显增加；需要 claim splitting、citation entailment、失败后的裁剪/重写，复杂度最高；validator 自身仍可能非确定。fail-closed 设计不当会增加 HALLUCINATION。

判断：适合作为后续架构方向，不适合作为风险最低的 4B。

## 5. 原子事实覆盖

推荐将问题先转为稳定的 `requiredAspects`，再让 checker 对每个既定 aspect 标注 `SUPPORTED/UNSUPPORTED/AMBIGUOUS` 和 citationIds，最后由 Java 做集合判定：只有所有 required aspect 都是 SUPPORTED 才放行。

关键约束：

1. `requiredAspects` 优先由保守的规则/查询结构产生；无法安全拆分时把整个问题作为单一 aspect，而不是让 LLM自由增删要求。
2. 日期、数字、课程名、学分等 aspect 继续要求显式证据。
3. 不允许 `PARTIAL` 放行，避免重复 Answerability V2 的回归。
4. citationId 必须存在；每个 supported aspect 至少一个 citation。
5. Java 仅做确定性的完整集合比较，不让 LLM自由输出最终 boolean。
6. `AMBIGUOUS` 与技术 `ERROR` 不应伪装成“知识库无资料”。

这比 V2 稳定的原因不是标签从 boolean 变多，而是“问题要求集合固定、每项支持可审计、最终决策由 Java 确定”，且仍坚持全覆盖才回答。
