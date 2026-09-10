# TransferRAG Failures

共 46 个非 PASS 案例。每个案例保留最终回答、正式 Sources 和诊断 TopK 摘要；完整原始 JSON 见 `evaluation-results.json`。

## Retrieval

案例数：14

### TRAG-015 REFUSAL_FALSE_NEGATIVE

- Question：2026年文学院汉语言文学转专业需要修哪些准入课程、至少几门？
- Expected：古代汉语上；古代汉语下；中国古代文学一；中国古代文学二；至少3门共9学分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.7835471630096436; doc=20, chunk=173, year=2026, dept=-, score=0.7236139178276062; doc=20, chunk=175, year=2026, dept=-, score=0.6751245856285095; doc=20, chunk=178, year=2026, dept=-, score=0.6272296905517578; doc=20, chunk=174, year=2026, dept=-, score=0.6011740565299988
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-016 REFUSAL_FALSE_NEGATIVE

- Question：2026年汉语言文学跨大类准入对第一学期平均学分绩和不及格记录有什么要求？
- Expected：平均学分绩4.0及以上；无不及格记录；无违纪行为
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.6810758709907532; doc=20, chunk=173, year=2026, dept=-, score=0.6560064554214478; doc=20, chunk=175, year=2026, dept=-, score=0.6377837657928467; doc=20, chunk=174, year=2026, dept=-, score=0.5512412786483765; doc=20, chunk=178, year=2026, dept=-, score=0.5382677912712097
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-018 REFUSAL_FALSE_NEGATIVE

- Question：2026年数学学院数学类转专业可用哪两套数学课程方案满足准入？
- Expected：数分I、II+高代I、II+解析几何；或微积分I、II第一层次+线性代数第一层次；两套满足一套即可
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=17, chunk=155, year=2026, dept=-, score=0.6731560230255127; doc=8, chunk=68, year=2026, dept=-, score=0.6731560230255127; doc=6, chunk=128, year=2026, dept=-, score=0.6612573862075806; doc=25, chunk=306, year=2026, dept=-, score=0.6480956077575684; doc=25, chunk=307, year=2026, dept=-, score=0.6436569094657898
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-019 REFUSAL_FALSE_NEGATIVE

- Question：2026年数学学院准入考核如何使用校内数学竞赛成绩？
- Expected：校内数学竞赛成绩可计附加分；数学专业类或非数学专业A类按课程方案对应
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=128, year=2026, dept=-, score=0.6108091473579407; doc=6, chunk=136, year=2026, dept=-, score=0.5995528697967529; doc=6, chunk=126, year=2026, dept=-, score=0.5877895951271057; doc=25, chunk=306, year=2026, dept=-, score=0.5876396298408508; doc=6, chunk=147, year=2026, dept=-, score=0.5872007012367249
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-027 WRONG_SOURCE

- Question：2026年软件工程转专业既要修哪些数学通修课，又要满足什么专业基础课要求？
- Expected：微积分I第一层次；微积分II第一层次；线性代数第一层次；四门专业基础课中至少2门
- Actual：根据当前知识库中的经验资料 [S1]，2026年软件工程转专业需要满足以下要求： **数学通修课** - 必须修读微积分 I（第一层次）和微积分 II（第一层次）[S1]。 - 如果是大二学生申请，还需修读线性代数（第一层次）[S1]。 **专业基础课（专业准入课程）** - 必须已修或在修以下四门课程：C语言程序设计基础（CPL）、计算系统基础 I、离散数学、软件工程与计算 I [S1]。 - 注意：对于2024级及以前的学生，四门中完成两门即可；2025级及以后则四门均为必修准入课程 [S1]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 02 申请条件 (doc=8, year=2026, dept=软件学院, score=0.7848897576332092)
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.7848897576332092; doc=17, chunk=155, year=2026, dept=-, score=0.7848897576332092; doc=11, chunk=82, year=2026, dept=-, score=0.7096738815307617; doc=5, chunk=37, year=2026, dept=-, score=0.6978949308395386; doc=5, chunk=41, year=2026, dept=-, score=0.6908856630325317
- Failure Reason：问题询问2026年官方软件工程准入要求，但回答引用了另一份“02 申请条件”经验资料，并给出‘2025级以后四门专业课均必修、线代仅大二需要’等与官方原表不一致的条件。
- Suggested Fix：提高明确年份官方政策查询对 OFFICIAL 来源和对应年级行的优先级。

### TRAG-034 REFUSAL_FALSE_NEGATIVE

