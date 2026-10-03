# Gold label review — frozen 82-case set

Production Logic Version: `8c34ccdf16d283231ff972ca8860fff3baaa28a4`
Benchmark Tooling Version: `ae69fae6958fdfc5041677ea4f073d55fce45241` (evaluation-only tracing; not a new RAG version).

`evaluation/test-cases.json` remains frozen. Candidate labels are suggestions only. Verify each candidate against the current MySQL Document metadata and original Evidence; never use a Canonical Document as a final source ID.
Edit only the `Human Decision` fields in this file, then run `python evaluation/benchmark/sync_gold_review.py` to validate and apply human decisions to `gold-labels.json`. Generated suggestions are never confirmed.

## Coverage

- Total: 82
- Confirmed answerable / unanswerable: 0 / 0
- Confirmed expectedDocumentIds / expectedFacts: 0 / 0
- Confirmed reference answers: 0 (target 20–30)
- Scorable n — Hit@3 0, MRR 0, Citation 0, Refusal 0, Context Recall 0
- Coverage target before full bench: answerable + expectedDocumentIds should reach 82/82; reference answers need only the selected 20–30.

## Reference-answer shortlist

Write concise, evidence-bounded answers only after verifying the original Evidence. The list intentionally includes historical PASS and failure classes to reduce cherry-picking. Historical outcomes are context, not current results.

| Case | Suggested reference coverage | Historical outcome |
|---|---|---|
| TRAG-001 | FACT, YEAR_SCOPED, DEPARTMENT_SCOPED | PASS |
| TRAG-002 | FACT, YEAR_SCOPED, DEPARTMENT_SCOPED | PASS |
| TRAG-006 | POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED | REFUSAL_FALSE_NEGATIVE, PASS |
| TRAG-009 | EXPERIENCE, DEPARTMENT_SCOPED | REFUSAL_FALSE_NEGATIVE |
| TRAG-015 | POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED | PASS, REFUSAL_FALSE_NEGATIVE |
| TRAG-017 | POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED | PASS |
| TRAG-024 | POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED, COLLOQUIAL | PASS, REFUSAL_FALSE_NEGATIVE |
| TRAG-027 | COMPOUND, POLICY | REFUSAL_FALSE_NEGATIVE, WRONG_SOURCE |
| TRAG-028 | YEAR_SCOPED, DEPARTMENT_SCOPED | PASS, YEAR_MISMATCH |
| TRAG-031 | FACT, YEAR_SCOPED | REFUSAL_FALSE_NEGATIVE |
| TRAG-043 | EXPERIENCE, DEPARTMENT_SCOPED | PARTIAL |
| TRAG-045 | EXPERIENCE, COLLOQUIAL | PARTIAL |
| TRAG-048 | FACT, YEAR_SCOPED, DEPARTMENT_SCOPED | REFUSAL_FALSE_NEGATIVE |
| TRAG-049 | FACT, YEAR_SCOPED | PASS, REFUSAL_FALSE_NEGATIVE |
| TRAG-056 | EXPERIENCE | PARTIAL, WRONG |
| TRAG-061 | EXPERIENCE, FALLBACK | PASS, REFUSAL_FALSE_NEGATIVE |
| TRAG-062 | EXPERIENCE, COLLOQUIAL | REFUSAL_FALSE_NEGATIVE |
| TRAG-067 | EXPERIENCE, YEAR_SCOPED | PASS, REFUSAL_FALSE_NEGATIVE |
| TRAG-070 | EXPERIENCE, FALLBACK | PARTIAL, REFUSAL_FALSE_NEGATIVE |
| TRAG-072 | POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED | PASS, YEAR_MISMATCH |
| TRAG-073 | POLICY, YEAR_SCOPED, FALLBACK | REFUSAL_FALSE_NEGATIVE |
| TRAG-076 | FACT, DEPARTMENT_SCOPED | PARTIAL, REFUSAL_FALSE_NEGATIVE |
| TRAG-077 | COMPOUND, POLICY, EXPERIENCE | REFUSAL_FALSE_NEGATIVE |
| TRAG-080 | EXPERIENCE, DEPARTMENT_SCOPED | REFUSAL_FALSE_NEGATIVE |

