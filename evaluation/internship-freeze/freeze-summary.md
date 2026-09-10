# TransferRAG 实习冻结收口摘要

## 最终生产架构候选

当前工作区已恢复到最后一次完整 82 题评测所使用的 Answerability V1 设计：checker 输出整体 `answerable`、证据 citationIds 与原因；Java 校验引用是否合法；不可回答或 checker 异常时 fail-closed；通过后继续使用 Source Pruning，再由最终 LLM 基于获准 Sources 生成答案。

已保留并验证的能力包括：文档导入与 OCR、Chunk/Embedding/Qdrant 检索、Entity Resolution、canonical department、`policyYear / cohortYear / effectiveYear`、metadata filtering、引用和来源审计、MySQL/Qdrant consistency、StructuredPolicyChunker 与 CohortAwareTextChunker。

## 评测演进

- 原始 baseline：36 / 82（43.90%）。
- Metadata 2A、Entity Resolution 2B、Year/Cohort 3A/3B 完成后的上一正式结果：51 / 82（62.20%）。
- 本次恢复后的最终回归：51 / 82（62.20%），相对 baseline 增加 15 个 PASS，提高 18.30 个百分点。

## Answerability 实验状态

- 4A：诊断实验，记录 nondeterminism、Evidence Window、Source Pruning 与架构选项；没有修改生产代码。
- 4B：未验收实验，引入 aspect-level checking、Java deterministic aggregation、四状态结果、checker `temperature=0` 与技术重试。稳定性改善，但出现稳定误拒答和回归。
- 4C：未验收实验，进一步加入 `questionAnchor`、ambiguityConflict 与语义边界规则；27题小评测未达到门槛，未进入 Stability 和82题。

4B/4C 的实验文件全部保留，但对应生产实现已回退，不作为最终生产能力宣传。

## 本次恢复与验证

恢复范围：

- `AnswerabilityResult.java`：恢复 boolean V1 DTO。
- `AnswerabilityService.java`：恢复 V1 checker、结构化 boolean 输出和 citation 校验。
- `RagService.java`：仅移除 4B/4C 的 AMBIGUOUS/TECHNICAL_ERROR 状态分流；保留 cohort、来源访问、citation 与 Source Pruning。
- `AnswerabilityServiceTest.java`、`RagServiceTest.java`：恢复与 V1 一致的测试，同时保留来源文件与 URL 安全测试。

验证结果：Maven 110/110；Spring Boot 正常启动；7 个重点文档 consistency 均为 OK；固定 82 题完整执行且无 SYSTEM_ERROR。

## 冻结判定

本次 PASS 数与上一正式版一致，`WRONG`、`YEAR_MISMATCH`、`HALLUCINATION`、`SYSTEM_ERROR` 均为 0。完整回归中 `TRAG-066` 曾出现一次 `WRONG_SOURCE`：回答把二次拔尖场景的材料混入大一转专业面试。

随后只对 `TRAG-066` 进行了三次冻结裁决复核。三次 Retrieval Evidence 完全一致，`doc13/chunk87` 与 `doc13/chunk88` 均进入 Top3；运行结果分别为 `REFUSAL_FALSE_NEGATIVE`、`PASS`、`REFUSAL_FALSE_NEGATIVE`。三次最终 Sources 均未包含 `chunk87`，唯一成功回答只引用正确的 `chunk88`，因此 `WRONG_SOURCE` 复现次数为 0/3。

按既定裁决标准，完整回归中的 `TRAG-066` 来源污染认定为一次性 LLM/source-selection nondeterministic outlier。最终项目状态为：**INTERNSHIP_FREEZE**。

工程功能开发在此冻结，不自动开启下一轮 RAG 优化。下一阶段以简历、面试准备、部署和演示材料为主。
