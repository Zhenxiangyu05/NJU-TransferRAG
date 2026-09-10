# TransferRAG Failures — Iteration 1 Answerability

共 45 个非 PASS 案例。完整响应和 TopK 诊断保存在 `evaluation-results.json`。

## Retrieval

案例数：12

### TRAG-015 REFUSAL_FALSE_NEGATIVE

- Question：2026年文学院汉语言文学转专业需要修哪些准入课程、至少几门？
- Expected：古代汉语上；古代汉语下；中国古代文学一；中国古代文学二；至少3门共9学分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.7835471630096436; doc=20, chunk=173, year=2026, dept=-, score=0.7236139178276062; doc=20, chunk=175, year=2026, dept=-, score=0.6751245856285095; doc=20, chunk=178, year=2026, dept=-, score=0.6272296905517578; doc=20, chunk=174, year=2026, dept=-, score=0.6011740565299988
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-016 REFUSAL_FALSE_NEGATIVE

- Question：2026年汉语言文学跨大类准入对第一学期平均学分绩和不及格记录有什么要求？
- Expected：平均学分绩4.0及以上；无不及格记录；无违纪行为
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.6810758709907532; doc=20, chunk=173, year=2026, dept=-, score=0.6560064554214478; doc=20, chunk=175, year=2026, dept=-, score=0.6377837657928467; doc=20, chunk=174, year=2026, dept=-, score=0.5512412786483765; doc=20, chunk=178, year=2026, dept=-, score=0.5382677912712097
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-018 REFUSAL_FALSE_NEGATIVE

- Question：2026年数学学院数学类转专业可用哪两套数学课程方案满足准入？
- Expected：数分I、II+高代I、II+解析几何；或微积分I、II第一层次+线性代数第一层次；两套满足一套即可
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=17, chunk=155, year=2026, dept=-, score=0.6731560230255127; doc=8, chunk=68, year=2026, dept=-, score=0.6731560230255127; doc=6, chunk=128, year=2026, dept=-, score=0.6612573862075806; doc=25, chunk=306, year=2026, dept=-, score=0.6480956077575684; doc=25, chunk=307, year=2026, dept=-, score=0.6436569094657898
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-019 REFUSAL_FALSE_NEGATIVE

- Question：2026年数学学院准入考核如何使用校内数学竞赛成绩？
- Expected：校内数学竞赛成绩可计附加分；数学专业类或非数学专业A类按课程方案对应
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=128, year=2026, dept=-, score=0.6108091473579407; doc=6, chunk=136, year=2026, dept=-, score=0.5995528697967529; doc=6, chunk=126, year=2026, dept=-, score=0.5877895951271057; doc=25, chunk=306, year=2026, dept=-, score=0.5876396298408508; doc=6, chunk=147, year=2026, dept=-, score=0.5872007012367249
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-034 REFUSAL_FALSE_NEGATIVE

- Question：2025级起技术科学试验班主要有哪四个分流方向？
- Expected：智能科学与技术；自动化机器人方向；集成电路设计与集成系统；数字经济
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=16, chunk=104, year=2025, dept=-, score=0.5408475995063782; doc=6, chunk=139, year=2025, dept=计算机学院, score=0.5272615551948547; doc=6, chunk=133, year=2025, dept=-, score=0.5170126557350159; doc=16, chunk=106, year=2025, dept=-, score=0.5138590335845947; doc=6, chunk=144, year=2025, dept=-, score=0.5136555433273315
- Failure Reason：诊断检索最高分为 0.541，正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：后续单独检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-042 REFUSAL_FALSE_NEGATIVE

- Question：地海拔尖对数学层次有什么要求？
- Expected：需要一层次数学；微积分I一层次；微积分II一层次；线性代数
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=25, chunk=307, year=2026, dept=-, score=0.5169183611869812; doc=29, chunk=324, year=2026, dept=-, score=0.5134637355804443; doc=6, chunk=136, year=2026, dept=-, score=0.5116908550262451; doc=25, chunk=306, year=2026, dept=-, score=0.5052566528320312; doc=9, chunk=73, year=2026, dept=-, score=0.4988344609737396
- Failure Reason：诊断检索最高分为 0.517，正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：后续单独检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-049 REFUSAL_FALSE_NEGATIVE