UNANSWERABLE coverage: the frozen 82 cases contain no independently verified unanswerable Gold candidate in the current metadata. Do not relabel a positive case as unanswerable. The separate `SMOKE-NEG-001` remains the refusal control and is outside this 82-case reference count.

# Batch 1 — 20 cases

## TRAG-002

Query: 2026级智能科学与技术专业各类必修和选修学分如何构成？
Source file: 2026级智能科学与技术主修培养方案_28b996c9.pdf
Category: 培养方案
Existing Expected Facts:
- 通识通修必修59学分
- 学科专业必修49学分
- 多元发展选修30学分
- 毕业论文或设计必修6学分

Candidate Evidence Documents:
- Document ID: 18; Title: 2026级智能科学与技术主修培养方案_28b996c9; sourceType: OFFICIAL_PDF; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [18] (no auto-mapped current IDs)
Reference answer required: YES — FACT, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-003

Query: 2026级智能科学与技术大一上建议修哪些数学和编程课程？
Source file: 2026级智能科学与技术主修培养方案_28b996c9.pdf
Category: 课程规划
Existing Expected Facts:
- 微积分I第一层次
- 线性代数第一层次
- 智能程序设计C语言

Candidate Evidence Documents:
- Document ID: 18; Title: 2026级智能科学与技术主修培养方案_28b996c9; sourceType: OFFICIAL_PDF; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [18] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-004

Query: 2026级智能科学与技术专业二年级下有哪些专业核心课？
Source file: 2026级智能科学与技术主修培养方案_28b996c9.pdf
Category: 课程规划
Existing Expected Facts:
- 机器人导论
- 类脑计算基础
- 模式识别与计算机视觉

Candidate Evidence Documents:
- Document ID: 18; Title: 2026级智能科学与技术主修培养方案_28b996c9; sourceType: OFFICIAL_PDF; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [18] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-005

Query: 2026级智能科学与技术专业的毕业设计安排在何时、多少学分？
Source file: 2026级智能科学与技术主修培养方案_28b996c9.pdf
Category: 培养方案
Existing Expected Facts:
- 四年级下学期
- 6学分
- 144学时

Candidate Evidence Documents:
- Document ID: 18; Title: 2026级智能科学与技术主修培养方案_28b996c9; sourceType: OFFICIAL_PDF; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [18] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-007

Query: 2026年分流或转入汉语言文学需要跨选哪些大一课程？
Source file: 26分流转汉文指北.pdf
Category: 课程规划
Existing Expected Facts:
- 古代文学
- 古代汉语

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-011

Query: 化生大类大一上有哪些主要专业相关课程？
Source file: 化生大类生存指南_.pdf
Category: 课程规划
Existing Expected Facts:
- 微积分I二层次
- 大学化学实验基础
- 普通生物学上
- 大学化学A

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-012

Query: 化生大类分流进生命科学方向，大一下程序设计课程如何选择？
Source file: 化生大类生存指南_.pdf
Category: 课程规划
Existing Expected Facts:
- Python程序设计与C语言程序设计二选一

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-032

Query: 人文大类大一课程负担和数学英语安排有什么特点？
Source file: 南京大学人文大类求生指南(1).docx
Category: 课程规划
Existing Expected Facts:
- 多为讲座或论文结课
- 分流只需五门大类课
- 大一上有微积分、大一下不再开数学
- 大一全年有英语

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-034

Query: 2025级起技术科学试验班主要有哪四个分流方向？
Source file: 南京大学技术科学试验班新生生存指南.pdf
Category: 明确事实
Existing Expected Facts:
- 智能科学与技术
- 自动化机器人方向
- 集成电路设计与集成系统
- 数字经济

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-037

Query: 技科自动化机器人方向大一下有哪些准入课？
Source file: 南京大学技术科学试验班新生生存指南.pdf
Category: 课程规划
Existing Expected Facts:
- 数据结构与算法设计
- 机器人与自动化导论

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-038

