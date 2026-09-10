# Retrieval Iteration 2 — Dataset, Failure Analysis and V2 Design

本文件只记录评测子集、只读诊断和设计方案。本轮未修改任何生产代码、索引、数据库或知识库资料。

## 1. 固定评测子集

### Smoke Set

共 15 题，其中 10 个 baseline PASS guard、4 个目标失败和 1 个真正应拒答的负向控制。

| caseId | Baseline | 角色 | 选择原因 |
|---|---|---|---|
| TRAG-001 | PASS | baseline-guard | 正常明确事实；培养方案 baseline PASS guard。 |
| TRAG-004 | PASS | baseline-guard | 列表型课程事实；培养方案 baseline PASS guard。 |
| TRAG-017 | PASS | baseline-guard | 转专业政策；法学院 baseline PASS guard。 |
| TRAG-021 | PASS | baseline-guard | 官方准入课程与成绩门槛；baseline PASS guard。 |
| TRAG-027 | WRONG_SOURCE | target-failure | 个人经验压过官方来源的 WRONG_SOURCE 代表。 |
| TRAG-034 | REFUSAL_FALSE_NEGATIVE | target-failure | 显式年份与文档 effectiveYear 不一致的代表。 |
| TRAG-039 | PASS | baseline-guard | 学院/大类明确事实；baseline PASS guard。 |
| TRAG-050 | PASS | baseline-guard | 旧年份经验事实；跨年份经验 baseline PASS guard。 |
| TRAG-054 | PASS | baseline-guard | 经验资料中的结构化事实；baseline PASS guard。 |
| TRAG-062 | REFUSAL_FALSE_NEGATIVE | target-failure | 正确 Chunk 排在 finalTopK 之外的 Retrieval 代表。 |
| TRAG-064 | REFUSAL_FALSE_NEGATIVE | target-failure | 光电别名与 department 元数据不一致代表。 |
| TRAG-069 | PASS | baseline-guard | 多年份比较；baseline PASS guard。 |
| TRAG-071 | PASS | baseline-guard | 电子学院学习经验；baseline PASS guard。 |
| TRAG-078 | PASS | baseline-guard | 综合指南列表事实；baseline PASS guard。 |
| SMOKE-NEG-001 | N/A | negative-control | 真正应该拒答的负向控制，防止 Retrieval 放宽后产生猜测。 |

负向控制 `SMOKE-NEG-001` 询问 2026 软件学院机试各考场的具体教室号。对 20 份原始资料的可读文本检查未发现“教室号/各考场/具体教室”证据，因此预期必须拒答。

### Retrieval Regression Set

共 30 题，其中 8 个 baseline PASS regression guard；其余覆盖 Retrieval、低分、来源权威性、Chunk 边界、学院过滤和 Year/cohort 边界。

