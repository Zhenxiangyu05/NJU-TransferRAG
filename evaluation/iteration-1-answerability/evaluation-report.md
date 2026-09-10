# TransferRAG Evaluation Report — Iteration 1 Answerability

本轮仅修改 Answerability / Evidence Sufficiency，并使用与 baseline 完全相同的 82 个问题复测。事实标准仍仅来自原始资料。

## 1. 数据集概况

- 原始资料文件：20
- 成功读取并可判读：16
- 学院/大类标签覆盖：24
- 专业/方向标签覆盖：20
- 年份覆盖：2024、2025、2026 及无显式年份经验资料
- 问题数量与实际执行数量：82 / 82
- 测试集：沿用 `evaluation/test-cases.json`，未新增、删减或改写案例

## 2. 总体结果

| 指标 | 数量 | 占比 |
|---|---:|---:|
| PASS | 37 | 45.12% |
| REFUSAL_FALSE_NEGATIVE | 35 | 42.68% |
| PARTIAL | 8 | 9.76% |
| WRONG | 0 | 0.00% |
| WRONG_SOURCE | 2 | 2.44% |
| YEAR_MISMATCH | 0 | 0.00% |
| DEPARTMENT_MISMATCH | 0 | 0.00% |
| HALLUCINATION | 0 | 0.00% |
| SYSTEM_ERROR | 0 | 0.00% |

- Answer Accuracy / Pass Rate：**37 / 82 = 45.12%**
- 资料中有答案但系统拒答率：**35 / 82 = 42.68%**
- 与 baseline 比较：Pass Rate 43.90% → 45.12%；False Refusal Rate 46.34% → 42.68%

## 3. 按类别统计

| 类别 | 测试数 | PASS | 失败 | Pass Rate |
|---|---:|---:|---:|---:|
| 别名 | 1 | 0 | 1 | 0.00% |
| 培养方案 | 4 | 3 | 1 | 75.00% |
| 学习经验 | 7 | 3 | 4 | 42.86% |
| 时间敏感 | 1 | 1 | 0 | 100.00% |
| 明确事实 | 8 | 2 | 6 | 25.00% |
| 条件 | 6 | 2 | 4 | 33.33% |
| 组合问题 | 2 | 0 | 2 | 0.00% |
| 经验 | 5 | 2 | 3 | 40.00% |
| 综合生存指南 | 4 | 4 | 0 | 100.00% |
| 课程规划 | 15 | 9 | 6 | 60.00% |
| 转专业政策 | 16 | 6 | 10 | 37.50% |
| 面试/机试 | 12 | 4 | 8 | 33.33% |
| 风险经验 | 1 | 1 | 0 | 100.00% |

## 4. 按学院统计

| 学院/大类 | 测试数 | PASS | 失败 | Pass Rate |
|---|---:|---:|---:|---:|
| 人工智能学院 | 5 | 4 | 1 | 80.00% |
| 信息管理学院 | 1 | 0 | 1 | 0.00% |
| 化学化工学院 | 1 | 1 | 0 | 100.00% |
| 化学化工学院/生命科学学院 | 2 | 2 | 0 | 100.00% |
| 南赫学院 | 5 | 2 | 3 | 40.00% |
| 地学大类 | 4 | 2 | 2 | 50.00% |
| 地理科学与海洋学院 | 1 | 0 | 1 | 0.00% |
| 工科试验班 | 2 | 1 | 1 | 50.00% |
| 技术科学试验班 | 3 | 1 | 2 | 33.33% |
| 数学学院 | 4 | 2 | 2 | 50.00% |
| 数理大类 | 2 | 0 | 2 | 0.00% |
| 文学院 | 7 | 2 | 5 | 28.57% |
| 智能科学与技术学院 | 5 | 3 | 2 | 60.00% |
| 机器人与自动化学院 | 1 | 1 | 0 | 100.00% |
| 法学院 | 6 | 6 | 0 | 100.00% |
| 物理学院 | 1 | 1 | 0 | 100.00% |
| 现代工程与应用科学学院 | 6 | 2 | 4 | 33.33% |
| 生命科学学院 | 1 | 1 | 0 | 100.00% |
| 电子科学与工程学院 | 10 | 2 | 8 | 20.00% |
| 社会学院 | 1 | 0 | 1 | 0.00% |
| 秉文书院/人文大类 | 4 | 1 | 3 | 25.00% |
| 计算机学院 | 6 | 2 | 4 | 33.33% |
| 软件学院 | 3 | 0 | 3 | 0.00% |
| 集成电路学院 | 1 | 1 | 0 | 100.00% |