- Question：2025级起技术科学试验班主要有哪四个分流方向？
- Expected：智能科学与技术；自动化机器人方向；集成电路设计与集成系统；数字经济
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=16, chunk=104, year=2025, dept=-, score=0.5408475995063782; doc=6, chunk=139, year=2025, dept=计算机学院, score=0.5272615551948547; doc=6, chunk=133, year=2025, dept=-, score=0.5170126557350159; doc=16, chunk=106, year=2025, dept=-, score=0.5138590335845947; doc=6, chunk=144, year=2025, dept=-, score=0.5136555433273315
- Failure Reason：诊断检索最高分为 0.541，低于0.55；正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-042 REFUSAL_FALSE_NEGATIVE

- Question：地海拔尖对数学层次有什么要求？
- Expected：需要一层次数学；微积分I一层次；微积分II一层次；线性代数
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=25, chunk=307, year=2026, dept=-, score=0.5169183611869812; doc=29, chunk=324, year=2026, dept=-, score=0.5134637355804443; doc=6, chunk=136, year=2026, dept=-, score=0.5116908550262451; doc=25, chunk=306, year=2026, dept=-, score=0.5052566528320312; doc=9, chunk=73, year=2026, dept=-, score=0.4988344609737396
- Failure Reason：诊断检索最高分为 0.517，低于0.55；正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-049 REFUSAL_FALSE_NEGATIVE

- Question：2024级数理大类计划分流比例和人数如何分配？
- Expected：数学28%/97人；物理46%/159人；大气14%/48人；天文12%/41人
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=140, year=2024, dept=计算机学院, score=0.5363843441009521; doc=6, chunk=143, year=2024, dept=软件学院, score=0.513130784034729; doc=6, chunk=138, year=2024, dept=-, score=0.48675960302352905; doc=6, chunk=118, year=2024, dept=法学院, score=0.4417182505130768; doc=6, chunk=120, year=2024, dept=哲学学院, score=0.43990767002105713
- Failure Reason：诊断检索最高分为 0.536，低于0.55；正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-060 REFUSAL_FALSE_NEGATIVE

- Question：转计算机前，资料建议如何平衡微积分II和笔试机试准备？
- Expected：微积分II需要大量投入；同时不能忽视离散、高程、计算系统等；平衡数学刷题与笔试机试内容
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=26, chunk=309, year=2026, dept=-, score=0.6658834218978882; doc=11, chunk=83, year=2026, dept=-, score=0.6473839282989502; doc=24, chunk=243, year=2026, dept=-, score=0.6456722021102905; doc=9, chunk=73, year=2026, dept=-, score=0.6380473375320435; doc=9, chunk=69, year=2026, dept=-, score=0.6341837644577026
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-061 REFUSAL_FALSE_NEGATIVE

- Question：转CS笔试的题量和数学题风格有什么经验？
- Expected：笔试2小时4题；微积分偏证明；不考多元函数微分积分、曲线曲面积分等下学期内容
- Actual：根据当前知识库资料无法确定转CS笔试的题量以及数学题的具体风格。现有资料[S2]仅为个人经验总结，其中提到“算法题也没做多少”“数学的往年卷做得不多”，但未给出题量范围或数学题的具体题型、难度等描述。该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S2 转计算机专业的风险 (doc=26, year=2026, dept=计算机学院, score=0.6408728361129761)
- Diagnostic TopK：doc=25, chunk=307, year=2026, dept=-, score=0.642494797706604; doc=26, chunk=308, year=2026, dept=-, score=0.6408728361129761; doc=26, chunk=309, year=2026, dept=-, score=0.6185469627380371; doc=21, chunk=202, year=2025, dept=-, score=0.6180916428565979; doc=9, chunk=73, year=2026, dept=-, score=0.5883561372756958
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-062 REFUSAL_FALSE_NEGATIVE

- Question：转CS机试失败案例中三道题大致是什么类型、得分如何？
- Expected：第一题大模拟60分；第二题状压DP约10分；第三题困难图论约10分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=9, chunk=69, year=2026, dept=-, score=0.5762456655502319; doc=26, chunk=308, year=2026, dept=-, score=0.5708751678466797; doc=9, chunk=73, year=2026, dept=-, score=0.5695652365684509; doc=26, chunk=310, year=2026, dept=-, score=0.56740802526474; doc=26, chunk=309, year=2026, dept=-, score=0.564179539680481
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-066 REFUSAL_FALSE_NEGATIVE