| caseId | Baseline | 角色 | 选择原因 |
|---|---|---|---|
| TRAG-015 | REFUSAL_FALSE_NEGATIVE | target-failure | 官方文学院 Chunk 未进入深检索结果；检查严格学院过滤和官方来源可见性。 |
| TRAG-016 | REFUSAL_FALSE_NEGATIVE | target-failure | 官方文学院 GPA/不及格条件未召回；检查结构化 Chunk 元数据。 |
| TRAG-018 | REFUSAL_FALSE_NEGATIVE | target-failure | 数学学院表格内容跨 Chunk 且正确段落未进入候选前列。 |
| TRAG-019 | REFUSAL_FALSE_NEGATIVE | target-failure | 数学竞赛附加分位于数学学院表格段；当前只召回相邻段。 |
| TRAG-027 | WRONG_SOURCE | target-failure | WRONG_SOURCE；个人资料压过对应官方政策。 |
| TRAG-034 | REFUSAL_FALSE_NEGATIVE | target-failure | Top1<0.55，但深查显示主要是显式年份与文档年份冲突。 |
| TRAG-042 | REFUSAL_FALSE_NEGATIVE | target-failure | 正确地学文档进入候选但分数低于0.55。 |
| TRAG-049 | REFUSAL_FALSE_NEGATIVE | target-failure | Top1<0.55，且2024正文事实被文档级2026过滤隐藏。 |
| TRAG-060 | REFUSAL_FALSE_NEGATIVE | target-failure | baseline 标为 Retrieval，复核后正确证据已在 Top1；用于防止误归因。 |
| TRAG-061 | REFUSAL_FALSE_NEGATIVE | target-failure | baseline 标为 Retrieval，复核后正确证据已在 Top3；用于 Answerability 边界观察。 |
| TRAG-062 | REFUSAL_FALSE_NEGATIVE | target-failure | 正确 Chunk 位于 rank 4，最直接的 candidateTopK/finalTopK 案例。 |
| TRAG-066 | REFUSAL_FALSE_NEGATIVE | target-failure | 目标文档 department 与规范学院名不一致，严格过滤排除。 |
| TRAG-074 | REFUSAL_FALSE_NEGATIVE | target-failure | 目标电子指南被学院元数据不一致排除。 |
| TRAG-075 | REFUSAL_FALSE_NEGATIVE | target-failure | Top1<0.55，但目标电子指南实际未进入候选，主因是元数据。 |
| TRAG-025 | REFUSAL_FALSE_NEGATIVE | target-failure | 计算机学院实体/学院过滤失败代表。 |
| TRAG-064 | REFUSAL_FALSE_NEGATIVE | target-failure | 光电简称和现代工程学院命名不一致代表。 |
| TRAG-070 | REFUSAL_FALSE_NEGATIVE | target-failure | 电子学院经验资料被其他学院结果挤掉代表。 |
| TRAG-077 | REFUSAL_FALSE_NEGATIVE | target-failure | 电子学院组合问题；学院过滤和多事实覆盖联合压力。 |
| TRAG-028 | YEAR_MISMATCH | target-failure | 年级行绑定错误的 YEAR_MISMATCH guard。 |
| TRAG-031 | REFUSAL_FALSE_NEGATIVE | target-failure | 正文年份与 effectiveYear 冲突的 Year guard。 |
| TRAG-041 | REFUSAL_FALSE_NEGATIVE | target-failure | 旧年份事实被年份过滤隐藏的 Year guard。 |
| TRAG-072 | YEAR_MISMATCH | target-failure | 大一/大二 cohort 行选错的 YEAR_MISMATCH guard。 |
| TRAG-001 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-017 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-021 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-039 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-050 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-054 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-063 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |
| TRAG-069 | PASS | baseline-guard | baseline PASS regression guard；确保 Retrieval 改造不损害稳定案例。 |

## 2. 当前 Retrieval 路径

- `RagService` 固定调用 `retrievalService.search(question, 3)`，最终只给 Answerability 三个结果。
- 普通查询中 candidateTopK 与 finalTopK 相同，均为 3；只有无显式年份的经验查询会将候选扩大到 `max(topK×3, 10)`，再做年份小幅加权。
- 只要严格 department 过滤返回任意结果，就不会执行宽松 fallback；这会让“少量错误 strict 结果”阻断正确的全局官方 Chunk。
- 当前没有 Neighbor Chunk Expansion，也没有按 `sourceType/official` 的 Retrieval 阶段重排。
- 0.55 阈值在 Retrieval 之后由 `RagService` 对最高向量分判断；本轮设计不调整该阈值。

## 3. 14 个核心 Retrieval 失败的只读深检索诊断

对 baseline 标记为 Retrieval、Top1<0.55 或 WRONG_SOURCE 的 14 个案例串行调用 `/api/test/search?topK=50`。TopK 返回数量受现有 metadata filter 限制，部分案例即使请求 50 也只返回 7～10 条。