## 5. 按年份统计

| 年份 | 测试数 | PASS | 失败 | Pass Rate |
|---|---:|---:|---:|---:|
| 2024 | 6 | 2 | 4 | 33.33% |
| 2025 | 30 | 19 | 11 | 63.33% |
| 2026 | 43 | 13 | 30 | 30.23% |
| 其他/未显式 | 3 | 3 | 0 | 100.00% |

## 6. 失败原因统计

| 根因 | 数量 |
|---|---:|
| Answerability 误拒绝 | 12 |
| 最终回答遗漏 | 8 |
| Retrieval 未召回正确 Chunk | 8 |
| EntityAlias / department 元数据 | 7 |
| 年份策略 / 索引元数据 | 4 |
| Top1 < 0.55 | 4 |
| 错误来源 / 事实生成 | 2 |

关键观察：Answerability 误拒绝仍有 12 个，其中 TRAG-008 是从 baseline PASS 新增的回归。另有 5 个原 Answerability 失败不再拒答，但只有 3 个成为 PASS；TRAG-009 变为 PARTIAL，TRAG-023 变为 WRONG_SOURCE。

## 7. 最典型的 10 个失败案例

### TRAG-008

- Question：2026年汉文二次选拔的初试和复试形式是什么？
- Expected：初试闭卷笔试2小时100分；复试面试10分钟；自我陈述2分钟；针对性提问8分钟
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=20, chunk=173, year=2026, dept=-, score=0.6232510805130005; doc=20, chunk=175, year=2026, dept=-, score=0.613408625125885; doc=20, chunk=172, year=2026, dept=-, score=0.5964897871017456; doc=20, chunk=174, year=2026, dept=-, score=0.5693745017051697; doc=22, chunk=218, year=2026, dept=-, score=0.5442665815353394
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Answerability
- Reason：诊断 Top1 已直接包含初试、复试、时长和分值的完整证据，但 V2 最终拒答；这是原 PASS 的 Answerability 回归。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-023

- Question：2026年软件工程转专业至少要修软件学院哪几门基础课中的几门？
- Expected：计算系统基础；C语言程序设计基础；软件工程与计算I；离散数学；四门中至少2门并取得学分
- Actual：根据当前知识库中的经验资料，2026年软件工程转专业要求修读以下四门专业准入课程：C语言程序设计基础（CPL）、计算系统基础 I、离散数学、软件工程与计算 I [S1][S2]。对于**2025级及以后**的学生，这四门课程均为必修准入课程，即需要全部修完 [S1][S2]；对于**2024级及以前**的学生，则只需完成其中两门即可 [S1][S2]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Retrieved Sources：S1 02 申请条件 (doc=8, year=2026, dept=软件学院, score=0.7236602902412415); S2 02 申请条件 (doc=17, year=2026, dept=软件学院, score=0.7236602902412415)
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.7236602902412415; doc=17, chunk=155, year=2026, dept=-, score=0.7236602902412415; doc=11, chunk=82, year=2026, dept=-, score=0.693400502204895; doc=5, chunk=37, year=2026, dept=-, score=0.6857840418815613; doc=5, chunk=38, year=2026, dept=-, score=0.6799354553222656
- Classification：WRONG_SOURCE
- Root Cause：Source Selection / Generation
- Reason：问题询问2026年官方要求，回答引用经验资料并错误声称2025级以后四门专业课必须全部修完；Ground Truth 为四门中至少修2门并取得学分。
- Suggested Fix：显式年份政策问题应优先采用对应年份官方来源，并避免把经验资料改写为政策；本轮不修改检索或生成。

