# TransferRAG Evaluation Report

本报告为只读 baseline 评测。事实标准仅来自指定原始资料；未使用模型常识补充 Ground Truth。

## 1. 数据集概况

- 扫描原始文件数：20
- 文件类型：PDF 13，DOCX 5，MD 2，TXT 0
- 成功读取并可判读：16
- 无法读取或无法判读：4
- 学院/大类标签覆盖：24
- 专业/方向标签覆盖：20
- 年份覆盖：2024、2025、2026 及无显式年份经验资料
- 问题数量：82

### 文件清单

| 文件 | 类型 | 读取状态 | 学院/专业推测 | 年份 | 主要内容 | 备注 |
|---|---:|---|---|---|---|---|
| 2025汉文转专业指南.pdf | PDF | 不可判读 | 文学院/汉语言文学 | 2025 | 转专业政策/条件 | 扫描件，未抽取到正文 |
| 2026版匡院指北 (version 2).pdf | PDF | 不可判读 | 匡亚明学院 | 2026 | 综合生存指南、二次选拔 | 字体映射乱码 |
| 2026级智能科学与技术主修培养方案_28b996c9.pdf | PDF | 正常 | 智能科学与技术学院 | 2026 | 培养方案、课程规划 |  |
| 26分流转汉文指北.pdf | PDF | 正常 | 文学院/汉语言文学 | 2026 | 分流与转专业、面试经验 |  |
| NJU转院生存手册（第一版） (1).pdf | PDF | 不可判读 | 多学院 | 未明确 | 综合转专业指南 | 字体映射乱码 |
| 化生大类生存指南_.pdf | PDF | 正常 | 化学化工学院、生命科学学院 | 2025 | 课程规划、分流、综合指南 |  |
| 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf | PDF | 正常 | 本科生院/多学院 | 2026 | 官方转专业政策与条件 |  |
| 南京大学人文大类求生指南(1).docx | DOCX | 正常 | 人文大类/相关学院 | 更新至2025 | 课程、分流、转专业、学习经验 |  |
| 南京大学技术科学试验班新生生存指南.pdf | PDF | 正常 | 技术科学试验班 | 以2025级为主 | 分流、课程、学习经验 |  |
| 南京大学数学学院生存手册_250525.pdf | PDF | 不可判读 | 数学学院 | 2025 | 综合生存指南、保研经验 | 字体映射乱码 |
| 地学大类生存指南(2).docx | DOCX | 正常 | 地学大类四学院 | 2024 | 课程、分流、转专业 |  |
| 工科试验班生存指南_Revised by 吻安.docx | DOCX | 正常 | 工科试验班四学院 | 2026 | 课程、分流、转专业、学习经验 |  |
| 数理大类生存指北.pdf | PDF | 正常 | 数学、物理、大气、天文 | 2025 | 分流、课程、学习经验 |  |
| 法学转专业分享（更新至2025年）.docx | DOCX | 正常 | 法学院 | 更新至2025 | 转专业条件、笔试面试经验 |  |
| 转AI风险.md | MD | 正常 | 人工智能学院 | 未显式 | 课程与转专业风险、面试经验 |  |
| 转CS风险.md | MD | 正常 | 计算机学院 | 2026 | 转专业、笔试机试经验 |  |
| 转光电概述（新）.docx | DOCX | 正常 | 现代工程与应用科学学院 | 2026 | 转专业条件、课程与面试 |  |
| 转电子指北.pdf | PDF | 正常 | 电子科学与工程学院 | 更新至2025 | 转专业政策、课程、面试经验 |  |
| 转电子指北2026版.pdf | PDF | 正常 | 电子科学与工程学院 | 2026 | 转专业政策、课程、面试经验 |  |
| 难喝生存手册v0（完整版）.pdf | PDF | 正常 | 南赫学院/大气科学 | 2025 | 培养方案、课程、保研与生涯经验 |  |