- Question：转光电面试为什么要重视数学和物理成绩？
- Expected：学院重视数学物理能力；面试官会看第一学期成绩；可能追问第二学期期中成绩
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=30, chunk=327, year=2026, dept=-, score=0.6313884258270264; doc=30, chunk=326, year=2026, dept=-, score=0.5966572165489197; doc=6, chunk=127, year=2026, dept=-, score=0.588994026184082; doc=22, chunk=218, year=2026, dept=-, score=0.5857593417167664; doc=25, chunk=307, year=2026, dept=-, score=0.5843404531478882
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-074 REFUSAL_FALSE_NEGATIVE

- Question：软院之外，电子学院的电路分析和模拟电路应如何学习？
- Expected：知识量大且琐碎；依赖课后自学；重理解记忆知识点；可参考课程视频和做题
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=11, chunk=83, year=2026, dept=-, score=0.625362753868103; doc=24, chunk=256, year=2026, dept=-, score=0.6211185455322266; doc=19, chunk=169, year=2026, dept=-, score=0.6053516864776611; doc=30, chunk=327, year=2026, dept=-, score=0.6008939743041992; doc=10, chunk=79, year=2026, dept=-, score=0.595953643321991
- Failure Reason：诊断 TopK 未包含足以覆盖全部 expectedFacts 的正确 Chunk，或正确文档仅召回了不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续检查切块边界、向量相关度、TopK 与来源优先级；本轮不改检索逻辑。

### TRAG-075 REFUSAL_FALSE_NEGATIVE

- Question：电子学院2026版C语言课程发生了什么变化？
- Expected：课程号改为18001790；从通修改为专业平台课；不对外开放；跨专业选修可能受限
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=11, chunk=81, year=2026, dept=-, score=0.5319957733154297; doc=18, chunk=160, year=2026, dept=-, score=0.5267254114151001; doc=18, chunk=163, year=2026, dept=-, score=0.5057293772697449; doc=18, chunk=156, year=2026, dept=-, score=0.5045052170753479; doc=30, chunk=326, year=2026, dept=-, score=0.5041753649711609
- Failure Reason：诊断检索最高分为 0.532，低于0.55；正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：检查向量召回、查询改写与切块表达；本轮不调整阈值。

## Metadata

案例数：7

### TRAG-025 REFUSAL_FALSE_NEGATIVE

- Question：2026年计算机科学与技术转专业需要修哪两门准入课，成绩要求多少？
- Expected：离散数学；程序设计基础或计算机程序的构造和解释；成绩80分及以上
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.694525957107544; doc=17, chunk=155, year=2026, dept=-, score=0.694525957107544; doc=18, chunk=156, year=2026, dept=-, score=0.6735349893569946; doc=6, chunk=128, year=2026, dept=-, score=0.6448765397071838; doc=6, chunk=153, year=2026, dept=-, score=0.6333624720573425
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-064 REFUSAL_FALSE_NEGATIVE

- Question：2026级跨专业准入光电信息类，准入课程最低要求是什么？
- Expected：普通物理力学、大学化学、普通物理热学中任意一门；已修或在修并在学期结束取得学分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6362829804420471; doc=30, chunk=326, year=2026, dept=-, score=0.6323796510696411; doc=6, chunk=136, year=2026, dept=-, score=0.6279793977737427; doc=6, chunk=128, year=2026, dept=-, score=0.6242436766624451; doc=6, chunk=129, year=2026, dept=-, score=0.6198489665985107
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-067 REFUSAL_FALSE_NEGATIVE

- Question：2026级光材二次拔尖的面试流程和题型有哪些？
- Expected：自我介绍；高中印象深刻的事；多个问题选答且准备1分钟；涉及光学隐身、信息材料、海市蜃楼、冷热杯破裂等
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=22, chunk=219, year=2026, dept=-, score=0.6067162156105042; doc=22, chunk=217, year=2026, dept=-, score=0.606399416923523; doc=22, chunk=218, year=2026, dept=-, score=0.604067325592041; doc=10, chunk=74, year=2026, dept=-, score=0.5681232810020447; doc=30, chunk=326, year=2026, dept=-, score=0.5678458213806152
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-070 REFUSAL_FALSE_NEGATIVE

- Question：电子转专业面试一般有哪些环节和提问范围？
- Expected：1分钟自我介绍；3到4分钟提问；可能问大学物理、电路分析、模拟电路等
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.6921214461326599; doc=22, chunk=219, year=2026, dept=-, score=0.6457149386405945; doc=10, chunk=76, year=2026, dept=-, score=0.6440854072570801; doc=10, chunk=79, year=2026, dept=-, score=0.6431617140769958; doc=22, chunk=217, year=2026, dept=-, score=0.6218868494033813
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-073 REFUSAL_FALSE_NEGATIVE