### TRAG-022

- Question：2026年人工智能学院转专业的考核形式是什么？
- Expected：资格审核后组织综合考核面试；按综合考核成绩确定名单
- Actual：根据南京大学2026年官方发布的准入计划，人工智能学院转专业的考核形式为**综合考核（面试）**[S2]。
- Retrieved Sources：S2 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表 (doc=6, year=2026, dept=本科生院, score=0.6734434962272644)
- Diagnostic TopK：无
- Classification：PARTIAL
- Root Cause：Generation
- Reason：回答只说明考核形式为面试，遗漏按综合考核成绩确定名单这一录取规则。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-033

- Question：人文大类学生想学有所获，指南建议重点培养哪些能力？
- Expected：多看专著和前沿论文；分析能力；知识功底；文献整理和信息提取能力
- Actual：根据当前知识库中的经验资料，人文大类学生想要学有所获，指南建议重点培养以下能力：**分析能力、知识功底、文献整理和信息提取能力**（尤其是对历史、哲学的同学）[S1][S3]。此外，学习新传的同学还应积极拓宽知识面并投身自媒体实践[S3]。该信息并非官方政策，请以学校或学院最新通知为准。
- Retrieved Sources：S1 南京大学人文科学试验班求生指南 (doc=28, year=2026, dept=*人文科学试验班, score=0.6766660213470459); S3 南京大学人文科学试验班求生指南 (doc=28, year=2026, dept=*人文科学试验班, score=0.5815918445587158)
- Diagnostic TopK：无
- Classification：PARTIAL
- Root Cause：Generation
- Reason：回答列出分析、知识功底和文献信息能力，但遗漏多看专著和前沿论文这一学习建议。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-079

- Question：南赫学院微积分II有什么学习特点和建议？
- Expected：套路性强、计算量大；强调应用和做题而非证明；以作业和quiz为导向；需要微积分I基础
- Actual：根据南赫学院的经验资料，微积分II的主要特点是**套路性强、计算量大**，是微积分I的加强版，且大多数定理证明复杂，因此**不强调证明，而强调对知识的运用** [S3]。 学习建议如下： - 以**作业和quiz题目为导向**出发学习，重点在计算、应用和做题 [S3]。 - 充分掌握课本《James Stewart - Calculus》上的题目 [S3]。 - 推荐B站up主“一高数”，但需**选择性看课**，因为部分知识点不完全对应 [S3]。 - 可利用AI辅助学习，但需注意AI可能有计算错误，要以参考答案矫正 [S3]。 - 考前练习的指导意义很大，建议以考前练习为导向发现不足 [S3]。 > 该信息并非官方政策，请以学校或学院最新通知为准。
- Retrieved Sources：S3 南赫生存手册v0（完整版） (doc=24, year=2026, dept=南京赫尔辛基大气与地球系统科学学院, score=0.6621659398078918)
- Diagnostic TopK：无
- Classification：PARTIAL
- Root Cause：Generation
- Reason：回答覆盖微积分II的做题、应用和作业导向，但未明确说明学习需要微积分I基础。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-082

