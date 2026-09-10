# Metadata / Entity Resolution Iteration — Read-only Analysis

本轮只读取代码、baseline 评测文件、MySQL、Qdrant 和 `/api/test/search`。未修改生产代码、EntityAlias、数据库、Qdrant、知识库文件或原 82 题。

## 1. 结论摘要

原先归入 Metadata / Alias 的 12 题中，按主根因复核后只有 **8 题**应继续留在 Metadata Iteration：`TRAG-064, 066, 067, 073, 074, 075, 076, 077`。

- **Year / cohort：3 题** — `TRAG-015, 016, 025`。正确官方 Chunk 在 Qdrant 中存在，但 `effectiveYear` 为 2025/2024；问题中的 2026 硬过滤将其排除。
- **Index / Data Quality：1 题** — `TRAG-070`。Ground Truth 文件 `转电子指北.pdf` 在原始资料目录存在，但 MySQL/Qdrant 中没有对应 Document/Chunk。
- **Metadata / Entity Resolution：8 题** — 两个目标文档的 Qdrant `effectiveYear` 缺失；同时存在学院名称不规范、department/major 缺失和 alias 不完整。

关键对照实验：原问题请求 Top50 时，12 题的正确 Chunk 均未出现；用多年份诊断绕过单年过滤后，除无索引目标的 `TRAG-070` 外，其余 11 题的正确 Chunk 均进入 rank 1～7（`TRAG-064` 的补充 Chunk 88 为 rank 18，但主证据 Chunk 87 为 rank 1）。因此这些案例不支持优先扩大 candidateTopK。

## 2. 当前 EntityAlias 实况

数据库只有 4 行：

| standardName | alias | entityType | department | 评价 |
|---|---|---|---|---|
| 光电系统信息材料实验班 | 光电 | PROGRAM | NULL | “光电”过于歧义；对 `光电信息类/转光电` 会映射到特定实验班，且无法推出学院 |
| 软件学院 | 软院 | DEPARTMENT | NULL | 能扩写文本，但 Retrieval 只读 `MatchedEntity.department`，因此不能生成软件学院过滤 |
| 现代工程学院 | 现工 | DEPARTMENT | NULL | 同样不能生成 department 过滤；standardName 还是非统一简称 |
| 汉语言文学 | 汉语言 | MAJOR | 文学院 | 当前唯一能稳定产生 canonical department 的记录 |

当前表结构对“一个别名 → 一个标准实体 → 一个学院”的学院、专业、单学院项目基本够用。它不足以可靠表达：

- 同一个短词的多义实体，例如“光电”“电子”；
- 技术科学试验班这类多学院大类，因为 `department` 只能保存一个字符串；
- 查询中的目标、比较对象和排除对象，例如“软院之外，电子学院……”；
- alias 优先级、置信度、有效年份和规范实体 ID。

所以 Metadata V2 的最小阶段不必立即扩表，但只能录入高置信的一对一映射；歧义词和多学院大类必须允许“不下硬过滤”。

建议语义：

- `计算机科学与技术`：`MAJOR`，department=`计算机学院`。
- `光材`：`PROGRAM`，standardName=`光电系统信息材料实验班`，department=`现代工程与应用科学学院`。
- `电子学院`：`DEPARTMENT`，standardName=`电子科学与工程学院`，department 同 standardName。
- `电子信息类`：更适合作为 `PROGRAM`（或现有字符串类型中的大类类型），department=`电子科学与工程学院`。
- 裸词 `电子`：不建议无条件单行映射；优先匹配“电子学院/转电子/电子专业”等长短语并结合意图。
- `技科`：可先作为 `PROGRAM` 参与 query expansion，但在当前单 department 模型下不应生成硬学院过滤。

## 3. QueryRewriteService 行为