- Question：2026年电子学院转专业面试时间和流程大致怎样？
- Expected：5月底面试；1分钟个人陈述；老师轮流提问；问题可涉及大物、电分、模电、C语言
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.7060462832450867; doc=7, chunk=66, year=2026, dept=-, score=0.6606777310371399; doc=10, chunk=79, year=2026, dept=-, score=0.6569396257400513; doc=22, chunk=219, year=2026, dept=-, score=0.6518709659576416; doc=22, chunk=217, year=2026, dept=-, score=0.6150370836257935
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-076 REFUSAL_FALSE_NEGATIVE

- Question：电子专业导学课有什么修读要求，选某一门是否限制后续分流方向？
- Expected：5门中至少修1门；对外开放4门；鼓楼开设1学分；选择某门不限制分流方向
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=19, chunk=169, year=2026, dept=-, score=0.7091469764709473; doc=6, chunk=136, year=2026, dept=-, score=0.6640005707740784; doc=5, chunk=39, year=2026, dept=-, score=0.6470369696617126; doc=19, chunk=166, year=2026, dept=-, score=0.6439273357391357; doc=30, chunk=327, year=2026, dept=-, score=0.6422401070594788
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

### TRAG-077 REFUSAL_FALSE_NEGATIVE

- Question：2026年转电子需要修哪些准入课，面试主要问什么？
- Expected：六门准入课均70分以上；面试1分钟陈述加提问；可能问大物、电分、模电、C语言
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.6388236880302429; doc=10, chunk=76, year=2026, dept=-, score=0.6235905885696411; doc=6, chunk=136, year=2026, dept=-, score=0.6179773807525635; doc=8, chunk=68, year=2026, dept=-, score=0.6146582365036011; doc=17, chunk=155, year=2026, dept=-, score=0.6146582365036011
- Failure Reason：诊断 TopK 主要来自其他学院或其他专业资料，目标学院证据未稳定进入前列，显示简称/实体识别或 department 过滤未生效。
- Suggested Fix：后续核查 EntityAlias 命中、department/chunkDepartment 元数据与全局官方表切块；本轮不添加别名。

## Year Strategy

案例数：4

### TRAG-028 YEAR_MISMATCH

- Question：2026年社会学院大一跨类准入需要哪些课程条件？
- Expected：已取得社会与心理科学导论学分；社会学概论、社会工作概论、心理学概论上中在修任意1门
- Actual：根据南京大学本科生院2026年发布的跨大类专业准入计划，社会学院大一跨类准入需满足以下课程条件： 1. 申请时须已取得社会学院开设的《社会与心理科学导论》课程学分 [S1]； 2. 须已修或在修《社会学概论》《社会工作概论》《心理学概论（上）》3门课程，本学期结束时取得相应学分 [S1]； 3. 须至少已修或在修拟准入专业教学计划中的2门专业核心课，本学期结束时取得相应学分 [S1]。 以上条件均来自官方资料，请以学校或学院最新通知为准。
- Sources：S1 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表 (doc=6, year=2026, dept=本科生院, score=0.6509084701538086)
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6509084701538086; doc=20, chunk=172, year=2026, dept=-, score=0.6491811275482178; doc=6, chunk=123, year=2026, dept=-, score=0.6358058452606201; doc=6, chunk=134, year=2026, dept=-, score=0.6146427392959595; doc=6, chunk=126, year=2026, dept=-, score=0.6143920421600342
- Failure Reason：问题询问2026年社会学院大一准入，回答取用了2024级旧要求（3门概论课全部修读并另修2门核心课），而2025级适用行仅要求三门概论课中在修任意1门。
- Suggested Fix：表格切块保留年级与学院行绑定，并对显式年份/当前准入批次精确过滤。

### TRAG-031 REFUSAL_FALSE_NEGATIVE

- Question：2023级人文大类110人的分流和转专业人数如何分布？
- Expected：新传57人；历史22人；汉语国际教育11人；哲学5人；转专业15人
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：无
- Failure Reason：问题含明确旧年份，诊断检索返回0条；资料正文含该年份事实，但导入后的 effectiveYear 统一为2026，显式年份过滤使对应 Chunk 完全不可见。
- Suggested Fix：后续核对文档级年份与正文事件年份的建模及索引状态；本轮不改 metadata 或索引。

### TRAG-041 REFUSAL_FALSE_NEGATIVE