- Question：大气物理与大气动力学在第一性原理方面有什么差别？
- Expected：大气动力学以Navier-Stokes方程为核心；大气物理缺少兼顾可靠和实用的第一性原理；大气物理关注非绝热加热和湍流混合等参数化问题
- Actual：根据当前知识库中的经验资料，大气物理与大气动力学在第一性原理方面的主要差别是：大气动力学以 Navier-Stokes 方程作为可靠且实用的“第一性原理”，而大气物理学则没有这样兼顾可靠和实用的“第一性原理”[S1][S2]。该信息并非官方政策，请以学校或学院最新通知为准。
- Retrieved Sources：S1 南赫生存手册v0（完整版） (doc=24, year=2026, dept=南京赫尔辛基大气与地球系统科学学院, score=0.7311747670173645); S2 南赫生存手册v0（完整版） (doc=24, year=2026, dept=南京赫尔辛基大气与地球系统科学学院, score=0.6662527322769165)
- Diagnostic TopK：无
- Classification：PARTIAL
- Root Cause：Generation
- Reason：回答比较了第一性原理，但遗漏大气物理重点处理非绝热加热、湍流混合等参数化问题。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-003

- Question：2026级智能科学与技术大一上建议修哪些数学和编程课程？
- Expected：微积分I第一层次；线性代数第一层次；智能程序设计C语言
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=18, chunk=156, year=2026, dept=-, score=0.7331855297088623; doc=18, chunk=162, year=2026, dept=-, score=0.689579427242279; doc=18, chunk=160, year=2026, dept=-, score=0.6813269257545471; doc=18, chunk=161, year=2026, dept=-, score=0.6736702919006348; doc=18, chunk=163, year=2026, dept=-, score=0.6642405986785889
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Answerability
- Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-028

- Question：2026年社会学院大一跨类准入需要哪些课程条件？
- Expected：已取得社会与心理科学导论学分；社会学概论、社会工作概论、心理学概论上中在修任意1门
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6509084701538086; doc=20, chunk=172, year=2026, dept=-, score=0.6491811275482178; doc=6, chunk=123, year=2026, dept=-, score=0.6358058452606201; doc=6, chunk=134, year=2026, dept=-, score=0.6146427392959595; doc=6, chunk=126, year=2026, dept=-, score=0.6143920421600342
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Year Strategy / Index Metadata
- Reason：问题的年份或年级条件与召回证据未可靠对齐，正确年份证据不可见或被其他年份/年级内容替代，最终拒答。
- Suggested Fix：后续单独核查正文年份、effectiveYear 与年级行绑定；本轮不改 metadata 或索引。

### TRAG-025

- Question：2026年计算机科学与技术转专业需要修哪两门准入课，成绩要求多少？
- Expected：离散数学；程序设计基础或计算机程序的构造和解释；成绩80分及以上
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.694525957107544; doc=17, chunk=155, year=2026, dept=-, score=0.694525957107544; doc=18, chunk=156, year=2026, dept=-, score=0.6735349893569946; doc=6, chunk=128, year=2026, dept=-, score=0.6448765397071838; doc=6, chunk=153, year=2026, dept=-, score=0.6333624720573425
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：EntityAlias / Department Metadata
- Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-015

- Question：2026年文学院汉语言文学转专业需要修哪些准入课程、至少几门？
- Expected：古代汉语上；古代汉语下；中国古代文学一；中国古代文学二；至少3门共9学分
- Actual：根据当前知识库资料无法确定。
- Retrieved Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.7835471630096436; doc=20, chunk=173, year=2026, dept=-, score=0.7236139178276062; doc=20, chunk=175, year=2026, dept=-, score=0.6751245856285095; doc=20, chunk=178, year=2026, dept=-, score=0.6272296905517578; doc=20, chunk=174, year=2026, dept=-, score=0.6011740565299988
- Classification：REFUSAL_FALSE_NEGATIVE
- Root Cause：Retrieval
- Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

## 8. 执行与边界

- `/api/rag/ask` 串行执行，每个 case 后原子保存，支持按 caseId 断点续跑。
- `/api/test/search?topK=10` 只用于失败诊断；正式判定以 `/api/rag/ask` 最终回答为准。
- 未修改 RetrievalService、QueryRewriteService、最终回答 Prompt、阈值、TopK、数据库、Qdrant、知识库文件、EntityAlias、年份策略或 metadata。
- 本轮结论是 baseline 后第一次 Answerability 迭代结果，不继续自动优化。