1. 当前是 **expansion**，不是 replacement。例如 `光电` 会变成 `光电（光电系统信息材料实验班）`。
2. 文本改写会按长度优先选择不重叠 Match；但所有 `matchedEntities` 在选最长 Match 之前就已加入。也就是说，“最长匹配”只保证改写文本，不保证最终实体集合只保留最长实体。
3. 多实体全部累积，Retrieval 将所有非空 department 用 OR 连接；没有 target/comparison/excluded 角色。
4. major 只有在 EntityAlias 行的 `department` 已填写时才能推出学院。
5. 同一 alias 可由多行映射到多个实体，当前行为是同时输出多个实体和多个 department，不做消歧。
6. 多学院大类无法用单个 `department` 正确表达。
7. 匹配使用 `String.contains/indexOf`，没有中文短语边界、ASCII 大小写归一或否定上下文，存在 substring 误匹配风险。
8. “软件工程”和“软件学院”只有在分别存在完整实体行时才可能区分；当前库没有“软件工程”行。
9. “电子”与“电子科学与工程学院”当前都没有记录；若简单添加裸词“电子”，还会放大 substring 和歧义问题。
10. 查询没有“学院”二字仍可识别，但前提是 alias/standardName 已配置。例如“软院”能匹配；只是该行 department 为 NULL，所以不能过滤。

## 4. Metadata Filter 实际行为

Retrieval 只从 `MatchedEntity.department` 提取学院，不使用 matched major 构建过滤。两个实际过滤模板为：

```text
F1 = (
  department == targetDepartment
  OR (scope == GLOBAL AND sourceType == OFFICIAL
      AND chunkDepartment == targetDepartment)
) AND effectiveYear == targetYear

F2 = effectiveYear == targetYear
```

`TRAG-015/016` 使用 F1（文学院，2026）；其余 10 题没有得到非空 targetDepartment，实际只使用 F2（均解析或推断为 2026）。只要 strict 查询返回任意结果就不会执行 fallback。

### 典型推导

`2026年计算机科学与技术转专业需要修哪两门准入课？`

```text
expected: department=计算机学院, major=计算机科学与技术, year=2026
actual rewrite: unchanged
actual matchedEntities: []
actual targetDepartment: []
actual resolvedYear: 2026
actual Qdrant filter: effectiveYear == 2026
```

正确 Chunk 139/140 的 `chunkDepartment=计算机学院`、`major=计算机科学与技术`，但 `effectiveYear=2025/2024`，所以被年份条件排除。缺少 major 映射是真问题，但不是这次拒答的主因。

## 5. 12 个案例逐项复核

| caseId | 用户表达 → 应解析 | 当前 QueryRewrite / filter | Ground Truth 与 metadata | TopK 复核与主结论 |
|---|---|---|---|---|
| TRAG-015 | `文学院` + `汉语言文学` → MAJOR 汉语言文学 / 文学院 | 汉语言文学命中现有行并扩写为“汉语言文学（汉语言）”；F1 文学院+2026 | doc6 chunk116；MySQL/Qdrant chunkDepartment=文学院、major=汉语言文学、policy/effectiveYear=2025 | 原Top50无chunk116；绕过单年过滤后rank3。**Year/cohort，不是 Alias** |
| TRAG-016 | `汉语言文学` → MAJOR / 文学院 | 同上；F1 文学院+2026 | 同 doc6 chunk116，effectiveYear=2025 | 原Top50无；绕过后rank6。**Year/cohort** |
| TRAG-025 | `计算机科学与技术` → MAJOR / 计算机学院 | 无实体命中；F2 2026 | doc6 chunks139/140，chunkDepartment=计算机学院、major正确，effectiveYear=2025/2024 | 原Top50只有doc6其他行，正确Chunk无；绕过后rank2/4。**Year/cohort，次要缺 major 映射** |
| TRAG-064 | `光电信息类` → 光电信息科学与工程 / 现代工程与应用科学学院 | 子串`光电`被错误扩到实验班，department=NULL；F2 2026 | doc13 chunks87/88；Document.department=`现代工程学院`，chunk department/major NULL；Qdrant effectiveYear NULL | 原Top50无；绕过后chunk87 rank1、88 rank18。**Metadata + alias歧义** |
| TRAG-066 | `转光电` → 光电信息科学与工程 / 现代工程与应用科学学院 | `光电`映射到实验班且department=NULL；F2 推断2026 | doc13 chunk88；同上 | 原Top50无；绕过后rank2。**Metadata + alias映射不完整** |
| TRAG-067 | `光材` → PROGRAM 光电系统信息材料实验班 / 现代工程与应用科学学院 | 无 alias；F2 2026 | doc13 chunk87；同上 | 原Top50无；绕过后rank1。**Metadata + 简称缺失** |
| TRAG-070 | `电子` → 电子科学与工程学院，并可关联电子信息类 | 无实体；F2 推断2026 | Ground Truth 是旧版 `转电子指北.pdf`；MySQL/Qdrant 无对应 Document。2026版doc14不能替代指定GT | 无目标Chunk可排名。**Index/Data Quality，非纯 Metadata** |
| TRAG-073 | `电子学院` → DEPARTMENT 电子科学与工程学院 | 无实体；F2 2026 | doc14 chunks94/95；Document.department=`电子信息类`，chunks department/major NULL；Qdrant effectiveYear NULL、scope缺失 | 原Top50无；绕过后rank3/1。**Metadata + alias缺失** |
| TRAG-074 | `软院之外`是排除/比较对象；目标`电子学院` | 只命中软院并扩写，但其department=NULL；电子学院未命中；F2 2026 | doc14 chunks96/97/98；metadata同上 | 原Top50无；绕过后rank7/6/1。**Metadata；另有实体角色 Code Bug** |
| TRAG-075 | `电子学院` → DEPARTMENT 电子科学与工程学院 | 无实体；F2 2026 | doc14 chunk98；metadata同上 | 原Top50无；绕过后rank1。**Metadata；不是 threshold 主因** |
| TRAG-076 | `电子专业` → PROGRAM/MAJOR 电子信息类 → 电子科学与工程学院 | 无实体；F2 推断2026 | doc14 chunk99；metadata同上 | 原Top50无；绕过后rank6。**Metadata + major/program→department 缺失** |
| TRAG-077 | `转电子` → 电子科学与工程学院 + 电子信息类 | 无实体；F2 2026 | doc14 chunks94/95；metadata同上 | 原Top50无；绕过后rank1/3。**Metadata + alias缺失** |