- Question：2024级数理大类计划分流比例和人数如何分配？
- Expected：数学28%/97人；物理46%/159人；大气14%/48人；天文12%/41人
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=140, year=2024, dept=计算机学院, score=0.5363843441009521; doc=6, chunk=143, year=2024, dept=软件学院, score=0.513130784034729; doc=6, chunk=138, year=2024, dept=-, score=0.48675960302352905; doc=6, chunk=118, year=2024, dept=法学院, score=0.4417182505130768; doc=6, chunk=120, year=2024, dept=哲学学院, score=0.43990767002105713
- Failure Reason：诊断检索最高分为 0.536，正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：后续单独检查向量召回、查询改写与切块表达；本轮不调整阈值。

### TRAG-060 REFUSAL_FALSE_NEGATIVE

- Question：转计算机前，资料建议如何平衡微积分II和笔试机试准备？
- Expected：微积分II需要大量投入；同时不能忽视离散、高程、计算系统等；平衡数学刷题与笔试机试内容
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=26, chunk=309, year=2026, dept=-, score=0.6658834218978882; doc=11, chunk=83, year=2026, dept=-, score=0.6473839282989502; doc=24, chunk=243, year=2026, dept=-, score=0.6456722021102905; doc=9, chunk=73, year=2026, dept=-, score=0.6380473375320435; doc=9, chunk=69, year=2026, dept=-, score=0.6341837644577026
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-062 REFUSAL_FALSE_NEGATIVE

- Question：转CS机试失败案例中三道题大致是什么类型、得分如何？
- Expected：第一题大模拟60分；第二题状压DP约10分；第三题困难图论约10分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=9, chunk=69, year=2026, dept=-, score=0.5762456655502319; doc=26, chunk=308, year=2026, dept=-, score=0.5708751678466797; doc=9, chunk=73, year=2026, dept=-, score=0.5695652365684509; doc=26, chunk=310, year=2026, dept=-, score=0.56740802526474; doc=26, chunk=309, year=2026, dept=-, score=0.564179539680481
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-066 REFUSAL_FALSE_NEGATIVE

- Question：转光电面试为什么要重视数学和物理成绩？
- Expected：学院重视数学物理能力；面试官会看第一学期成绩；可能追问第二学期期中成绩
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=30, chunk=327, year=2026, dept=-, score=0.6313884258270264; doc=30, chunk=326, year=2026, dept=-, score=0.5966572165489197; doc=6, chunk=127, year=2026, dept=-, score=0.588994026184082; doc=22, chunk=218, year=2026, dept=-, score=0.5857593417167664; doc=25, chunk=307, year=2026, dept=-, score=0.5843404531478882
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-074 REFUSAL_FALSE_NEGATIVE

- Question：软院之外，电子学院的电路分析和模拟电路应如何学习？
- Expected：知识量大且琐碎；依赖课后自学；重理解记忆知识点；可参考课程视频和做题
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=11, chunk=83, year=2026, dept=-, score=0.625362753868103; doc=24, chunk=256, year=2026, dept=-, score=0.6211185455322266; doc=19, chunk=169, year=2026, dept=-, score=0.6053516864776611; doc=30, chunk=327, year=2026, dept=-, score=0.6008939743041992; doc=10, chunk=79, year=2026, dept=-, score=0.595953643321991
- Failure Reason：诊断 TopK 未包含足以覆盖 expectedFacts 的正确 Chunk，或只召回不含答案的相邻 Chunk，导致最终拒答。
- Suggested Fix：后续单独检查切块边界、向量相关度与来源优先级；本轮不改检索。

### TRAG-075 REFUSAL_FALSE_NEGATIVE

- Question：电子学院2026版C语言课程发生了什么变化？
- Expected：课程号改为18001790；从通修改为专业平台课；不对外开放；跨专业选修可能受限
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=11, chunk=81, year=2026, dept=-, score=0.5319957733154297; doc=18, chunk=160, year=2026, dept=-, score=0.5267254114151001; doc=18, chunk=163, year=2026, dept=-, score=0.5057293772697449; doc=18, chunk=156, year=2026, dept=-, score=0.5045052170753479; doc=30, chunk=326, year=2026, dept=-, score=0.5041753649711609
- Failure Reason：诊断检索最高分为 0.532，正确或近似证据未达到系统可用阈值，最终拒答。
- Suggested Fix：后续单独检查向量召回、查询改写与切块表达；本轮不调整阈值。

## Metadata

案例数：7

### TRAG-025 REFUSAL_FALSE_NEGATIVE