Query: 技科集成电路设计与集成系统方向大一下有哪些准入课？
Source file: 南京大学技术科学试验班新生生存指南.pdf
Category: 课程规划
Existing Expected Facts:
- 信息科学中的物理学下
- 电路分析

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-039

Query: 地学大类有哪四个分流院系？
Source file: 地学大类生存指南(2).docx
Category: 明确事实
Existing Expected Facts:
- 地球科学与工程学院
- 地理科学与海洋学院
- 环境学院
- 大气科学学院

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-047

Query: 工试学生若想分流光电并计划保研，指南建议大一额外修什么课，为什么？
Source file: 工科试验班生存指南_Revised by 吻安.docx
Category: 课程规划
Existing Expected Facts:
- 电路分析
- 光电保研要求必修
- 避免后续课程质量或补修问题

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-048

Query: 2025级数理大类包含哪些学院方向？
Source file: 数理大类生存指北.pdf
Category: 明确事实
Existing Expected Facts:
- 数学学院
- 物理学院
- 大气科学学院
- 天文与空间科学学院

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 数理大类生存指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — FACT, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-049

Query: 2024级数理大类计划分流比例和人数如何分配？
Source file: 数理大类生存指北.pdf
Category: 明确事实
Existing Expected Facts:
- 数学28%/97人
- 物理46%/159人
- 大气14%/48人
- 天文12%/41人

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 数理大类生存指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — FACT, YEAR_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-065

Query: 转光电的两学期课程规划建议是什么？
Source file: 转光电概述（新）.docx
Category: 课程规划
Existing Expected Facts:
- 大一上微积分I、普物力学
- 大一下微积分II、普物热学、大学物理实验1
- 大二大学化学

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-069

Query: 2024和2025年大一转电子的报名、录取和报录比是多少？
Source file: 转电子指北.pdf
Category: 时间敏感
Existing Expected Facts:
- 2024年37报名30录取约81%
- 2025年38报名24录取约63.15%

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-075

Query: 电子学院2026版C语言课程发生了什么变化？
Source file: 转电子指北2026版.pdf
Category: 课程规划
Existing Expected Facts:
- 课程号改为18001790
- 从通修改为专业平台课
- 不对外开放
- 跨专业选修可能受限

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-076

Query: 电子专业导学课有什么修读要求，选某一门是否限制后续分流方向？
Source file: 转电子指北2026版.pdf
Category: 课程规划
Existing Expected Facts:
- 5门中至少修1门
- 对外开放4门
- 鼓楼开设1学分
- 选择某门不限制分流方向

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PARTIAL, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — FACT, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-082

Query: 大气物理与大气动力学在第一性原理方面有什么差别？
Source file: 难喝生存手册v0（完整版）.pdf
Category: 明确事实
Existing Expected Facts:
- 大气动力学以Navier-Stokes方程为核心
- 大气物理缺少兼顾可靠和实用的第一性原理
- 大气物理关注非绝热加热和湍流混合等参数化问题

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL, PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

# Batch 2 — 22 cases

## TRAG-006

Query: 2026年人文大类分流汉语言文学有多少名额，面向外专业转入有多少名额？
Source file: 26分流转汉文指北.pdf
Category: 转专业政策
Existing Expected Facts:
- 分流名额10个
- 外专业转专业名额11个
- 两类名额不互通

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE, PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-008

Query: 2026年汉文二次选拔的初试和复试形式是什么？
Source file: 26分流转汉文指北.pdf
Category: 面试/机试
Existing Expected Facts:
- 初试闭卷笔试2小时100分
- 复试面试10分钟
- 自我陈述2分钟
- 针对性提问8分钟

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE, PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-009

Query: 准备汉语言文学转专业面试时，对报名材料应做什么准备？
Source file: 26分流转汉文指北.pdf
Category: 经验
Existing Expected Facts:
- 重新熟悉提交材料
- 可能围绕材料针对性追问
- 表达应简明务实

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-010

Query: 文学院是否接受大二学年的汉语言文学转专业申请？
Source file: 26分流转汉文指北.pdf
Category: 转专业政策
Existing Expected Facts:
- 不接受大二学年转专业申请

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-015