## 6. 问题分类与计数

### 主根因（互斥）

| 主类 | 数量 | caseId |
|---|---:|---|
| Metadata / Entity Resolution | 8 | TRAG-064, 066, 067, 073, 074, 075, 076, 077 |
| Year / cohort | 3 | TRAG-015, 016, 025 |
| Index / Data Quality | 1 | TRAG-070 |

### Metadata 子类（可重叠）

| 子类 | 数量 | caseId | 典型示例 |
|---|---:|---|---|
| A. 简称/表达缺失 | 7 | 067, 070, 073, 074, 075, 076, 077 | 光材、电子、电子学院、电子专业、转电子 |
| B. major/program → department 缺失或不可靠 | 4 | 025, 064, 066, 076 | 计算机科学与技术无实体行；光电行department为空 |
| C. 大类/方向/歧义表达 | 5 | 064, 067, 070, 076, 077 | 光电信息类、光材、电子/电子信息类 |
| D. Document.department 错误或非 canonical | 8 | 064, 066, 067, 073, 074, 075, 076, 077 | doc13=`现代工程学院`；doc14把`电子信息类`填在学院字段 |
| E. Chunk.department / major 缺失 | 8 | 064, 066, 067, 073, 074, 075, 076, 077 | doc13/doc14 所有目标 Chunk 两字段均为 NULL |
| F. Qdrant metadata 不完整/不一致 | 8 | 064, 066, 067, 073, 074, 075, 076, 077 | 两文档 `effectiveYear` 缺失；doc14 的 Qdrant scope 缺失而 MySQL 为 DEPARTMENT |
| G. QueryRewrite 正确但 Filter 构造错误 | 0 | — | 过滤表达式机械地使用了 rewrite 结果；未发现直接构造错误 |
| H. 实际不是 Metadata 主问题 | 4 | 015, 016, 025, 070 | 3个 Year/cohort，1个未导入文档 |

补充：`TRAG-074` 是 Query Understanding 的代码语义缺口。若只把 `软院` 的 department 补齐，当前逻辑反而会把“软院之外”当目标学院并进行错误硬过滤。因此它不能靠补数据单独解决。

## 7. Code Bug 与 Bad Metadata 分离

### Code / model limitations

- 最长匹配没有同步约束 `matchedEntities`，重叠短实体仍可能进入过滤。
- `DEPARTMENT` 实体不会默认以 `standardName` 作为 department，完全依赖可空的 `EntityAlias.department`。
- 没有 majors/departments 的结构化输出，也没有 target/comparison/excluded 角色。
- substring 匹配会误识别短词；多实体只做 department OR。
- Retrieval 不读取 major，且 strict 有任意结果就不 fallback；这会放大错误实体/metadata 的影响。
- 本批案例没有证据表明 `buildMetadataFilter` 自身拼错字段或条件。