- Question：2026年计算机科学与技术转专业需要修哪两门准入课，成绩要求多少？
- Expected：离散数学；程序设计基础或计算机程序的构造和解释；成绩80分及以上
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.694525957107544; doc=17, chunk=155, year=2026, dept=-, score=0.694525957107544; doc=18, chunk=156, year=2026, dept=-, score=0.6735349893569946; doc=6, chunk=128, year=2026, dept=-, score=0.6448765397071838; doc=6, chunk=153, year=2026, dept=-, score=0.6333624720573425
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-064 REFUSAL_FALSE_NEGATIVE

- Question：2026级跨专业准入光电信息类，准入课程最低要求是什么？
- Expected：普通物理力学、大学化学、普通物理热学中任意一门；已修或在修并在学期结束取得学分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6362829804420471; doc=30, chunk=326, year=2026, dept=-, score=0.6323796510696411; doc=6, chunk=136, year=2026, dept=-, score=0.6279793977737427; doc=6, chunk=128, year=2026, dept=-, score=0.6242436766624451; doc=6, chunk=129, year=2026, dept=-, score=0.6198489665985107
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-067 REFUSAL_FALSE_NEGATIVE

- Question：2026级光材二次拔尖的面试流程和题型有哪些？
- Expected：自我介绍；高中印象深刻的事；多个问题选答且准备1分钟；涉及光学隐身、信息材料、海市蜃楼、冷热杯破裂等
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=22, chunk=219, year=2026, dept=-, score=0.6067162156105042; doc=22, chunk=217, year=2026, dept=-, score=0.606399416923523; doc=22, chunk=218, year=2026, dept=-, score=0.604067325592041; doc=10, chunk=74, year=2026, dept=-, score=0.5681232810020447; doc=30, chunk=326, year=2026, dept=-, score=0.5678458213806152
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-070 REFUSAL_FALSE_NEGATIVE

- Question：电子转专业面试一般有哪些环节和提问范围？
- Expected：1分钟自我介绍；3到4分钟提问；可能问大学物理、电路分析、模拟电路等
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.6921214461326599; doc=22, chunk=219, year=2026, dept=-, score=0.6457149386405945; doc=10, chunk=76, year=2026, dept=-, score=0.6440854072570801; doc=10, chunk=79, year=2026, dept=-, score=0.6431617140769958; doc=22, chunk=217, year=2026, dept=-, score=0.6218868494033813
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-073 REFUSAL_FALSE_NEGATIVE

- Question：2026年电子学院转专业面试时间和流程大致怎样？
- Expected：5月底面试；1分钟个人陈述；老师轮流提问；问题可涉及大物、电分、模电、C语言
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.7060462832450867; doc=7, chunk=66, year=2026, dept=-, score=0.6606777310371399; doc=10, chunk=79, year=2026, dept=-, score=0.6569396257400513; doc=22, chunk=219, year=2026, dept=-, score=0.6518709659576416; doc=22, chunk=217, year=2026, dept=-, score=0.6150370836257935
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-076 REFUSAL_FALSE_NEGATIVE

- Question：电子专业导学课有什么修读要求，选某一门是否限制后续分流方向？
- Expected：5门中至少修1门；对外开放4门；鼓楼开设1学分；选择某门不限制分流方向
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=19, chunk=169, year=2026, dept=-, score=0.7091469764709473; doc=6, chunk=136, year=2026, dept=-, score=0.6640005707740784; doc=5, chunk=39, year=2026, dept=-, score=0.6470369696617126; doc=19, chunk=166, year=2026, dept=-, score=0.6439273357391357; doc=30, chunk=327, year=2026, dept=-, score=0.6422401070594788
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

### TRAG-077 REFUSAL_FALSE_NEGATIVE

- Question：2026年转电子需要修哪些准入课，面试主要问什么？
- Expected：六门准入课均70分以上；面试1分钟陈述加提问；可能问大物、电分、模电、C语言
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=10, chunk=74, year=2026, dept=-, score=0.6388236880302429; doc=10, chunk=76, year=2026, dept=-, score=0.6235905885696411; doc=6, chunk=136, year=2026, dept=-, score=0.6179773807525635; doc=8, chunk=68, year=2026, dept=-, score=0.6146582365036011; doc=17, chunk=155, year=2026, dept=-, score=0.6146582365036011
- Failure Reason：诊断 TopK 主要来自其他学院或专业，目标证据未稳定进入前列，显示简称识别或 department 元数据过滤未生效。
- Suggested Fix：后续单独核查 EntityAlias 与 department/chunkDepartment；本轮不添加别名或修改元数据。

## Year Strategy

案例数：4

### TRAG-028 REFUSAL_FALSE_NEGATIVE