## 2. 总体结果

| 指标 | 数量 | 占比 |
|---|---:|---:|
| PASS | 36 | 43.90% |
| REFUSAL_FALSE_NEGATIVE | 38 | 46.34% |
| PARTIAL | 4 | 4.88% |
| WRONG | 1 | 1.22% |
| WRONG_SOURCE | 1 | 1.22% |
| YEAR_MISMATCH | 2 | 2.44% |
| DEPARTMENT_MISMATCH | 0 | 0.00% |
| HALLUCINATION | 0 | 0.00% |
| SYSTEM_ERROR | 0 | 0.00% |

- Answer Accuracy / Pass Rate：**36 / 82 = 43.90%**
- 资料中有答案但系统拒答率：**38 / 82 = 46.34%**

## 3. 按类别统计

| 类别 | 测试数 | PASS | 失败 | Pass Rate |
|---|---:|---:|---:|---:|
| 别名 | 1 | 0 | 1 | 0.00% |
| 培养方案 | 4 | 3 | 1 | 75.00% |
| 学习经验 | 7 | 5 | 2 | 71.43% |
| 时间敏感 | 1 | 1 | 0 | 100.00% |
| 明确事实 | 8 | 3 | 5 | 37.50% |
| 条件 | 6 | 2 | 4 | 33.33% |
| 组合问题 | 2 | 0 | 2 | 0.00% |
| 经验 | 5 | 1 | 4 | 20.00% |
| 综合生存指南 | 4 | 3 | 1 | 75.00% |
| 课程规划 | 15 | 7 | 8 | 46.67% |
| 转专业政策 | 16 | 5 | 11 | 31.25% |
| 面试/机试 | 12 | 5 | 7 | 41.67% |
| 风险经验 | 1 | 1 | 0 | 100.00% |

## 4. 按学院统计

| 学院/大类 | 测试数 | PASS | 失败 | Pass Rate |
|---|---:|---:|---:|---:|
| 人工智能学院 | 5 | 5 | 0 | 100.00% |
| 信息管理学院 | 1 | 0 | 1 | 0.00% |
| 化学化工学院 | 1 | 1 | 0 | 100.00% |
| 化学化工学院/生命科学学院 | 2 | 1 | 1 | 50.00% |
| 南赫学院 | 5 | 4 | 1 | 80.00% |
| 地学大类 | 4 | 2 | 2 | 50.00% |
| 地理科学与海洋学院 | 1 | 0 | 1 | 0.00% |
| 工科试验班 | 2 | 1 | 1 | 50.00% |
| 技术科学试验班 | 3 | 1 | 2 | 33.33% |
| 数学学院 | 4 | 2 | 2 | 50.00% |
| 数理大类 | 2 | 0 | 2 | 0.00% |
| 文学院 | 7 | 3 | 4 | 42.86% |
| 智能科学与技术学院 | 5 | 3 | 2 | 60.00% |
| 机器人与自动化学院 | 1 | 0 | 1 | 0.00% |
| 法学院 | 6 | 5 | 1 | 83.33% |
| 物理学院 | 1 | 0 | 1 | 0.00% |
| 现代工程与应用科学学院 | 6 | 2 | 4 | 33.33% |
| 生命科学学院 | 1 | 1 | 0 | 100.00% |
| 电子科学与工程学院 | 10 | 2 | 8 | 20.00% |
| 社会学院 | 1 | 0 | 1 | 0.00% |
| 秉文书院/人文大类 | 4 | 2 | 2 | 50.00% |
| 计算机学院 | 6 | 1 | 5 | 16.67% |
| 软件学院 | 3 | 0 | 3 | 0.00% |
| 集成电路学院 | 1 | 0 | 1 | 0.00% |

## 5. 按年份统计