| caseId | Ground Truth / 正确位置 | TopK 观察 | 结论 |
|---|---|---|---|
| TRAG-015 | doc 6，官方准入表第1页文学院行 | 请求50仅返回10条，全部为 doc 20 个人指南；doc 6 完全未出现 | 严格学院过滤/Chunk department 元数据使官方行不可见；不是 candidateTopK 问题 |
| TRAG-016 | doc 6，第1页文学院审核依据 | 请求50仅返回10条，doc 6 完全未出现 | 同 TRAG-015，正确官方 Chunk 被过滤 |
| TRAG-018 | doc 6，第2页数学类两套课程方案 | doc 6 的相邻或其他学院 Chunk 在 rank 3 起出现，但正确数学方案段在 Top50 中仍不可直接识别 | 表格抽取后学院行跨 Chunk、语义与 metadata 绑定丢失；属于 Chunk 质量问题 |
| TRAG-019 | doc 6，第2页数学竞赛附加分段 | Top1 是 doc 6 的相邻 chunk 128，但内容已越过数学学院进入物理/商学院；数学邻段约 rank 8，完整竞赛段未进入 Top50 | 典型同文档相邻 Chunk + 表格边界问题 |
| TRAG-027 | doc 6，第4页软件工程官方行 | doc 17/doc 8 的个人整理位列1/2；正确官方软件行未进入Top50，doc 6 的无关行从 rank 8 出现 | WRONG_SOURCE；需要候选池与官方重排联合处理，单独加 authority bonus 不足 |
| TRAG-034 | doc 19，技术科学试验班指南 | 目标 doc 19 在Top50完全缺失；问题写2025级，但文档元数据 year=2026 | Year/cohort 元数据问题，不应归因于低阈值 |
| TRAG-042 | doc 29，地学指南拔尖段（chunk 323/324附近） | doc 29 进入 rank 2/14/15，但最高约0.513，均低于0.55 | 真正的 threshold/向量低分案例；本轮暂不调阈值 |
| TRAG-049 | doc 25，数理大类指南 | 请求50仅返回7条且目标doc 25缺失；正文是2024数据，文档元数据year=2026 | Year filter 隐藏正文旧年份，不是 candidateTopK 问题 |
| TRAG-060 | doc 26 chunk 309 | 正确证据完整位于 rank 1，包含微积分II投入、其他课遗漏和平衡建议 | baseline 根因误标；实际是 Answerability 误拒绝，不是 Retrieval |
| TRAG-061 | doc 26 chunk 309 | 正确证据位于 rank 3，完整覆盖2小时4题、证明风格和不考下学期内容 | baseline 根因误标；不是 Retrieval |
| TRAG-062 | doc 26 chunk 310 | 正确完整 Chunk 位于 rank 4；finalTopK=3 时被截掉 | candidateTopK/finalTopK 分离可直接解决；邻居扩展也可辅助 |
| TRAG-066 | doc 13，转光电面试段 | 目标doc 13在Top50完全缺失；document.department=`现代工程学院`，规范实体使用`现代工程与应用科学学院` | Metadata/Alias 命名不一致，本轮不解决 |
| TRAG-074 | doc 14，电子指南第7页 | 目标doc 14在Top50完全缺失；document.department=`电子信息类`，问题实体为`电子科学与工程学院` | department 元数据不一致，本轮不解决 |
| TRAG-075 | doc 14，第8页C语言段 | 目标doc 14完全缺失，表面Top1约0.53；根因仍是学院元数据过滤而非单纯低分 | Metadata/Alias 问题，本轮不调阈值 |

## 4. 按主解决方案分类

以下按主要根因互斥计数；同一案例可能有次要可用手段。分析全集为 25 个 baseline Retrieval/Metadata/Year 相关失败。另有 2 个原标为 Retrieval 的案例被纠正为 Answerability，不计入 A～G。

| 类别 | 数量 | caseId | 说明 |
|---|---:|---|---|
| A. 扩大 candidateTopK 可能解决 | 1 | TRAG-062 | 正确 Chunk rank 4，仅被 finalTopK=3 截断 |
| B. Neighbor Chunk Expansion 单独可解决 | 0 | — | 当前没有仅靠 ±1 邻居即可高置信完全解决的独立案例；可作为 018/019/062 的辅助措施 |
| C. 官方来源重排可能解决 | 1 | TRAG-027 | 需先保证正确官方 Chunk 进入候选；不能只对任意官方文档盲目加分 |
| D. Chunk 本身切分有问题 | 2 | TRAG-018, TRAG-019 | 官方表格中学院、年级、条件和考核方法跨 Chunk/错位 |
| E. Metadata / Alias，本轮不解决 | 12 | TRAG-015, TRAG-016, TRAG-025, TRAG-064, TRAG-066, TRAG-067, TRAG-070, TRAG-073, TRAG-074, TRAG-075, TRAG-076, TRAG-077 | strict filter 排除目标文档或被其他学院结果挤占 |
| F. Year / cohort，本轮不解决 | 6 | TRAG-028, TRAG-031, TRAG-034, TRAG-041, TRAG-049, TRAG-072 | 正文年份、文档effectiveYear或大一/大二行绑定问题 |
| G. Threshold，暂时不调 | 1 | TRAG-042 | 正确文档已出现但最高分低于0.55 |