- Question：地学大类2023级分流去向人数是多少？
- Expected：大气17人；环境29人；地科13人；地海24人；转出39人
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：无
- Failure Reason：问题含明确旧年份，诊断检索返回0条；资料正文含该年份事实，但导入后的 effectiveYear 统一为2026，显式年份过滤使对应 Chunk 完全不可见。
- Suggested Fix：后续核对文档级年份与正文事件年份的建模及索引状态；本轮不改 metadata 或索引。

### TRAG-072 YEAR_MISMATCH

- Question：2026年大一转电子的六门准入课和最低成绩要求是什么？
- Expected：微积分I和II第一层次；大学物理I和II；电路分析；模拟电路；总评均不低于70
- Actual：根据官方资料，2026年大一转入电子科学与工程学院（电子专业）的准入课程与最低成绩要求如下： - **必须修读三门数学课程**：00010011A《微积分I(第一层次)》、00010011B《微积分II(第一层次)》、00010011C《线性代数（第一层次）》，每门课总评均须不低于70分 [S3]。 - **学科基础课程**：须取得至少15个学分，且每门课总评均不低于70分。学科基础课程包括：18000110A《大学物理Ⅰ》、18000110B《大学物理Ⅱ》、18000310《电路分析》、18001560T《电子学基础I实验》、18000610《模拟电路》、18000510《信号与系统》、18001570T《电子学基础II实验》 [S3]。 注意：学科基础课程共列出7门，并非固定6门，但要求修满至少15学分且每门不低于70分。此外，综合表现优秀者可降低准入课程要求 [S3]。
- Sources：S3 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表 (doc=6, year=2026, dept=本科生院, score=0.6128564476966858)
- Diagnostic TopK：doc=17, chunk=155, year=2026, dept=-, score=0.6278324127197266; doc=8, chunk=68, year=2026, dept=-, score=0.6278324127197266; doc=6, chunk=136, year=2026, dept=-, score=0.6128564476966858; doc=30, chunk=326, year=2026, dept=-, score=0.6111667156219482; doc=18, chunk=156, year=2026, dept=-, score=0.5978610515594482
- Failure Reason：问题询问2026年大一转电子的六门准入课，回答却使用了大二准入条件（含线性代数和至少15学分学科基础课）。
- Suggested Fix：在表格切块中显式绑定年级/申请阶段，检索和回答时校验‘大一/大二’。

## Answerability

案例数：16

### TRAG-003 REFUSAL_FALSE_NEGATIVE

- Question：2026级智能科学与技术大一上建议修哪些数学和编程课程？
- Expected：微积分I第一层次；线性代数第一层次；智能程序设计C语言
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=18, chunk=156, year=2026, dept=-, score=0.7331855297088623; doc=18, chunk=162, year=2026, dept=-, score=0.689579427242279; doc=18, chunk=160, year=2026, dept=-, score=0.6813269257545471; doc=18, chunk=161, year=2026, dept=-, score=0.6736702919006348; doc=18, chunk=163, year=2026, dept=-, score=0.6642405986785889
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-005 REFUSAL_FALSE_NEGATIVE

- Question：2026级智能科学与技术专业的毕业设计安排在何时、多少学分？
- Expected：四年级下学期；6学分；144学时
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=18, chunk=156, year=2026, dept=-, score=0.765220582485199; doc=18, chunk=162, year=2026, dept=-, score=0.6485235691070557; doc=18, chunk=163, year=2026, dept=-, score=0.6439810991287231; doc=18, chunk=160, year=2026, dept=-, score=0.6275012493133545; doc=18, chunk=159, year=2026, dept=-, score=0.6216619610786438
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-007 REFUSAL_FALSE_NEGATIVE

- Question：2026年分流或转入汉语言文学需要跨选哪些大一课程？
- Expected：古代文学；古代汉语
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.7559970617294312; doc=20, chunk=175, year=2026, dept=-, score=0.6488946676254272; doc=20, chunk=178, year=2026, dept=-, score=0.6448134779930115; doc=20, chunk=173, year=2026, dept=-, score=0.6418572664260864; doc=20, chunk=177, year=2026, dept=-, score=0.5652530789375305
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-009 REFUSAL_FALSE_NEGATIVE

- Question：准备汉语言文学转专业面试时，对报名材料应做什么准备？
- Expected：重新熟悉提交材料；可能围绕材料针对性追问；表达应简明务实
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=174, year=2026, dept=-, score=0.6765984296798706; doc=20, chunk=175, year=2026, dept=-, score=0.664575457572937; doc=20, chunk=173, year=2026, dept=-, score=0.6591543555259705; doc=16, chunk=104, year=2025, dept=-, score=0.6456522941589355; doc=20, chunk=172, year=2026, dept=-, score=0.6321349143981934
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-020 REFUSAL_FALSE_NEGATIVE