| 年份 | 测试数 | PASS | 失败 | Pass Rate |
|---|---:|---:|---:|---:|
| 2024 | 6 | 2 | 4 | 33.33% |
| 2025 | 30 | 18 | 12 | 60.00% |
| 2026 | 43 | 13 | 30 | 30.23% |
| 其他/未显式 | 3 | 3 | 0 | 100.00% |

旧年份事实的主要风险不是模型常识，而是显式年份过滤与统一 effectiveYear=2026 的索引元数据冲突：两个含 2023 年事实的问题在诊断搜索中直接返回 0 条。

## 6. 失败原因统计

| 根因 | 数量 |
|---|---:|
| Answerability 误拒绝 | 16 |
| Retrieval 未召回正确 Chunk | 10 |
| EntityAlias / department 过滤或元数据 | 7 |
| 最终回答遗漏或错误 | 5 |
| 年份策略或 effectiveYear 元数据 | 4 |
| Top1 < 0.55 | 4 |

诊断结论：

- 大量拒答并非 Qdrant 完全无结果；16 个案例的 `/api/test/search` TopK 已包含足够证据，最终仍被 Answerability fail-closed。
- 9 个案例属于正确 Chunk 未进入 TopK或只召回同文档相邻 Chunk；另有 4 个案例 Top1 低于 0.55。
- 7 个案例出现目标学院简称/实体未形成稳定过滤，TopK 被软件学院、匡院等其他资料占据。
- 4 个案例与年份/申请阶段绑定有关：2 个旧年份问题检索为 0，2 个回答取错年级或大一/大二行。
- 未发现 HTTP 500、超时、解析异常；本轮也没有足够证据认定 MySQL 与 Qdrant 存在文档索引状态不一致。

## 7. 最典型的 10 个失败案例

### TRAG-027

- Question：2026年软件工程转专业既要修哪些数学通修课，又要满足什么专业基础课要求？
- Expected：微积分I第一层次；微积分II第一层次；线性代数第一层次；四门专业基础课中至少2门
- Actual：根据当前知识库中的经验资料 [S1]，2026年软件工程转专业需要满足以下要求： **数学通修课** - 必须修读微积分 I（第一层次）和微积分 II（第一层次）[S1]。 - 如果是大二学生申请，还需修读线性代数（第一层次）[S1]。 **专业基础课（专业准入课程）** - 必须已修或在修以下四门课程：C语言程序设计基础（CPL）、计算系统基础 I、离散数学、软件工程与计算 I [S1]。 - 注意：对于2024级及以前的学生，四门中完成两门即可；2025级及以后则四门均为必修准入课程 [S1]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Retrieved Sources：S1 02 申请条件 (doc=8, year=2026, dept=软件学院, score=0.7848897576332092)
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.7848897576332092; doc=17, chunk=155, year=2026, dept=-, score=0.7848897576332092; doc=11, chunk=82, year=2026, dept=-, score=0.7096738815307617; doc=5, chunk=37, year=2026, dept=-, score=0.6978949308395386; doc=5, chunk=41, year=2026, dept=-, score=0.6908856630325317
- Classification：WRONG_SOURCE
- Root Cause：Retrieval
- Reason：问题询问2026年官方软件工程准入要求，但回答引用了另一份“02 申请条件”经验资料，并给出‘2025级以后四门专业课均必修、线代仅大二需要’等与官方原表不一致的条件。
- Suggested Fix：提高明确年份官方政策查询对 OFFICIAL 来源和对应年级行的优先级。

### TRAG-028