Query: 2026年文学院汉语言文学转专业需要修哪些准入课程、至少几门？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 古代汉语上
- 古代汉语下
- 中国古代文学一
- 中国古代文学二
- 至少3门共9学分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-016

Query: 2026年汉语言文学跨大类准入对第一学期平均学分绩和不及格记录有什么要求？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 条件
Existing Expected Facts:
- 平均学分绩4.0及以上
- 无不及格记录
- 无违纪行为

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-017

Query: 2026年法学大一转专业的准入课程条件是什么？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 法理学导论、刑法学总论一、民法学总则、宪法学中任意1门
- 学期结束取得学分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-018

Query: 2026年数学学院数学类转专业可用哪两套数学课程方案满足准入？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 数分I、II+高代I、II+解析几何
- 或微积分I、II第一层次+线性代数第一层次
- 两套满足一套即可

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-019

Query: 2026年数学学院准入考核如何使用校内数学竞赛成绩？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 条件
Existing Expected Facts:
- 校内数学竞赛成绩可计附加分
- 数学专业类或非数学专业A类按课程方案对应

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-020

Query: 2026年物理学类跨大类准入需要修哪些数学和物理课程？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 微积分I第一层次
- 微积分II第一层次
- 线性代数第一层次
- 力学
- 热学

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-021

Query: 2026年人工智能专业跨大类准入的6门课程和成绩门槛是什么？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 高等代数一
- 高等代数二
- 数学分析一
- 数学分析二
- 离散数学
- 程序设计基础
- 每门80分及以上

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-022

Query: 2026年人工智能学院转专业的考核形式是什么？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 面试/机试
Existing Expected Facts:
- 资格审核后组织综合考核面试
- 按综合考核成绩确定名单

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-023

Query: 2026年软件工程转专业至少要修软件学院哪几门基础课中的几门？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 计算系统基础
- C语言程序设计基础
- 软件工程与计算I
- 离散数学
- 四门中至少2门并取得学分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-025

Query: 2026年计算机科学与技术转专业需要修哪两门准入课，成绩要求多少？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 离散数学
- 程序设计基础或计算机程序的构造和解释
- 成绩80分及以上

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-026

Query: 2026年计算机学院转专业综合考核有哪些环节？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 面试/机试
Existing Expected Facts:
- 笔试
- 机试
- 面试
- 任一项不及格不予录取

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-028

Query: 2026年社会学院大一跨类准入需要哪些课程条件？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 已取得社会与心理科学导论学分
- 社会学概论、社会工作概论、心理学概论上中在修任意1门

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, YEAR_MISMATCH
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-029

Query: 2026年信息管理与信息系统专业跨类准入可选四门课程中的哪一门？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 转专业政策
Existing Expected Facts:
- 信息资源管理导论
- 信息组织
- 程序设计语言
- 数据思维
- 任意1门

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-033

Query: 人文大类学生想学有所获，指南建议重点培养哪些能力？
Source file: 南京大学人文大类求生指南(1).docx
Category: 学习经验
Existing Expected Facts:
- 多看专著和前沿论文
- 分析能力
- 知识功底
- 文献整理和信息提取能力

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-036

Query: 2025级技科分流加权排名主要参考哪些课程？
Source file: 南京大学技术科学试验班新生生存指南.pdf
Category: 条件
Existing Expected Facts:
- 微积分
- 线性代数
- 信息科学中的物理学

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-040

Query: 地学大类分流排名看哪四门课的成绩？
Source file: 地学大类生存指南(2).docx
Category: 条件
Existing Expected Facts:
- 英语
- 数学通修课
- 大学化学A
- 地球科学与资源环境导论

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-043

Query: 地学大类学生转专业选课时，指南对数学层次有什么建议？
Source file: 地学大类生存指南(2).docx
Category: 学习经验
Existing Expected Facts:
- 修读目标专业要求的数学层次
- 若目标线代大一修读建议上学期提前修

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-045

Query: 工科试验班为什么被指南认为利于转专业？
Source file: 工科试验班生存指南_Revised by 吻安.docx
Category: 经验
Existing Expected Facts:
- 自带一层次微积分
- 除通修课外准入课均可选
- 可按目标院系安排课程
- 转失败补课相对少

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, COLLOQUIAL
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