- Question：2026年物理学类跨大类准入需要修哪些数学和物理课程？
- Expected：微积分I第一层次；微积分II第一层次；线性代数第一层次；力学；热学
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=126, year=2026, dept=-, score=0.7020105123519897; doc=6, chunk=128, year=2026, dept=-, score=0.6932871341705322; doc=6, chunk=129, year=2026, dept=-, score=0.6655862927436829; doc=6, chunk=134, year=2026, dept=-, score=0.6597716808319092; doc=6, chunk=135, year=2026, dept=-, score=0.6437729001045227
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-023 REFUSAL_FALSE_NEGATIVE

- Question：2026年软件工程转专业至少要修软件学院哪几门基础课中的几门？
- Expected：计算系统基础；C语言程序设计基础；软件工程与计算I；离散数学；四门中至少2门并取得学分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.7236602902412415; doc=17, chunk=155, year=2026, dept=-, score=0.7236602902412415; doc=11, chunk=82, year=2026, dept=-, score=0.693400502204895; doc=5, chunk=37, year=2026, dept=-, score=0.6857840418815613; doc=5, chunk=38, year=2026, dept=-, score=0.6799354553222656
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-024 REFUSAL_FALSE_NEGATIVE

- Question：软工2026年转专业复试包括什么，什么情况不予录取？
- Expected：复试包括机试和面试；机试或面试任一不及格不录取
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=7, chunk=66, year=2026, dept=-, score=0.6604678630828857; doc=11, chunk=85, year=2026, dept=-, score=0.6530429124832153; doc=17, chunk=155, year=2026, dept=-, score=0.6528915166854858; doc=8, chunk=68, year=2026, dept=-, score=0.6528915166854858; doc=10, chunk=76, year=2026, dept=-, score=0.6426039934158325
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-026 REFUSAL_FALSE_NEGATIVE

- Question：2026年计算机学院转专业综合考核有哪些环节？
- Expected：笔试；机试；面试；任一项不及格不予录取
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=7, chunk=66, year=2026, dept=-, score=0.6793063879013062; doc=10, chunk=79, year=2026, dept=-, score=0.6724403500556946; doc=8, chunk=68, year=2026, dept=-, score=0.6524646282196045; doc=17, chunk=155, year=2026, dept=-, score=0.6524646282196045; doc=10, chunk=76, year=2026, dept=-, score=0.6514286994934082
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-029 REFUSAL_FALSE_NEGATIVE

- Question：2026年信息管理与信息系统专业跨类准入可选四门课程中的哪一门？
- Expected：信息资源管理导论；信息组织；程序设计语言；数据思维；任意1门
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6150941252708435; doc=30, chunk=326, year=2026, dept=-, score=0.6001560091972351; doc=18, chunk=156, year=2026, dept=-, score=0.5880192518234253; doc=17, chunk=155, year=2026, dept=-, score=0.5816323161125183; doc=8, chunk=68, year=2026, dept=-, score=0.5816323161125183
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-036 REFUSAL_FALSE_NEGATIVE

- Question：2025级技科分流加权排名主要参考哪些课程？
- Expected：微积分；线性代数；信息科学中的物理学
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=125, year=2025, dept=数学学院, score=0.6176490783691406; doc=16, chunk=104, year=2025, dept=-, score=0.5686601996421814; doc=6, chunk=132, year=2025, dept=大气科学学院, score=0.5604015588760376; doc=6, chunk=139, year=2025, dept=计算机学院, score=0.5602705478668213; doc=6, chunk=130, year=2025, dept=-, score=0.558928370475769
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-037 REFUSAL_FALSE_NEGATIVE

- Question：技科自动化机器人方向大一下有哪些准入课？
- Expected：数据结构与算法设计；机器人与自动化导论
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=153, year=2026, dept=-, score=0.6838679313659668; doc=19, chunk=168, year=2026, dept=-, score=0.6202205419540405; doc=19, chunk=166, year=2026, dept=-, score=0.6158523559570312; doc=6, chunk=146, year=2026, dept=-, score=0.6020611524581909; doc=6, chunk=152, year=2026, dept=-, score=0.5989012718200684
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-038 REFUSAL_FALSE_NEGATIVE