- Question：2026年社会学院大一跨类准入需要哪些课程条件？
- Expected：已取得社会与心理科学导论学分；社会学概论、社会工作概论、心理学概论上中在修任意1门
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6509084701538086; doc=20, chunk=172, year=2026, dept=-, score=0.6491811275482178; doc=6, chunk=123, year=2026, dept=-, score=0.6358058452606201; doc=6, chunk=134, year=2026, dept=-, score=0.6146427392959595; doc=6, chunk=126, year=2026, dept=-, score=0.6143920421600342
- Failure Reason：问题的年份或年级条件与召回证据未可靠对齐，正确年份证据不可见或被其他年份/年级内容替代，最终拒答。
- Suggested Fix：后续单独核查正文年份、effectiveYear 与年级行绑定；本轮不改 metadata 或索引。

### TRAG-031 REFUSAL_FALSE_NEGATIVE

- Question：2023级人文大类110人的分流和转专业人数如何分布？
- Expected：新传57人；历史22人；汉语国际教育11人；哲学5人；转专业15人
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：无
- Failure Reason：问题的年份或年级条件与召回证据未可靠对齐，正确年份证据不可见或被其他年份/年级内容替代，最终拒答。
- Suggested Fix：后续单独核查正文年份、effectiveYear 与年级行绑定；本轮不改 metadata 或索引。

### TRAG-041 REFUSAL_FALSE_NEGATIVE

- Question：地学大类2023级分流去向人数是多少？
- Expected：大气17人；环境29人；地科13人；地海24人；转出39人
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：无
- Failure Reason：问题的年份或年级条件与召回证据未可靠对齐，正确年份证据不可见或被其他年份/年级内容替代，最终拒答。
- Suggested Fix：后续单独核查正文年份、effectiveYear 与年级行绑定；本轮不改 metadata 或索引。

### TRAG-072 REFUSAL_FALSE_NEGATIVE

- Question：2026年大一转电子的六门准入课和最低成绩要求是什么？
- Expected：微积分I和II第一层次；大学物理I和II；电路分析；模拟电路；总评均不低于70
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=17, chunk=155, year=2026, dept=-, score=0.6278324127197266; doc=8, chunk=68, year=2026, dept=-, score=0.6278324127197266; doc=6, chunk=136, year=2026, dept=-, score=0.6128564476966858; doc=30, chunk=326, year=2026, dept=-, score=0.6111667156219482; doc=18, chunk=156, year=2026, dept=-, score=0.5978610515594482
- Failure Reason：问题的年份或年级条件与召回证据未可靠对齐，正确年份证据不可见或被其他年份/年级内容替代，最终拒答。
- Suggested Fix：后续单独核查正文年份、effectiveYear 与年级行绑定；本轮不改 metadata 或索引。

## Answerability

案例数：12

### TRAG-003 REFUSAL_FALSE_NEGATIVE

- Question：2026级智能科学与技术大一上建议修哪些数学和编程课程？
- Expected：微积分I第一层次；线性代数第一层次；智能程序设计C语言
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=18, chunk=156, year=2026, dept=-, score=0.7331855297088623; doc=18, chunk=162, year=2026, dept=-, score=0.689579427242279; doc=18, chunk=160, year=2026, dept=-, score=0.6813269257545471; doc=18, chunk=161, year=2026, dept=-, score=0.6736702919006348; doc=18, chunk=163, year=2026, dept=-, score=0.6642405986785889
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-005 REFUSAL_FALSE_NEGATIVE

- Question：2026级智能科学与技术专业的毕业设计安排在何时、多少学分？
- Expected：四年级下学期；6学分；144学时
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=18, chunk=156, year=2026, dept=-, score=0.765220582485199; doc=18, chunk=162, year=2026, dept=-, score=0.6485235691070557; doc=18, chunk=163, year=2026, dept=-, score=0.6439810991287231; doc=18, chunk=160, year=2026, dept=-, score=0.6275012493133545; doc=18, chunk=159, year=2026, dept=-, score=0.6216619610786438
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-007 REFUSAL_FALSE_NEGATIVE

- Question：2026年分流或转入汉语言文学需要跨选哪些大一课程？
- Expected：古代文学；古代汉语
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=172, year=2026, dept=-, score=0.7559970617294312; doc=20, chunk=175, year=2026, dept=-, score=0.6488946676254272; doc=20, chunk=178, year=2026, dept=-, score=0.6448134779930115; doc=20, chunk=173, year=2026, dept=-, score=0.6418572664260864; doc=20, chunk=177, year=2026, dept=-, score=0.5652530789375305
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-008 REFUSAL_FALSE_NEGATIVE

