# Metadata Iteration 2A Comparison

## 1. Metadata changes

| Document | Before | After |
|---|---|---|
| doc13 | Document.department=`现代工程学院`; 2个Chunk的department/major为NULL；Qdrant effectiveYear缺失 | Document/Chunk department统一为`现代工程与应用科学学院`；Qdrant department/chunkDepartment同步；effectiveYear=2026；scope/sourceType一致 |
| doc14 | Document.department误填`电子信息类`; 13个Chunk的department/major为NULL；Qdrant effectiveYear和scope缺失 | Document/Chunk department=`电子科学与工程学院`；Chunk/Qdrant major=`电子信息类`；effectiveYear=2026；scope=`DEPARTMENT` |

doc13 的 major 保持 NULL：原文只有“光材二次拔尖班”“转光电”等表达，没有可靠出现 canonical 专业或实验班全称。本轮没有借助常识补写。doc14 的 `电子信息类` 由原文明确支持，已从错误的学院字段迁移到 Chunk major/program 语义。

## 2. MySQL / Qdrant consistency

- doc13：`OK`，2 MySQL Chunks / 2 Qdrant Points，0 blocking issues；仅有2个 deliberate major-missing warnings。
- doc14：`OK`，13 MySQL Chunks / 13 Qdrant Points，0 issues，0 warnings。
- 连续两次 reindex 后数量仍为2/13，证明现有 delete-by-documentId + add 流程在本次数据上幂等。

## 3. Smoke Set

共15题：

- PASS：10
- REFUSAL_FALSE_NEGATIVE：4
- WRONG_SOURCE：1
- SYSTEM_ERROR：0
- Pass Rate：66.7%

10个 PASS 包含真正应拒答的 `SMOKE-NEG-001`。baseline PASS guards 中 `TRAG-021` 出现1个 regression；其正确官方证据在 Top1，属于 Answerability / nondeterministic regression。`TRAG-064` 的正确 doc13 chunk87 已回到 Top1，但该次 smoke 最终拒答；在 metadata regression 独立复跑中已 PASS，说明 metadata visibility 已修复、最终拒答仍有波动。

## 4. Metadata Regression Set

共22题：

- PASS：14
- PARTIAL：2
- REFUSAL_FALSE_NEGATIVE：6
- SYSTEM_ERROR：0
- Pass Rate：63.6%

6个拒答中：`TRAG-015/016/025` 是未处理的 Year/cohort；`TRAG-074/008/021` 的正确证据已进入 TopK，属于 Answerability。两个 PARTIAL 为 `TRAG-070`（Ground Truth 旧文档仍未导入）和 `TRAG-076`（Generation 遗漏）。

## 5. Seven target cases

| caseId | Baseline | Iteration 2A | 正确证据位置 | 结论 |
|---|---|---|---|---|
| TRAG-064 | REFUSAL_FALSE_NEGATIVE | PASS（metadata regression）；smoke复跑拒答 | doc13 chunk87 rank1 | metadata 已改善；最终回答存在 Answerability 波动 |
| TRAG-066 | REFUSAL_FALSE_NEGATIVE | PASS | doc13 chunk88 rank2 | 已改善 |
| TRAG-067 | REFUSAL_FALSE_NEGATIVE | PASS | doc13 chunk87 rank1 | 已改善 |
| TRAG-073 | REFUSAL_FALSE_NEGATIVE | PASS | doc14 chunk95 rank1、chunk94 rank2 | 已改善 |
| TRAG-075 | REFUSAL_FALSE_NEGATIVE | PASS | doc14 chunk98 rank1 | 已改善 |
| TRAG-076 | REFUSAL_FALSE_NEGATIVE | PARTIAL | doc14 chunk99 rank3 | metadata 正常；Generation 遗漏“共5门”和“鼓楼1学分” |
| TRAG-077 | REFUSAL_FALSE_NEGATIVE | PASS | doc14 chunk95 rank1、chunk94 rank2 | 已改善 |

七个 target 的正确 Chunk 均重新进入 TopK；metadata regression 中6个 PASS、1个 PARTIAL。

## 6. Regression analysis

baseline PASS regression 共2个：

- `TRAG-008`：doc20 正确证据在 Top1，最终拒答。
- `TRAG-021`：doc6 chunk146 正确完整证据在 Top1，最终拒答。

两者都没有依赖 doc13/doc14，且检索证据完整，记录为 `Answerability / nondeterministic regression`，不在 Metadata 2A 修复。

`TRAG-076` 不是 metadata、Answerability 或 evidence coverage 问题：chunk99 rank3 已完整写明“5门中至少修1门、对外开放4门、只在鼓楼、1学分、不限制分流方向”，Answerability 允许回答且最终答案引用该证据，但生成阶段漏掉两个关键事实，因此归为 Generation PARTIAL。

## 7. Remaining boundaries

- Entity Resolution / Metadata 2B：仍需补齐高置信 alias、major/program→canonical department，并处理 `TRAG-074` 中“软院之外”的非目标实体语义。
- Year/cohort：`TRAG-015, TRAG-016, TRAG-025`。
- Index/Data Quality：`TRAG-070`，本轮未导入旧版 `转电子指北.pdf`。
- Answerability：`TRAG-008, TRAG-021, TRAG-074`，本轮只记录。
- Generation：`TRAG-076`，本轮只记录。

## 8. Acceptance

Metadata 2A 达到验收目标：doc13/doc14 的 canonical department、Chunk metadata、effectiveYear 和 scope 已同步到 MySQL/Qdrant，点位一一对应；七个重点 target 的正确 Chunk 全部恢复可见；无 SYSTEM_ERROR。建议在用户确认后进入 Metadata 2B，本轮不继续修改 EntityAlias 或 QueryRewrite。