- Question：2026年社会学院大一跨类准入需要哪些课程条件？
- Expected：已取得社会与心理科学导论学分；社会学概论、社会工作概论、心理学概论上中在修任意1门
- Actual：根据南京大学本科生院2026年发布的跨大类专业准入计划，社会学院大一跨类准入需满足以下课程条件： 1. 申请时须已取得社会学院开设的《社会与心理科学导论》课程学分 [S1]； 2. 须已修或在修《社会学概论》《社会工作概论》《心理学概论（上）》3门课程，本学期结束时取得相应学分 [S1]； 3. 须至少已修或在修拟准入专业教学计划中的2门专业核心课，本学期结束时取得相应学分 [S1]。 以上条件均来自官方资料，请以学校或学院最新通知为准。
- Retrieved Sources：S1 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表 (doc=6, year=2026, dept=本科生院, score=0.6509084701538086)
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6509084701538086; doc=20, chunk=172, year=2026, dept=-, score=0.6491811275482178; doc=6, chunk=123, year=2026, dept=-, score=0.6358058452606201; doc=6, chunk=134, year=2026, dept=-, score=0.6146427392959595; doc=6, chunk=126, year=2026, dept=-, score=0.6143920421600342
- Classification：YEAR_MISMATCH
- Root Cause：Year Strategy
- Reason：问题询问2026年社会学院大一准入，回答取用了2024级旧要求（3门概论课全部修读并另修2门核心课），而2025级适用行仅要求三门概论课中在修任意1门。
- Suggested Fix：表格切块保留年级与学院行绑定，并对显式年份/当前准入批次精确过滤。

### TRAG-072

- Question：2026年大一转电子的六门准入课和最低成绩要求是什么？
- Expected：微积分I和II第一层次；大学物理I和II；电路分析；模拟电路；总评均不低于70
- Actual：根据官方资料，2026年大一转入电子科学与工程学院（电子专业）的准入课程与最低成绩要求如下： - **必须修读三门数学课程**：00010011A《微积分I(第一层次)》、00010011B《微积分II(第一层次)》、00010011C《线性代数（第一层次）》，每门课总评均须不低于70分 [S3]。 - **学科基础课程**：须取得至少15个学分，且每门课总评均不低于70分。学科基础课程包括：18000110A《大学物理Ⅰ》、18000110B《大学物理Ⅱ》、18000310《电路分析》、18001560T《电子学基础I实验》、18000610《模拟电路》、18000510《信号与系统》、18001570T《电子学基础II实验》 [S3]。 注意：学科基础课程共列出7门，并非固定6门，但要求修满至少15学分且…
- Retrieved Sources：S3 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表 (doc=6, year=2026, dept=本科生院, score=0.6128564476966858)
- Diagnostic TopK：doc=17, chunk=155, year=2026, dept=-, score=0.6278324127197266; doc=8, chunk=68, year=2026, dept=-, score=0.6278324127197266; doc=6, chunk=136, year=2026, dept=-, score=0.6128564476966858; doc=30, chunk=326, year=2026, dept=-, score=0.6111667156219482; doc=18, chunk=156, year=2026, dept=-, score=0.5978610515594482
- Classification：YEAR_MISMATCH
- Root Cause：Year Strategy
- Reason：问题询问2026年大一转电子的六门准入课，回答却使用了大二准入条件（含线性代数和至少15学分学科基础课）。
- Suggested Fix：在表格切块中显式绑定年级/申请阶段，检索和回答时校验‘大一/大二’。

### TRAG-031

- Question：2023级人文大类110人的分流和转专业人数如何分布？
- Expected：新传57人；历史22人；汉语国际教育11人；哲学5人；转专业15人
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：无
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Year Strategy / Index Metadata
- Reason：问题含明确旧年份，诊断检索返回0条；资料正文含该年份事实，但导入后的 effectiveYear 统一为2026，显式年份过滤使对应 Chunk 完全不可见。
- Suggested Fix：后续核对文档级年份与正文事件年份的建模及索引状态；本轮不改 metadata 或索引。

### TRAG-041

- Question：地学大类2023级分流去向人数是多少？
- Expected：大气17人；环境29人；地科13人；地海24人；转出39人
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：无
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Year Strategy / Index Metadata
- Reason：问题含明确旧年份，诊断检索返回0条；资料正文含该年份事实，但导入后的 effectiveYear 统一为2026，显式年份过滤使对应 Chunk 完全不可见。
- Suggested Fix：后续核对文档级年份与正文事件年份的建模及索引状态；本轮不改 metadata 或索引。