- Question：2026年汉文二次选拔的初试和复试形式是什么？
- Expected：初试闭卷笔试2小时100分；复试面试10分钟；自我陈述2分钟；针对性提问8分钟
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=20, chunk=173, year=2026, dept=-, score=0.6232510805130005; doc=20, chunk=175, year=2026, dept=-, score=0.613408625125885; doc=20, chunk=172, year=2026, dept=-, score=0.5964897871017456; doc=20, chunk=174, year=2026, dept=-, score=0.5693745017051697; doc=22, chunk=218, year=2026, dept=-, score=0.5442665815353394
- Failure Reason：诊断 Top1 已直接包含初试、复试、时长和分值的完整证据，但 V2 最终拒答；这是原 PASS 的 Answerability 回归。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-024 REFUSAL_FALSE_NEGATIVE

- Question：软工2026年转专业复试包括什么，什么情况不予录取？
- Expected：复试包括机试和面试；机试或面试任一不及格不录取
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=7, chunk=66, year=2026, dept=-, score=0.6604678630828857; doc=11, chunk=85, year=2026, dept=-, score=0.6530429124832153; doc=17, chunk=155, year=2026, dept=-, score=0.6528915166854858; doc=8, chunk=68, year=2026, dept=-, score=0.6528915166854858; doc=10, chunk=76, year=2026, dept=-, score=0.6426039934158325
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-026 REFUSAL_FALSE_NEGATIVE

- Question：2026年计算机学院转专业综合考核有哪些环节？
- Expected：笔试；机试；面试；任一项不及格不予录取
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=7, chunk=66, year=2026, dept=-, score=0.6793063879013062; doc=10, chunk=79, year=2026, dept=-, score=0.6724403500556946; doc=8, chunk=68, year=2026, dept=-, score=0.6524646282196045; doc=17, chunk=155, year=2026, dept=-, score=0.6524646282196045; doc=10, chunk=76, year=2026, dept=-, score=0.6514286994934082
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-029 REFUSAL_FALSE_NEGATIVE

- Question：2026年信息管理与信息系统专业跨类准入可选四门课程中的哪一门？
- Expected：信息资源管理导论；信息组织；程序设计语言；数据思维；任意1门
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=122, year=2026, dept=-, score=0.6150941252708435; doc=30, chunk=326, year=2026, dept=-, score=0.6001560091972351; doc=18, chunk=156, year=2026, dept=-, score=0.5880192518234253; doc=17, chunk=155, year=2026, dept=-, score=0.5816323161125183; doc=8, chunk=68, year=2026, dept=-, score=0.5816323161125183
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-036 REFUSAL_FALSE_NEGATIVE

- Question：2025级技科分流加权排名主要参考哪些课程？
- Expected：微积分；线性代数；信息科学中的物理学
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=125, year=2025, dept=数学学院, score=0.6176490783691406; doc=16, chunk=104, year=2025, dept=-, score=0.5686601996421814; doc=6, chunk=132, year=2025, dept=大气科学学院, score=0.5604015588760376; doc=6, chunk=139, year=2025, dept=计算机学院, score=0.5602705478668213; doc=6, chunk=130, year=2025, dept=-, score=0.558928370475769
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-046 REFUSAL_FALSE_NEGATIVE

- Question：2026级工试想分流现工院，需要修读哪些课中的至少一门？
- Expected：普通物理力学；普通物理热学；大学化学；三门中一门
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=30, chunk=326, year=2026, dept=-, score=0.6817584037780762; doc=30, chunk=327, year=2026, dept=-, score=0.6766449213027954; doc=19, chunk=168, year=2026, dept=-, score=0.6160160303115845; doc=6, chunk=135, year=2026, dept=-, score=0.6145524382591248; doc=5, chunk=36, year=2026, dept=-, score=0.6058609485626221
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-048 REFUSAL_FALSE_NEGATIVE

- Question：2025级数理大类包含哪些学院方向？
- Expected：数学学院；物理学院；大气科学学院；天文与空间科学学院
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=125, year=2025, dept=数学学院, score=0.6253305673599243; doc=21, chunk=184, year=2025, dept=-, score=0.6020071506500244; doc=21, chunk=187, year=2025, dept=-, score=0.5979468822479248; doc=21, chunk=183, year=2025, dept=-, score=0.5971869230270386; doc=21, chunk=185, year=2025, dept=-, score=0.5806998014450073
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-068 REFUSAL_FALSE_NEGATIVE