根因纠正：`TRAG-060`、`TRAG-061` 的正确完整证据分别已经位于 Top1 和 Top3，应留给 Answerability，而不是 Retrieval V2。

## 5. Retrieval V2 最小设计

### 5.1 candidateTopK 与 finalTopK 分离

建议保留最终给 RAG 的 `finalTopK=3`，内部候选改为小而固定的 `candidateTopK=max(finalTopK×4, 12)`，并设置上限（例如20）。先做向量召回与过滤，再在候选池内扩展、重排、去重，最后截取3条。

- 直接目标：TRAG-062；可能为 TRAG-027 的官方候选提供进入重排的机会。
- 风险：候选增加会增加排序成本；如果直接把全部候选送给 LLM，会扩大噪声，因此必须在 Retrieval 内收敛回3条。
- 不应期待：被 metadata/year filter 完全排除的案例不会因 candidateTopK 增加而恢复。

### 5.2 有界 Neighbor Chunk Expansion

仅对候选池前3～5个 seed，按 `(documentId, chunkIndex)` 加载同文档 `±1` 邻居；去重后仍进入统一重排。邻居必须继承并校验相同 document、effectiveYear，并在存在 policyYear/department/major 时保持兼容。邻居分数采用 seed score 加固定距离惩罚，而不是伪造新的向量相似度。

- 辅助目标：TRAG-018、TRAG-019、TRAG-062。
- 风险：表格相邻行可能属于其他学院或年级；必须做元数据兼容检查并限制扩展数量。
- 当前证据：018/019 的边界跨度可能超过 ±1，因此 Neighbor Expansion 只能缓解，不能替代后续结构化 Chunk 修复。

### 5.3 政策查询的 authority-aware ranking

在 `experienceQuery=false` 且问题包含“准入/要求/条件/名额/考核/政策/转专业”等政策意图词时启用。排序键建议为：实体与年份兼容性硬约束 → 向量分 → 小幅 authority bonus；OFFICIAL/OFFICIAL_PDF 只在语义分差较小且 metadata 对齐时优先。经验查询不启用官方压制。

- 目标：TRAG-027，减少个人整理压过官方政策。
- 风险：错误学院的官方表格不能因为“官方”就压过正确个人资料；authority 必须晚于实体/年份兼容性检查。
- 设计限制：当前 QueryRewriteResult 没有 policyQuery 字段，可在 RetrievalService 内用最小关键词判定，避免修改 QueryRewrite。

### 5.4 推荐执行顺序与停止条件

1. 先实现 candidate/final TopK 分离并验证 TRAG-062。
2. 加有界 ±1 邻居扩展，检查上下文噪声和跨年级污染。
3. 最后加入保守的政策 authority 重排，专查 TRAG-027 与全部 PASS guards。
4. 不在同一迭代修改 threshold、metadata、alias、年份策略或 Chunker，以保持因果可解释。

## 6. 预计改善范围

- 高置信改善：TRAG-062。
- 可能改善但需验证：TRAG-018、TRAG-019（邻居只能缓解表格边界）、TRAG-027（候选池与官方重排必须同时命中正确官方 Chunk）。
- 不应指望本轮解决：Metadata/Alias 的12个、Year/cohort的6个、threshold 的TRAG-042，以及实际属于 Answerability 的TRAG-060/TRAG-061。

## 7. 后续评测流程

1. 每次 Retrieval 开发先跑 `evaluation/smoke-cases.json`。负向控制必须继续拒答，10个 baseline PASS guards 不得回归。
2. Smoke 通过后跑 `evaluation/retrieval-regression-cases.json`；重点检查 TRAG-062、TRAG-027 和8个 PASS guards。
3. 只有 Retrieval V2 准备收口时才重新跑完整82题。
4. 子集结果必须存入新的 iteration 目录，不覆盖 baseline 或 Iteration 1。