# Batch 3 — 22 cases

## TRAG-050

Query: 数理大类分流数学，往年核心课程平均学分绩大约需要多少？
Source file: 数理大类生存指北.pdf
Category: 条件
Existing Expected Facts:
- 通常约4.2
- 2024级因考试较难约4.1

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 数理大类生存指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-052

Query: 转法学需要满足哪两个基本条件？
Source file: 法学转专业分享（更新至2025年）.docx
Category: 转专业政策
Existing Expected Facts:
- 四门准入课中任意一门取得学分
- 参加笔试和面试
- 按综合成绩和计划人数择优录取

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-054

Query: 法学转专业笔试的科目选择和分值结构是什么？
Source file: 法学转专业分享（更新至2025年）.docx
Category: 面试/机试
Existing Expected Facts:
- 民法学总则50分
- 刑法学50分
- 法理学50分
- 三选二作答

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-055

Query: 法学转专业面试通常有哪些环节？
Source file: 法学转专业分享（更新至2025年）.docx
Category: 面试/机试
Existing Expected Facts:
- 自我介绍
- 专业知识
- 英语口语
- 专业知识可在民法、刑法、法理中选一

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-058

Query: 转AI失败为何可能产生课程无法替代的风险？
Source file: 转AI风险.md
Category: 风险经验
Existing Expected Facts:
- AI课程不能替代其他专业课程
- 失败后仍需补通修微积分
- 失败代价较大

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-059

Query: AI学院转专业面试有什么经验性特征和风险？
Source file: 转AI风险.md
Category: 面试/机试
Existing Expected Facts:
- 通常不问专业问题、偏闲聊
- 面试仍会卡人
- 存在不可控风险

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-063

Query: 转计困难主要体现在哪四方面？
Source file: 转CS风险.md
Category: 经验
Existing Expected Facts:
- 数学要求高
- 笔试思维要求高
- 机试题型多变且变难
- 报考人数与成功率不确定

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-064

Query: 2026级跨专业准入光电信息类，准入课程最低要求是什么？
Source file: 转光电概述（新）.docx
Category: 转专业政策
Existing Expected Facts:
- 普通物理力学、大学化学、普通物理热学中任意一门
- 已修或在修并在学期结束取得学分

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-066

Query: 转光电面试为什么要重视数学和物理成绩？
Source file: 转光电概述（新）.docx
Category: 面试/机试
Existing Expected Facts:
- 学院重视数学物理能力
- 面试官会看第一学期成绩
- 可能追问第二学期期中成绩

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-067

Query: 2026级光材二次拔尖的面试流程和题型有哪些？
Source file: 转光电概述（新）.docx
Category: 面试/机试
Existing Expected Facts:
- 自我介绍
- 高中印象深刻的事
- 多个问题选答且准备1分钟
- 涉及光学隐身、信息材料、海市蜃楼、冷热杯破裂等

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, YEAR_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-071

Query: 转电子课程复习有哪些通用建议？
Source file: 转电子指北.pdf
Category: 学习经验
Existing Expected Facts:
- 多做往年卷
- 认真听考前指导
- 大物重视书后习题

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-072

Query: 2026年大一转电子的六门准入课和最低成绩要求是什么？
Source file: 转电子指北2026版.pdf
Category: 转专业政策
Existing Expected Facts:
- 微积分I和II第一层次
- 大学物理I和II
- 电路分析
- 模拟电路
- 总评均不低于70

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, YEAR_MISMATCH
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-073

Query: 2026年电子学院转专业面试时间和流程大致怎样？
Source file: 转电子指北2026版.pdf
Category: 面试/机试
Existing Expected Facts:
- 5月底面试
- 1分钟个人陈述
- 老师轮流提问
- 问题可涉及大物、电分、模电、C语言

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, FALLBACK
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-074

Query: 软院之外，电子学院的电路分析和模拟电路应如何学习？
Source file: 转电子指北2026版.pdf
Category: 学习经验
Existing Expected Facts:
- 知识量大且琐碎
- 依赖课后自学
- 重理解记忆知识点
- 可参考课程视频和做题

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-079