- Question：转电子大一准入需要哪六门课程达到多少分？
- Expected：微积分I一层次；大学物理I；电路分析；微积分II一层次；模拟电路；大学物理II；均不低于70分
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=6, chunk=136, year=2026, dept=-, score=0.6592690348625183; doc=17, chunk=155, year=2026, dept=-, score=0.6493565440177917; doc=8, chunk=68, year=2026, dept=-, score=0.6493565440177917; doc=6, chunk=154, year=2026, dept=-, score=0.6447964906692505; doc=6, chunk=146, year=2026, dept=-, score=0.6403669118881226
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

### TRAG-080 REFUSAL_FALSE_NEGATIVE

- Question：南赫学院大一选课的总学分和考试课程数量建议是多少？
- Expected：总学分以25以内为宜；最多30学分；考试课程10门以内
- Actual：根据当前知识库资料无法确定。
- Sources：无
- Diagnostic TopK：doc=24, chunk=237, year=2026, dept=-, score=0.7222495079040527; doc=24, chunk=258, year=2026, dept=-, score=0.7194973230361938; doc=24, chunk=259, year=2026, dept=-, score=0.717545211315155; doc=24, chunk=262, year=2026, dept=-, score=0.7004944086074829; doc=24, chunk=241, year=2026, dept=-, score=0.681888997554779
- Failure Reason：诊断检索 TopK 已包含可回答的关键证据，但最终 /api/rag/ask 仍拒答，判定为 Answerability 误拒绝。
- Suggested Fix：继续校准 FULL/PARTIAL 判定的一致性与结构化输出稳定性；本轮停止在记录，不再修改。

## Generation

案例数：10

### TRAG-009 PARTIAL

- Question：准备汉语言文学转专业面试时，对报名材料应做什么准备？
- Expected：重新熟悉提交材料；可能围绕材料针对性追问；表达应简明务实
- Actual：根据知识库中的经验资料，在准备汉语言文学转专业面试时，对报名材料应做以下准备：**务必对提交的报名材料内容进行重新熟悉**，因为面试老师会看到你提交的材料，并可能针对其中内容进行提问和拓展提问 [S1]。该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 26分流转汉文指北 (doc=20, year=2026, dept=文学院, score=0.6765984296798706)
- Diagnostic TopK：无
- Failure Reason：回答仅覆盖重新熟悉报名材料及可能被追问，遗漏表达应简明务实这一关键建议。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-022 PARTIAL

- Question：2026年人工智能学院转专业的考核形式是什么？
- Expected：资格审核后组织综合考核面试；按综合考核成绩确定名单
- Actual：根据南京大学2026年官方发布的准入计划，人工智能学院转专业的考核形式为**综合考核（面试）**[S2]。
- Sources：S2 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表 (doc=6, year=2026, dept=本科生院, score=0.6734434962272644)
- Diagnostic TopK：无
- Failure Reason：回答只说明考核形式为面试，遗漏按综合考核成绩确定名单这一录取规则。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-023 WRONG_SOURCE

- Question：2026年软件工程转专业至少要修软件学院哪几门基础课中的几门？
- Expected：计算系统基础；C语言程序设计基础；软件工程与计算I；离散数学；四门中至少2门并取得学分
- Actual：根据当前知识库中的经验资料，2026年软件工程转专业要求修读以下四门专业准入课程：C语言程序设计基础（CPL）、计算系统基础 I、离散数学、软件工程与计算 I [S1][S2]。对于**2025级及以后**的学生，这四门课程均为必修准入课程，即需要全部修完 [S1][S2]；对于**2024级及以前**的学生，则只需完成其中两门即可 [S1][S2]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 02 申请条件 (doc=8, year=2026, dept=软件学院, score=0.7236602902412415); S2 02 申请条件 (doc=17, year=2026, dept=软件学院, score=0.7236602902412415)
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.7236602902412415; doc=17, chunk=155, year=2026, dept=-, score=0.7236602902412415; doc=11, chunk=82, year=2026, dept=-, score=0.693400502204895; doc=5, chunk=37, year=2026, dept=-, score=0.6857840418815613; doc=5, chunk=38, year=2026, dept=-, score=0.6799354553222656
- Failure Reason：问题询问2026年官方要求，回答引用经验资料并错误声称2025级以后四门专业课必须全部修完；Ground Truth 为四门中至少修2门并取得学分。
- Suggested Fix：显式年份政策问题应优先采用对应年份官方来源，并避免把经验资料改写为政策；本轮不修改检索或生成。