- Question：技科集成电路设计与集成系统方向大一下有哪些准入课？
- Expected：信息科学中的物理学下；电路分析
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=19, chunk=168, year=2026, dept=-, score=0.6519353985786438; doc=19, chunk=169, year=2026, dept=-, score=0.6411211490631104; doc=6, chunk=153, year=2026, dept=-, score=0.6387734413146973; doc=6, chunk=152, year=2026, dept=-, score=0.6231960654258728; doc=5, chunk=41, year=2026, dept=-, score=0.6217055320739746
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-046 REFUSAL_FALSE_NEGATIVE

- Question：2026级工试想分流现工院，需要修读哪些课中的至少一门？
- Expected：普通物理力学；普通物理热学；大学化学；三门中一门
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=30, chunk=326, year=2026, dept=-, score=0.6817584037780762; doc=30, chunk=327, year=2026, dept=-, score=0.6766449213027954; doc=19, chunk=168, year=2026, dept=-, score=0.6160160303115845; doc=6, chunk=135, year=2026, dept=-, score=0.6145524382591248; doc=5, chunk=36, year=2026, dept=-, score=0.6058609485626221
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-048 REFUSAL_FALSE_NEGATIVE

- Question：2025级数理大类包含哪些学院方向？
- Expected：数学学院；物理学院；大气科学学院；天文与空间科学学院
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=125, year=2025, dept=数学学院, score=0.6253305673599243; doc=21, chunk=184, year=2025, dept=-, score=0.6020071506500244; doc=21, chunk=187, year=2025, dept=-, score=0.5979468822479248; doc=21, chunk=183, year=2025, dept=-, score=0.5971869230270386; doc=21, chunk=185, year=2025, dept=-, score=0.5806998014450073
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-068 REFUSAL_FALSE_NEGATIVE

- Question：转电子大一准入需要哪六门课程达到多少分？
- Expected：微积分I一层次；大学物理I；电路分析；微积分II一层次；模拟电路；大学物理II；均不低于70分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=136, year=2026, dept=-, score=0.6592690348625183; doc=17, chunk=155, year=2026, dept=-, score=0.6493565440177917; doc=8, chunk=68, year=2026, dept=-, score=0.6493565440177917; doc=6, chunk=154, year=2026, dept=-, score=0.6447964906692505; doc=6, chunk=146, year=2026, dept=-, score=0.6403669118881226
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

### TRAG-080 REFUSAL_FALSE_NEGATIVE

- Question：南赫学院大一选课的总学分和考试课程数量建议是多少？
- Expected：总学分以25以内为宜；最多30学分；考试课程10门以内
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=24, chunk=237, year=2026, dept=-, score=0.7222495079040527; doc=24, chunk=258, year=2026, dept=-, score=0.7194973230361938; doc=24, chunk=259, year=2026, dept=-, score=0.717545211315155; doc=24, chunk=262, year=2026, dept=-, score=0.7004944086074829; doc=24, chunk=241, year=2026, dept=-, score=0.681888997554779
- Failure Reason：诊断检索 TopK 已包含可直接回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：复核 Answerability 对列表、表格和多事实问题的充分性判断；本轮只记录不修改。

## Generation

案例数：5

### TRAG-014 PARTIAL

- Question：化生大类学生大一需要完成哪些劳育和志愿时长？
- Expected：基础劳育10小时；每学期志愿10小时；完成大学生劳育考试
- Actual：根据当前知识库中的经验资料，化生大类学生大一期间需完成以下要求： - **劳育时长**：完成十小时基础劳育时长。[S1] - **志愿时长**：每学期需完成十小时志愿时长。[S1] ⚠️ 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 化生大类生存指南 (doc=23, year=2026, dept=*理科试验班（化学与生命科学类）, score=0.5964410901069641)
- Diagnostic TopK：无
- Failure Reason：回答覆盖基础劳育10小时和每学期志愿10小时，但遗漏了还需完成大学生劳育考试。
- Suggested Fix：生成阶段按问题的全部 expectedFacts 逐项核对后再输出。

### TRAG-032 PARTIAL

- Question：人文大类大一课程负担和数学英语安排有什么特点？
- Expected：多为讲座或论文结课；分流只需五门大类课；大一上有微积分、大一下不再开数学；大一全年有英语
- Actual：根据当前知识库中的经验资料，人文大类大一的课程负担非常轻松：大部分课程以论文结课，平时无随堂作业，许多课程不点名，学生有大量自我支配时间 [S3]。数学和英语安排上，大一上学期设有微积分课程，大一下学期后不再开设数学课；大一一整年开设英语课程，分流后只有历史学对专业外语有硬性要求 [S3]。该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S3 南京大学人文科学试验班求生指南 (doc=28, year=2026, dept=*人文科学试验班, score=0.6697366237640381)
- Diagnostic TopK：无
- Failure Reason：回答覆盖课程负担、数学和英语安排，但遗漏了‘分流只需修五门大类课程’这一关键事实。
- Suggested Fix：生成答案前按 expectedFacts 做完整性检查。