### TRAG-003

- Question：2026级智能科学与技术大一上建议修哪些数学和编程课程？
- Expected：微积分I第一层次；线性代数第一层次；智能程序设计C语言
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=18, chunk=156, year=2026, dept=-, score=0.7331855297088623; doc=18, chunk=162, year=2026, dept=-, score=0.689579427242279; doc=18, chunk=160, year=2026, dept=-, score=0.6813269257545471; doc=18, chunk=161, year=2026, dept=-, score=0.6736702919006348; doc=18, chunk=163, year=2026, dept=-, score=0.6642405986785889
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Answerability
- Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-015

- Question：2026年文学院汉语言文学转专业需要修哪些准入课程、至少几门？
- Expected：古代汉语上；古代汉语下；中国古代文学一；中国古代文学二；至少3门共9学分
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.7835471630096436; doc=20, chunk=173, year=2026, dept=-, score=0.7236139178276062; doc=20, chunk=175, year=2026, dept=-, score=0.6751245856285095; doc=20, chunk=178, year=2026, dept=-, score=0.6272296905517578; doc=20, chunk=174, year=2026, dept=-, score=0.6011740565299988
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Retrieval
- Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-034

- Question：2025级起技术科学试验班主要有哪四个分流方向？
- Expected：智能科学与技术；自动化机器人方向；集成电路设计与集成系统；数字经济
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=16, chunk=104, year=2025, dept=-, score=0.5408475995063782; doc=6, chunk=139, year=2025, dept=计算机学院, score=0.5272615551948547; doc=6, chunk=133, year=2025, dept=-, score=0.5170126557350159; doc=16, chunk=106, year=2025, dept=-, score=0.5138590335845947; doc=6, chunk=144, year=2025, dept=-, score=0.5136555433273315
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Retrieval - Top1 below 0.55
- Reason：诊断检索最高分为 0.541，低于0.55；正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-067

- Question：2026级光材二次拔尖的面试流程和题型有哪些？
- Expected：自我介绍；高中印象深刻的事；多个问题选答且准备1分钟；涉及光学隐身、信息材料、海市蜃楼、冷热杯破裂等
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=22, chunk=219, year=2026, dept=-, score=0.6067162156105042; doc=22, chunk=217, year=2026, dept=-, score=0.606399416923523; doc=22, chunk=218, year=2026, dept=-, score=0.604067325592041; doc=10, chunk=74, year=2026, dept=-, score=0.5681232810020447; doc=30, chunk=326, year=2026, dept=-, score=0.5678458213806152
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：EntityAlias / Department Metadata
- Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-070

- Question：电子转专业面试一般有哪些环节和提问范围？
- Expected：1分钟自我介绍；3到4分钟提问；可能问大学物理、电路分析、模拟电路等
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.6921214461326599; doc=22, chunk=219, year=2026, dept=-, score=0.6457149386405945; doc=10, chunk=76, year=2026, dept=-, score=0.6440854072570801; doc=10, chunk=79, year=2026, dept=-, score=0.6431617140769958; doc=22, chunk=217, year=2026, dept=-, score=0.6218868494033813
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：EntityAlias / Department Metadata
- Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

## 8. 评测约束与可复现性

- 正式判定以 `POST /api/rag/ask` 的最终回答为准；`/api/test/search?topK=10` 仅用于失败诊断。
- 调用串行执行，并在每个 case 后原子保存 `evaluation-results.json`；按 caseId 支持断点续跑。
- 本轮未修改 RetrievalService、QueryRewriteService、AnswerabilityService、Prompt、threshold、数据库、Qdrant、知识库文件、metadata 或 `.gitignore`。
- `evaluation/` 是评测产物目录，不属于生产代码；是否提交 Git 由项目维护者决定。