### TRAG-027 WRONG_SOURCE

- Question：2026年软件工程转专业既要修哪些数学通修课，又要满足什么专业基础课要求？
- Expected：微积分I第一层次；微积分II第一层次；线性代数第一层次；四门专业基础课中至少2门
- Actual：根据当前知识库中的经验资料，2026年软件工程转专业的要求如下： **数学通修课：**需完成微积分 I（第一层次）和微积分 II（第一层次）；若申请时为**大二学生**，还需完成线性代数（第一层次）[S1][S2]。 **专业基础课：**申请前需**已修或在修**以下四门课程：C语言程序设计基础（CPL）、计算系统基础 I、离散数学、软件工程与计算 I。注意：**2024 级及以前**的学生完成其中两门即可，**2025 级及以后**的学生四门均为必修准入课程[S1][S2]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 02 申请条件 (doc=8, year=2026, dept=软件学院, score=0.7848897576332092); S2 02 申请条件 (doc=17, year=2026, dept=软件学院, score=0.7848897576332092)
- Diagnostic TopK：doc=8, chunk=68, year=2026, dept=-, score=0.7848897576332092; doc=17, chunk=155, year=2026, dept=-, score=0.7848897576332092; doc=11, chunk=82, year=2026, dept=-, score=0.7096738815307617; doc=5, chunk=37, year=2026, dept=-, score=0.6978949308395386; doc=5, chunk=41, year=2026, dept=-, score=0.6908856630325317
- Failure Reason：问题询问2026年软件工程官方准入要求，回答继续引用经验资料，给出2025级以后四门专业课全必修等与官方表不一致的条件。
- Suggested Fix：显式年份政策问题应优先采用对应年份官方来源，并避免把经验资料改写为政策；本轮不修改检索或生成。

### TRAG-032 PARTIAL

- Question：人文大类大一课程负担和数学英语安排有什么特点？
- Expected：多为讲座或论文结课；分流只需五门大类课；大一上有微积分、大一下不再开数学；大一全年有英语
- Actual：根据当前知识库中的经验资料[S3]，人文大类大一课程负担总体非常轻松：大部分课程以论文结课，少数开卷考试，极少数闭卷考试且会提前告知考题；平时无随堂作业，多数课程不点名，课业压力极小。数学安排在**大一上学期**设有微积分课程，**大一下学期起不再开设**数学课[S3]。英语安排为**大一一整年**开设英语课程，分流后仅历史学专业对专业外语有硬性课程要求[S3]。 **请注意**：该信息并非官方政策，具体课程安排请以学校或学院最新通知为准。
- Sources：S3 南京大学人文科学试验班求生指南 (doc=28, year=2026, dept=*人文科学试验班, score=0.6697366237640381)
- Diagnostic TopK：无
- Failure Reason：回答覆盖课程负担、数学和英语安排，但遗漏分流只需修五门大类课程。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-033 PARTIAL

- Question：人文大类学生想学有所获，指南建议重点培养哪些能力？
- Expected：多看专著和前沿论文；分析能力；知识功底；文献整理和信息提取能力
- Actual：根据当前知识库中的经验资料，人文大类学生想要学有所获，指南建议重点培养以下能力：**分析能力、知识功底、文献整理和信息提取能力**（尤其是对历史、哲学的同学）[S1][S3]。此外，学习新传的同学还应积极拓宽知识面并投身自媒体实践[S3]。该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 南京大学人文科学试验班求生指南 (doc=28, year=2026, dept=*人文科学试验班, score=0.6766660213470459); S3 南京大学人文科学试验班求生指南 (doc=28, year=2026, dept=*人文科学试验班, score=0.5815918445587158)
- Diagnostic TopK：无
- Failure Reason：回答列出分析、知识功底和文献信息能力，但遗漏多看专著和前沿论文这一学习建议。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-043 PARTIAL

- Question：地学大类学生转专业选课时，指南对数学层次有什么建议？
- Expected：修读目标专业要求的数学层次；若目标线代大一修读建议上学期提前修
- Actual：根据当前知识库中的经验资料，地学大类各院系对数学层次要求不同，有的修读一层次数学，有的修读二层次数学。**如果计划转专业，建议选课时修读对应目标专业所要求的数学层次** [S1][S2]。 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 地球科学与资源环境类生存指南 (doc=29, year=2026, dept=*理科试验班（地球科学与资源环境类）, score=0.7368510365486145); S2 地球科学与资源环境类生存指南 (doc=29, year=2026, dept=*理科试验班（地球科学与资源环境类）, score=0.6783273816108704)
- Diagnostic TopK：无
- Failure Reason：回答覆盖应选目标专业要求的数学层次，但遗漏目标专业若大一修线代则建议上学期提前修。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-045 PARTIAL

