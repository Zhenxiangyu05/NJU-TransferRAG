# NJU Compass Evaluation

本目录保存 NJU Compass 的固定评测集、回归记录、失败归因和 Internship Freeze 报告。Ground Truth 来源于已扫描的原始校园资料，不以模型常识作为事实标准。

## 建议纳入 Git 的内容

- 固定数据集：`test-cases.json`。
- 小规模回归集：`smoke-cases.json`、`retrieval-regression-cases.json`、`metadata-regression-cases.json`、`year-regression-cases.json`、`answerability-regression-cases.json`。
- 已验收 Iteration 的中文 comparison、before/after、analysis 和 failure reports。
- Answerability 实验的精简分析与工程结论。
- `internship-freeze/` 下的 `final-regression-report.md`、`freeze-summary.md`、`known-limitations.md`、`restoration-plan.md` 与 `trag-066-final-check.md`。
- 不含凭据、使用相对路径且有复现价值的评测脚本。

## Answerability 实验状态

- 4A：diagnosis，只读架构与稳定性分析。
- 4B：rejected / unaccepted experiment，未进入最终生产版本。
- 4C：rejected / unaccepted experiment，未进入最终生产版本。
- Internship Freeze：生产实现恢复为通过完整回归的 Answerability V1。

保留 4A/4B/4C 的分析是为了记录失败假设、regression 和恢复决策，不代表这些实验能力属于当前产品。

## 公开提交边界

以下内容标记为 **DO_NOT_PUBLIC_COMMIT**，默认不适合直接放入公开仓库：

- `**/evaluation-results.json`、`**/*-results.json` 等完整 raw result；它们可能包含完整 Answer、Sources、TopK 和长篇原始 Chunk。
- `**/trace-snapshots.json`、`**/debug-checker-outputs.json` 等 debug trace。
- `**/code-snapshots/`、`**/protected-hashes*.json`、`**/pause-checkpoint.md` 等恢复或暂停临时文件。
- `**/*.log` 和原子写入遗留的 `**/*.tmp`。
- `iteration-4a-answerability-analysis/answerability-trace.md` 与 `iteration-4c-answerability-semantics-analysis/blocker-trace.md`；当前内容含较长 Evidence 摘录，应在公开前单独审查。

扫描发现，部分 raw JSON 和 trace 文件包含邮箱、电话号码或联系人文本。这些内容可能来自官方通知或社区经验原文，但公开发布前仍需进行隐私、授权和资料转载范围审查。未经审查时，应将完整结果保存在私有归档或 Release artifact，而不是 Git 历史中。

正式测试集和精简 Markdown 报告不应被宽泛 ignore。若需要公开某个 raw result，应先生成不含长篇 Chunk、联系人信息和本地运行细节的 sanitized summary。

## 冻结结果

- Baseline：36 / 82，43.90%。
- Internship Freeze：51 / 82，62.20%。
- 提升：+15 PASS，+18.30 percentage points。
- 最终固定回归：`WRONG = 0`、`YEAR_MISMATCH = 0`、`HALLUCINATION = 0`、`SYSTEM_ERROR = 0`。
- `TRAG-066` 曾在一次 Full Regression 中出现 `WRONG_SOURCE`；后续三次独立复核均未再次复现，记录为 nondeterministic source-selection outlier。

项目最终状态为 `INTERNSHIP_FREEZE`。
