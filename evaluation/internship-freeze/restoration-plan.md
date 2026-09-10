# 实习冻结恢复计划（待确认）

## 当前决策与证据

本文件仅提交恢复计划，尚未回退生产代码、运行测试或启动评测。

目标架构是 Metadata 2A、Entity Resolution 2B、Year/Cohort 3A/3B 完成后的 Answerability V1 链路。历史正式结果为 51/82（62.20%），不是本次恢复后的新结果。

核对依据：

- 当前 `git diff`、`git status`，以及初始化提交 `047bb60` 中可读取的 V1 实现。
- `evaluation/full-regression-after-metadata-year/evaluation-report.md`：固定 82 题，51 PASS，指定严重错误指标为 0。
- `evaluation/iteration-4a-answerability-analysis/answerability-trace.md`：记录 boolean checker、citation 校验、固定拒答和 Source Pruning；4A 未修改代码。
- `evaluation/iteration-4a-answerability-analysis/nondeterminism-analysis.md`：当时 checker 未单独设置 temperature。
- 4B、4C 的 `implementation-summary.md` 与对应测试差异。

仓库只有初始化提交，没有 51/82 时的独立 Git 快照。因此只能用初始化提交提取已核实的 V1 文件/片段，不能将整个工作树恢复到该提交，也不能声称已经获得历史版本逐字节快照。最终以局部差异审计、测试、一致性检查和重新执行固定 82 题验证。

## 精确恢复范围

| 文件 | 拟恢复的实验部分 | 保留内容 |
|---|---|---|
| `src/main/java/com/yu/transferrag/dto/AnswerabilityResult.java` | 恢复 `boolean answerable / evidenceCitationIds / reason`；移除 4B 四状态、aspects 和对应工厂方法 | V1 引用列表归一化与 `notAnswerable` |
| `src/main/java/com/yu/transferrag/service/AnswerabilityService.java` | 恢复 Git 可读取的 V1 checker；移除 aspect aggregation、questionAnchor、ambiguityConflict、checker 专用 temperature 和额外技术重试 | 原始证据限制、结构化 boolean 输出、citation 校验、异常 fail-closed |
| `src/main/java/com/yu/transferrag/service/RagService.java` | 仅移除 4B AMBIGUOUS/TECHNICAL_ERROR 文案及状态 switch，恢复 V1 拒答分支 | `setCohortYear`、fileAvailable、安全 sourceUrl、本地文件检查、Source Pruning、原 Generation Prompt、0.55 门槛以及其他已有逻辑 |
| `src/test/java/com/yu/transferrag/service/AnswerabilityServiceTest.java` | 恢复 V1 通用 checker 测试；移除仅依赖 4B/4C 结构的测试 | 完整证据、多子问题缺证据、非法/缺失引用、解析失败、调用异常等 V1 测试 |
| `src/test/java/com/yu/transferrag/service/RagServiceTest.java` | 恢复 boolean DTO mock；移除两项实验状态专属测试 | 批量 Document 查询、稳定引用、来源文件和 URL 安全测试、Source Pruning 测试 |

`RagService` 中 cohort 映射是需保留的 Year/Cohort 功能；文件与 URL 辅助逻辑属于来源访问功能，不属于 Answerability 实验。即使无法从单一提交独立确定每项来源访问改动时间，也不应在本次回退中删除。

## 完整保留范围

- Metadata、Query Resolution、EntityAlias、Retrieval、StructuredPolicyChunker、CohortAwareTextChunker、数据库和 Qdrant 状态。
- 前端、管理端及所有其他未提交工作。
- 四个 4A/4B/4C 历史目录与已有评测文件原样保留。在新冻结文档中标明：4A 为诊断实验，4B/4C 为未验收实验。

当前 `git status --short` 共 44 条：29 条已跟踪文件修改，15 条未跟踪文件或目录。`evaluation/` 为未跟踪目录，单条状态不代表目录内只有一个文件。

## 确认后的执行顺序

1. 保存待恢复五个文件的当前实验快照及哈希；保存历史 evaluation、Ground Truth、范围外生产文件的哈希，以便审计是否被改动。
2. 用局部补丁执行上述恢复，不使用整仓 reset/restore，不整文件覆盖 RagService 及其测试。
3. 检查实验 API 残留与范围外差异，运行一次 Maven 全量测试，要求 failures/errors 均为 0。
4. 确认启动流程后加载恢复后的 Spring Boot；检查 MySQL/Qdrant 一致性，至少覆盖历史正式评测检查过的 doc6、13、14、19、25、28、29。异常时先记录并定位，不自动进行索引修复。
5. 串行执行固定 `evaluation/test-cases.json` 的全部 82 题，每题原子保存，支持断点；最终语义判断结合 expectedFacts、证据与实际引用，不能复用历史分类代替本次判断。
6. 保存 `final-regression-results.json`、`final-regression-report.md`、`freeze-summary.md`、`known-limitations.md`，必要时仅同步简洁 README 状态。
7. 与 51/82 比较，单独核查 WRONG、WRONG_SOURCE、YEAR_MISMATCH、HALLUCINATION、SYSTEM_ERROR；如有结构性退化，定位恢复是否完整并停止，不开启新优化。
8. 仅验证通过后标记 `INTERNSHIP_FREEZE`；不 commit，交由用户决定提交组织方式。

## 恢复后的行为预期

恢复 V1 也意味着取消实验版的专用 AMBIGUOUS 和 TECHNICAL_ERROR 分流及 checker temperature=0。它们不能作为“通用改进”暗中保留，否则不能证明回到了指定正式架构。V1 的误拒答、技术失败统一拒答和 LLM 波动将在已知限制中如实记录。

附件第四节要求“先形成恢复计划，确认后再执行”。因此本轮在提交本计划后等待用户确认，尚不执行生产恢复。