- Question：工科试验班为什么被指南认为利于转专业？
- Expected：自带一层次微积分；除通修课外准入课均可选；可按目标院系安排课程；转失败补课相对少
- Actual：根据当前知识库中的经验资料，工科试验班被认为利于转专业的原因主要有以下三点： 1. **课程设置高度灵活**：工科试验班没有专业必修课，所有准入课均为“可选”，学生可以一门准入课都不选，从而将所有精力投入目标专业的准备，实现“all in转专业”[S1][S3]。 2. **转专业失败代价较低**：即使转专业失败，工试学生也不会被发配到特别差的专业；同时，由于大一课业较少，转入对应院系后需补修的课程不多，甚至部分教务员可能允许学生大二继续尝试转专业[S3]。 3. **内部专业满意度高**：工科试验班内部的专业满足率持续提高，近似可视为专业任选，这降低了学生因分流不满而被迫转专业的风险[S1]。 **需要提醒**：该信息并非官方政策，具体转专业要求、课程设置及分流方案请以学校或学院最新通知为准。
- Sources：S1 工科试验班生存指南 (doc=30, year=2026, dept=*工科试验班, score=0.5993660092353821); S3 工科试验班生存指南 (doc=30, year=2026, dept=*工科试验班, score=0.5899030566215515)
- Diagnostic TopK：无
- Failure Reason：回答覆盖课程灵活、可按目标方向投入及失败后补课较少，但遗漏工试自带一层次微积分。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-079 PARTIAL

- Question：南赫学院微积分II有什么学习特点和建议？
- Expected：套路性强、计算量大；强调应用和做题而非证明；以作业和quiz为导向；需要微积分I基础
- Actual：根据南赫学院的经验资料，微积分II的主要特点是**套路性强、计算量大**，是微积分I的加强版，且大多数定理证明复杂，因此**不强调证明，而强调对知识的运用** [S3]。 学习建议如下： - 以**作业和quiz题目为导向**出发学习，重点在计算、应用和做题 [S3]。 - 充分掌握课本《James Stewart - Calculus》上的题目 [S3]。 - 推荐B站up主“一高数”，但需**选择性看课**，因为部分知识点不完全对应 [S3]。 - 可利用AI辅助学习，但需注意AI可能有计算错误，要以参考答案矫正 [S3]。 - 考前练习的指导意义很大，建议以考前练习为导向发现不足 [S3]。 > 该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S3 南赫生存手册v0（完整版） (doc=24, year=2026, dept=南京赫尔辛基大气与地球系统科学学院, score=0.6621659398078918)
- Diagnostic TopK：无
- Failure Reason：回答覆盖微积分II的做题、应用和作业导向，但未明确说明学习需要微积分I基础。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

### TRAG-082 PARTIAL

- Question：大气物理与大气动力学在第一性原理方面有什么差别？
- Expected：大气动力学以Navier-Stokes方程为核心；大气物理缺少兼顾可靠和实用的第一性原理；大气物理关注非绝热加热和湍流混合等参数化问题
- Actual：根据当前知识库中的经验资料，大气物理与大气动力学在第一性原理方面的主要差别是：大气动力学以 Navier-Stokes 方程作为可靠且实用的“第一性原理”，而大气物理学则没有这样兼顾可靠和实用的“第一性原理”[S1][S2]。该信息并非官方政策，请以学校或学院最新通知为准。
- Sources：S1 南赫生存手册v0（完整版） (doc=24, year=2026, dept=南京赫尔辛基大气与地球系统科学学院, score=0.7311747670173645); S2 南赫生存手册v0（完整版） (doc=24, year=2026, dept=南京赫尔辛基大气与地球系统科学学院, score=0.6662527322769165)
- Diagnostic TopK：无
- Failure Reason：回答比较了第一性原理，但遗漏大气物理重点处理非绝热加热、湍流混合等参数化问题。
- Suggested Fix：最终生成前逐项核对问题的全部关键事实；本轮不修改最终回答 Prompt。

## Index/Data Quality

案例数：0

## System Error

案例数：0