Query: 南赫学院微积分II有什么学习特点和建议？
Source file: 难喝生存手册v0（完整版）.pdf
Category: 学习经验
Existing Expected Facts:
- 套路性强、计算量大
- 强调应用和做题而非证明
- 以作业和quiz为导向
- 需要微积分I基础

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-081

Query: 大气电学有哪些主要研究方法？
Source file: 难喝生存手册v0（完整版）.pdf
Category: 学习经验
Existing Expected Facts:
- 观测
- 数值模拟
- 少量室内实验
- 观测可用高速摄像机或天线阵

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-013

Query: 化生大类分流进化学方向，大一下需要修哪些主要课程？
Source file: 化生大类生存指南_.pdf
Category: 课程规划
Existing Expected Facts:
- 微积分II二层次
- Python或C语言二选一
- 大学化学B
- 大学化学实验
- 普通物理
- 马克思主义原理

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-014

Query: 化生大类学生大一需要完成哪些劳育和志愿时长？
Source file: 化生大类生存指南_.pdf
Category: 综合生存指南
Existing Expected Facts:
- 基础劳育10小时
- 每学期志愿10小时
- 完成大学生劳育考试

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-030

Query: 人文大类有哪些主要分流方向？
Source file: 南京大学人文大类求生指南(1).docx
Category: 综合生存指南
Existing Expected Facts:
- 历史
- 哲学
- 新闻传播
- 汉语国际教育

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-044

Query: 2026级工科试验班有哪些主要分流学院和专业？
Source file: 工科试验班生存指南_Revised by 吻安.docx
Category: 综合生存指南
Existing Expected Facts:
- 现代工程与应用科学学院
- 工程管理学院
- 能源与资源学院
- 生物医学工程学院
- 共8个专业方向

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-057

Query: 人工智能专业大一上课程压力主要体现在哪些课？
Source file: 转AI风险.md
Category: 课程规划
Existing Expected Facts:
- 数学分析每周5学时
- 高等代数5学时
- 程序设计基础6学时
- 离散数学4学时
- 课表很满

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-078

Query: 南赫学院的课程体系主要分为哪些类别？
Source file: 难喝生存手册v0（完整版）.pdf
Category: 培养方案
Existing Expected Facts:
- 基础课
- 概论课
- 专业核心
- 地球系统学科交叉模块
- 自选模块
- 专业选修

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

# Batch 4 — 18 cases

## TRAG-001

Query: 2026级智能科学与技术专业学制和总学分分别是多少？
Source file: 2026级智能科学与技术主修培养方案_28b996c9.pdf
Category: 培养方案
Existing Expected Facts:
- 学制4年
- 总学分144

Candidate Evidence Documents:
- Document ID: 18; Title: 2026级智能科学与技术主修培养方案_28b996c9; sourceType: OFFICIAL_PDF; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [18] (no auto-mapped current IDs)
Reference answer required: YES — FACT, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-024

Query: 软工2026年转专业复试包括什么，什么情况不予录取？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 别名
Existing Expected Facts:
- 复试包括机试和面试
- 机试或面试任一不及格不录取

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED, COLLOQUIAL
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-027

Query: 2026年软件工程转专业既要修哪些数学通修课，又要满足什么专业基础课要求？
Source file: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表.pdf
Category: 组合问题
Existing Expected Facts:
- 微积分I第一层次
- 微积分II第一层次
- 线性代数第一层次
- 四门专业基础课中至少2门

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE, WRONG_SOURCE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — COMPOUND, POLICY
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-031

Query: 2023级人文大类110人的分流和转专业人数如何分布？
Source file: 南京大学人文大类求生指南(1).docx
Category: 明确事实
Existing Expected Facts:
- 新传57人
- 历史22人
- 汉语国际教育11人
- 哲学5人
- 转专业15人

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — FACT, YEAR_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-035

Query: 技术科学试验班通常大一和大二分别在哪个校区？
Source file: 南京大学技术科学试验班新生生存指南.pdf
Category: 综合生存指南
Existing Expected Facts:
- 大一鼓楼校区
- 大二起苏州校区

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-041