### Bad metadata / index state

- EntityAlias 只有4行；`光电`映射过度具体且department为空，两个 DEPARTMENT alias 行也都没有department。
- doc13 的学院名不是评测和其他资料采用的 canonical 全称。
- doc14 把专业大类 `电子信息类` 写入 Document.department。
- doc13/doc14 的目标 Chunk 均缺 department 和 major。
- Qdrant 中 doc13/doc14 的 `effectiveYear` 缺失；doc14 的 scope 也落后于 MySQL。
- doc6 的 MySQL policyYear 与 Qdrant effectiveYear 一致，不是索引不同步；它是“文件年份/政策行年份/cohort”语义策略问题。
- TRAG-070 的旧版 Ground Truth 文件未建立 MySQL Document 和 Qdrant Chunk，属于未导入数据。

## 8. Metadata V2 最小设计（不实现）

### 8.1 先建立 canonical contract 与只读一致性检查

确定唯一学院标准名，并检查每个已上传 Document/Chunk 的 MySQL 与 Qdrant：documentId、department、scope、sourceType、year/effectiveYear、chunkDepartment、major、policyYear。输出差异报告并阻止“数据库显示已上传但关键 payload 缺失”的索引进入评测。

这一步直接针对 064/066/067/073～077。注意：必须先修数据/索引，再启用更严格实体过滤，否则 canonical alias 会把现有非规范文档彻底排除。

### 8.2 在现有表上先补高置信映射

不立即扩字段。先覆盖一对一实体：学院全称与常用简称、major→department、单学院 program→department。`DEPARTMENT` 行要求 department 等于 canonical standardName；裸词“电子/光电”保持保守，优先配置更长短语。对技科等多学院项目只参与 query expansion，不产生单学院硬过滤。

### 8.3 最小 ResolvedQuery

在现有 QueryRewrite 结果旁增加一个小型结构化结果，而非重构整个 RAG：

```text
ResolvedQuery {
  normalizedQuery,
  departments,
  majors,
  explicitYear,
  experienceQuery,
  matchedEntities
}
```

`matchedEntities` 至少保留 span 和 target/comparison/excluded 角色，或在最小版本中明确排除“之外/相比/不同于”附近实体。最长匹配必须同时决定 rewritten text 和 resolved entities；ASCII alias 做大小写归一。

### 8.4 Filter 只使用 canonical、无歧义结果

- department 只来自 canonical resolver；major 映射可推出 department，但保留 major 供诊断和后续二级过滤。
- 歧义词、多学院项目或冲突实体时不要下单学院硬过滤，回退到无 department 过滤并记录原因。
- strict filter 本身无需大改；先让输入 canonical 且让 Qdrant payload 完整。
- 增加可观测诊断：normalizedQuery、resolved departments/majors/year、最终 filter、fallback 是否发生。

### 8.5 预计效果与边界

- 高概率改善：`TRAG-064, 066, 067, 073, 075, 076, 077`。
- `TRAG-074` 还要求最小的否定/比较实体角色处理，否则补齐软院 mapping 可能造成 regression。
- 不应由本轮解决：`TRAG-015, 016, 025`（Year/cohort）；`TRAG-070`（导入/索引数据）；当前没有需明确转给 Answerability 的案例。
- 由于绕过单年过滤后正确 Chunk 已位于前列，这 8 题不需要先做 candidateTopK、Neighbor Expansion 或 threshold 调整。

## 9. Metadata Regression Set

`evaluation/metadata-regression-cases.json` 共 **22 题**，完全复用原 Ground Truth：

- 12 个待复核边界：`TRAG-015, 016, 025, 064, 066, 067, 070, 073, 074, 075, 076, 077`。
- 10 个 baseline PASS guards：`TRAG-006, 008, 010, 021, 047, 063, 065, 069, 071, 078`。

其中 065/069/071 特别保护 doc13/doc14 在“经验跨年份/多年份”路径下当前能够召回的行为；006/008/010 保护现有汉语言文学→文学院解析；021 保护 GLOBAL OFFICIAL + chunkDepartment；047、063、078 保护学院/简称未解析时的现有召回。

后续建议仍按：smoke → metadata regression → 相关 retrieval regression；只有 Metadata V2 收口时再跑完整82题。
