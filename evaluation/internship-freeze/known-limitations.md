# TransferRAG 已知限制

以下问题均由真实知识库评测观察得到，尚未解决。

## Answerability 误拒答

原始资料明确有答案时，V1 checker 仍可能返回不可回答。本次共有 21 个 `REFUSAL_FALSE_NEGATIVE`，其中 `TRAG-008、009、037、060、064` 的直接证据已进入候选，但仍被拒答。V1 对 provider 异常与语义证据不足使用同一 fail-closed 文案，外部响应无法区分两者。

`TRAG-066` 的三次冻结裁决复核也出现两次误拒答、一次 PASS；三次 Evidence Hash 完全一致。这进一步说明 Answerability 在固定证据下仍存在非确定性。

## Evidence Window 与 TopK

生产 Answerability 只看到最终 Top3。`TRAG-003、031、062` 的关键事实位于同文档的其他 Chunk 或 Top3 之外，导致整体问题无法通过。`TRAG-005、036、042、080` 的主要问题仍在 Retrieval 召回或排序。

## Generation 完整性

checker 通过不保证最终回答覆盖全部 Ground Truth。本次 `TRAG-016、032、043、045、056、070、076` 遗漏一个或多个关键事实。组合问题与完整列表尤其容易发生遗漏。

## Source Authority 与范围边界

官方政策查询可能过滤掉提供 Ground Truth 的个人指南，导致 `TRAG-006、007、073、077` 拒答。

完整 82 题回归中曾观察到一次相邻场景来源污染：`TRAG-066` 同时检索到 `doc13/chunk87` 的“26级光材二次拔尖”和 `doc13/chunk88` 的“大一转光电面试经验”，最终回答把前者的光学题、高考及竞赛考量混入后者。冻结裁决的三次复核中，该 `WRONG_SOURCE` 未再次出现；因此它被认定为 nondeterministic outlier，而不是本轮可稳定复现的结构性回归。

这一现象仍作为已知风险保留。若未来恢复开发，应优先研究 scenario-aware source filtering 或 generation source boundary；当前实习投递阶段不再投入功能开发时间。

## Cohort 限定

同一 transfer cycle 中可能同时存在不同 applicant cohort 规则。V1 无独立 `AMBIGUOUS` 产品状态；`TRAG-025、029` 选择了一套有证据的规则，却没有向用户说明 cohort，回答不能视为完整可靠。

## Entity Resolution

EntityAlias 与 major-to-department 映射已显著改善查询过滤，但仍有 `TRAG-038、046` 未稳定得到最终答案。多学院大类、方向名称和历史简称仍需要谨慎维护。

## 知识库完整性

部分 Ground Truth 文件尚未作为独立文档完整导入，或现有资料仅包含相邻版本和相似事实。系统不能把相似资料当作缺失文档的等价替代；`TRAG-070` 本次仍缺少“3到4分钟提问”这一事实。

## LLM 非确定性

相同 Retrieval 证据可能产生回答与拒答翻转。上一正式版本和本次结果之间存在等量的 PASS regression 与 improvement；`TRAG-071` 本次由 PASS 变为拒答，`TRAG-048、082` 则由非 PASS 变为 PASS。单次评测结果不能证明语义判断完全确定。

## 当前使用边界

系统适合提供带引用的知识库辅助问答和演示，不应把个人经验自动升级为官方政策，也不应把当前 62.20% Pass Rate 表述为高准确率、完全消除幻觉或生产级保障。