### TRAG-043 PARTIAL

- Question：地学大类学生转专业选课时，指南对数学层次有什么建议？
- Expected：修读目标专业要求的数学层次；若目标线代大一修读建议上学期提前修
- Actual：根据当前知识库中的经验资料，地学大类不同院系对数学层次要求不同：有的修读一层次数学，有的修读二层次数学。建议转专业时，务必修读**目标专业所要求的对应数学层次**[S1][S2]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 地球科学与资源环境类生存指南 (doc=29, year=2026, dept=*理科试验班（地球科学与资源环境类）, score=0.7368510365486145); S2 地球科学与资源环境类生存指南 (doc=29, year=2026, dept=*理科试验班（地球科学与资源环境类）, score=0.6783273816108704)
- Diagnostic TopK：无
- Failure Reason：回答说明应修目标专业要求的数学层次，但遗漏‘若目标专业线代在大一修读，建议上学期提前修’的课程规划建议。
- Suggested Fix：对包含两项建议的问题逐项覆盖。

### TRAG-045 PARTIAL

- Question：工科试验班为什么被指南认为利于转专业？
- Expected：自带一层次微积分；除通修课外准入课均可选；可按目标院系安排课程；转失败补课相对少
- Actual：根据当前知识库中的经验资料，工科试验班被认为利于转专业主要有以下原因： - 课程设置灵活：工科试验班没有除通修课外的自选课程，如果一心想转专业，可以一门准入课都不选，从而将精力集中在目标专业的准备上 [S1]。 - 无专业必修课限制：工试的四个分流院系所需准入课大概率没有重合，上下两学期的所有准入课均为“可选”，因此学生可以 **“all in” 转专业**，直接将自己当作目标院系的学生 [S3]。 - 转专业失败风险较低：即使转专业失败，后果并不严重，不会因为失败而被发配到特别差的专业；而且由于大一课业较少，失败后进入对应院系也无需补很多课程 [S3]。 - 教务支持：部分教务员可能愿意支持学生大二继续尝试转专业 [S3]。 **注意**：该信息并非官方政策，具体转专业要求、课程安排及教务政策请以学校或学院最新通知为准。
- Sources：S1 工科试验班生存指南 (doc=30, year=2026, dept=*工科试验班, score=0.5993660092353821); S3 工科试验班生存指南 (doc=30, year=2026, dept=*工科试验班, score=0.5899030566215515)
- Diagnostic TopK：无
- Failure Reason：回答列出课程灵活、可 all-in、失败后补课少等原因，但遗漏工试自带一层次微积分这一关键条件。
- Suggested Fix：生成时完整覆盖并列依据。

### TRAG-056 WRONG

- Question：法学转专业备考为什么应重点准备民法总则和刑法总论？
- Expected：多数人笔试选择民法和刑法；大一下是重要阶段；需结合教材笔记和往年题全面备考
- Actual：根据当前知识库中的经验资料，法学转专业备考重点准备民法总则和刑法总论，主要有以下原因： - **笔试分值高且选择集中**：笔试中，民法学总则（50分）和刑法学（50分）是必选科目，而法理学（50分）因“摇摆空间过大，几乎所有人都会选择民刑两个板块”[S1]。因此，大一下学期的民总和刑总备考是“最重要的阶段”[S1]。 - **面试可自选科目**：面试的专业知识部分允许考生从民法学总则、刑法学、法理学中三选一，由对应老师提问，内容相对基础，也可能超纲[S1]。 - **笔试成绩为关键参考**：最终录取“以笔试成绩为主要参考”[S1]，而民总和刑总占据了笔试总分的三分之二，投入精力备考能直接提升录取机会。 请注意，该信息并非官方政策，具体考核安排请以学校或学院最新通知为准。
- Sources：S1 法学转专业分享 (doc=31, year=2026, dept=法学院, score=0.7200465202331543); S3 法学转专业分享 (doc=31, year=2026, dept=法学院, score=0.6515581607818604)
- Diagnostic TopK：无
- Failure Reason：回答错误称民法与刑法是‘必选科目’且占笔试总分三分之二；原资料是民法、刑法、法理各50分，三选二，多数人经验上选择民刑。
- Suggested Fix：区分‘多数人选择’与‘制度上必选’，避免把经验偏好改写为硬性规则。

## Index/Data Quality

案例数：0

## System Error

案例数：0