Query: 地学大类2023级分流去向人数是多少？
Source file: 地学大类生存指南(2).docx
Category: 明确事实
Existing Expected Facts:
- 大气17人
- 环境29人
- 地科13人
- 地海24人
- 转出39人

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-042

Query: 地海拔尖对数学层次有什么要求？
Source file: 地学大类生存指南(2).docx
Category: 条件
Existing Expected Facts:
- 需要一层次数学
- 微积分I一层次
- 微积分II一层次
- 线性代数

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-046

Query: 2026级工试想分流现工院，需要修读哪些课中的至少一门？
Source file: 工科试验班生存指南_Revised by 吻安.docx
Category: 转专业政策
Existing Expected Facts:
- 普通物理力学
- 普通物理热学
- 大学化学
- 三门中一门

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-051

Query: 数理大类准备分流数学时，解析几何、数学分析、高等代数分别应如何投入？
Source file: 数理大类生存指北.pdf
Category: 学习经验
Existing Expected Facts:
- 解析几何课后题和考前突击可高分
- 数学分析应最大投入并打牢课本例题作业
- 高代重基础且赋分，若求后续学习也要打牢

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 数理大类生存指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-053

Query: 法学转专业四门准入课程分别是什么？
Source file: 法学转专业分享（更新至2025年）.docx
Category: 明确事实
Existing Expected Facts:
- 法理学导论
- 民法学总则
- 刑法学总论一
- 宪法学

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-056

Query: 法学转专业备考为什么应重点准备民法总则和刑法总论？
Source file: 法学转专业分享（更新至2025年）.docx
Category: 经验
Existing Expected Facts:
- 多数人笔试选择民法和刑法
- 大一下是重要阶段
- 需结合教材笔记和往年题全面备考

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL, WRONG
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-060

Query: 转计算机前，资料建议如何平衡微积分II和笔试机试准备？
Source file: 转CS风险.md
Category: 经验
Existing Expected Facts:
- 微积分II需要大量投入
- 同时不能忽视离散、高程、计算系统等
- 平衡数学刷题与笔试机试内容

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-061

Query: 转CS笔试的题量和数学题风格有什么经验？
Source file: 转CS风险.md
Category: 面试/机试
Existing Expected Facts:
- 笔试2小时4题
- 微积分偏证明
- 不考多元函数微分积分、曲线曲面积分等下学期内容

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, FALLBACK
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-062

Query: 转CS机试失败案例中三道题大致是什么类型、得分如何？
Source file: 转CS风险.md
Category: 面试/机试
Existing Expected Facts:
- 第一题大模拟60分
- 第二题状压DP约10分
- 第三题困难图论约10分

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, COLLOQUIAL
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-068

Query: 转电子大一准入需要哪六门课程达到多少分？
Source file: 转电子指北.pdf
Category: 转专业政策
Existing Expected Facts:
- 微积分I一层次
- 大学物理I
- 电路分析
- 微积分II一层次
- 模拟电路
- 大学物理II
- 均不低于70分

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-070

Query: 电子转专业面试一般有哪些环节和提问范围？
Source file: 转电子指北.pdf
Category: 面试/机试
Existing Expected Facts:
- 1分钟自我介绍
- 3到4分钟提问
- 可能问大学物理、电路分析、模拟电路等

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, FALLBACK
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-077

Query: 2026年转电子需要修哪些准入课，面试主要问什么？
Source file: 转电子指北2026版.pdf
Category: 组合问题
Existing Expected Facts:
- 六门准入课均70分以上
- 面试1分钟陈述加提问
- 可能问大物、电分、模电、C语言

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — COMPOUND, POLICY, EXPERIENCE
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW

## TRAG-080

Query: 南赫学院大一选课的总学分和考试课程数量建议是多少？
Source file: 难喝生存手册v0（完整版）.pdf
Category: 课程规划
Existing Expected Facts:
- 总学分以25以内为宜
- 最多30学分
- 考试课程10门以内

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, DEPARTMENT_SCOPED
Current reviewStatus: NEEDS_REVIEW

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW
