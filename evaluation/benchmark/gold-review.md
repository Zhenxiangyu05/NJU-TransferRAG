# Gold label review — frozen 82-case set

Production Logic Version: `8c34ccdf16d283231ff972ca8860fff3baaa28a4`
Benchmark Tooling Version: `ae69fae6958fdfc5041677ea4f073d55fce45241` (evaluation-only tracing; not a new RAG version).

`evaluation/test-cases.json` remains frozen. Candidate labels are suggestions only. Verify each candidate against the current MySQL Document metadata and original Evidence; never use a Canonical Document as a final source ID.
Gold `expectedFactsOverride` is optional and, when present, completely replaces the frozen test case's `expectedFacts` array; it is never an index patch.
Edit only the `Human Decision` fields in this file, then run `python evaluation/benchmark/sync_gold_review.py` to validate and apply human decisions to `gold-labels.json`. Generated suggestions are never confirmed.

## Coverage

- Total: 82
- Confirmed total / answerable / unanswerable: 82 / 71 / 11
- Confirmed expectedDocumentIds / expectedFacts: 82 / 71
- Confirmed reference answers: 15 (target 20–30)
- Scorable n — Hit@3 71, MRR 71, Citation 71, Refusal 11, Context Recall 20, Expected Fact Recall 71
- Full review target: all 82 cases decided; answerable cases have Evidence IDs and refusals have empty IDs. Reference answers cover only the selected 20–30.

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

UNANSWERABLE Gold is evidence-relative: confirmed negative cases are scorable for Refusal Accuracy only and do not enter retrieval, citation, expected-fact, or context-recall metrics. The separate `SMOKE-NEG-001` remains outside this 82-case set.

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 据以2024级课程为参考的个人化生大类手册，微积分I二层次
- 据以2024级课程为参考的个人化生大类手册，大学化学实验基础
- 据以2024级课程为参考的个人化生大类手册，普通生物学上
- 据以2024级课程为参考的个人化生大类手册，大学化学A

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据以2024级课程为参考的个人化生大类手册，Python程序设计与C语言程序设计二选一

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 自动化（机器人方向）
- 集成电路设计与集成系统
- 数字经济

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 该指南所列2025级自动化（机器人方向）大一下准入课包括数据结构与算法设计。
- 该指南所列2025级自动化（机器人方向）大一下准入课包括机器人与自动化导论。

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 该指南所列2025级集成电路设计与集成系统方向大一下准入课包括信息科学中的物理学（下）。
- 该指南所列2025级集成电路设计与集成系统方向大一下准入课包括电路分析。

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 2025年38报名、24接收，表列报录比63%。

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，五门专业导学课中至少选修一门；其中四门对外开放
- 据2026年个人转电子指南，导学课在鼓楼开设，计一学分
- 据2026年个人转电子指南，选择某门导学课不限制之后的专业分流方向

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PARTIAL, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — FACT, DEPARTMENT_SCOPED
Current reviewStatus: CONFIRMED

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
- 大气物理研究Navier-Stokes方程中的非绝热加热项和湍流混合项。

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL, PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE, PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 据2026年个人汉文分流指南，不接受大二学年转专业申请

Candidate Evidence Documents:
- Document ID: 20; Title: 26分流转汉文指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [20] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级汉语言文学条目：第一学期全部课程平均学分绩须达4.0（含）以上
- 2026准入计划中2025级汉语言文学条目：课程无不及格记录

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级法学条目：法理学导论、刑法学总论一、民法学总则、宪法学中任意1门
- 2026准入计划中2025级法学条目：学期结束取得学分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级数学类条目：数分I、II+高代I、II+解析几何
- 2026准入计划中2025级数学类条目：或微积分I、II第一层次+线性代数第一层次
- 2026准入计划中2025级数学类条目：两套满足一套即可

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级数学类条目：校内数学竞赛成绩可以计入准入考核附加分
- 2026准入计划中2025级数学类条目：修读数学分析、高等代数、解析几何路线，对应数学专业类竞赛附加分
- 2026准入计划中2025级数学类条目：修读第一层次微积分I/II、线性代数路线，对应非数学专业A类竞赛附加分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级物理学类条目：微积分I第一层次
- 2026准入计划中2025级物理学类条目：微积分II第一层次
- 2026准入计划中2025级物理学类条目：线性代数第一层次
- 2026准入计划中2025级物理学类条目：力学
- 2026准入计划中2025级物理学类条目：热学

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级人工智能条目：资格审核后组织综合考核面试
- 2026准入计划中2025级人工智能条目：按综合考核成绩确定名单

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2024级软件工程条目：计算系统基础、C语言程序设计基础、软件工程与计算I、离散数学四门中至少修读两门并取得学分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2024级计算机科学与技术条目：离散数学，以及程序设计基础或计算机程序的构造和解释
- 2026准入计划中2024级计算机科学与技术条目：两门准入课程本学期结束须取得学分且成绩均达80分（含）以上

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中计算机学院综合考核条目：笔试
- 2026准入计划中计算机学院综合考核条目：机试
- 2026准入计划中计算机学院综合考核条目：面试
- 2026准入计划中计算机学院综合考核条目：任一项不及格不予录取

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级社会工作条目：已取得社会与心理科学导论学分
- 2026准入计划中2025级社会工作条目：社会学概论、社会工作概论、心理学概论上中在修任意1门

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, YEAR_MISMATCH
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2025级信息管理与信息系统条目：信息资源管理导论
- 2026准入计划中2025级信息管理与信息系统条目：信息组织
- 2026准入计划中2025级信息管理与信息系统条目：程序设计语言
- 2026准入计划中2025级信息管理与信息系统条目：数据思维
- 2026准入计划中2025级信息管理与信息系统条目：任意1门

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2025年个人汉文转专业指南，指南建议阅读两三本经典原著、四五本相关衍生著作，并参考学术论文或书籍
- 据2025年个人汉文转专业指南，指南强调耐心阅读、提炼自己的观点并培养文学思考与表达能力

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据以2025级经验为主的个人技科指南，微积分
- 据以2025级经验为主的个人技科指南，线性代数
- 据以2025级经验为主的个人技科指南，信息科学中的物理学

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人地学大类生存指南，英语
- 据个人地学大类生存指南，数学通修课
- 据个人地学大类生存指南，大学化学A
- 据个人地学大类生存指南，地球科学与资源环境导论

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人地学大类生存指南，修读目标专业要求的数学层次
- 据个人地学大类生存指南，若目标线代大一修读建议上学期提前修

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, DEPARTMENT_SCOPED
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, COLLOQUIAL
Current reviewStatus: CONFIRMED

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
- 据2025级个人数理大类指南，按指南往年经验，数学分流核心课平均学分绩约4.2可进入数学学院
- 据2025级个人数理大类指南，指南回顾2024级因考试较难约降到4.1；这不是固定准入线

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 数理大类生存指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据更新至2025年的个人法学转专业指南，四门准入课中任意一门取得学分
- 据更新至2025年的个人法学转专业指南，参加笔试和面试
- 据更新至2025年的个人法学转专业指南，按综合成绩和计划人数择优录取

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据更新至2025年的个人法学转专业指南，民法学总则50分
- 据更新至2025年的个人法学转专业指南，刑法学50分
- 据更新至2025年的个人法学转专业指南，法理学50分
- 据更新至2025年的个人法学转专业指南，三选二作答

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据更新至2025年的个人法学转专业指南，自我介绍
- 据更新至2025年的个人法学转专业指南，专业知识
- 据更新至2025年的个人法学转专业指南，英语口语
- 据更新至2025年的个人法学转专业指南，专业知识可在民法、刑法、法理中选一

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人整理的转AI风险材料，AI专业课程不能直接替代其他专业课程，其他专业课程也不能直接替代AI课程
- 据个人整理的转AI风险材料，若转AI失败，所修AI数学分析仍可能需要补修通修微积分，作者认为失败代价较大

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人整理的转AI风险材料，通常不问专业问题、偏闲聊
- 据个人整理的转AI风险材料，面试仍会卡人
- 据个人整理的转AI风险材料，存在不可控风险

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2026年个人转光电概述，个人概述转述的准入文件要求：普通物理（力学）、大学化学、普通物理（热学）三门中至少已修或在修一门，并在学期结束取得学分

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2026年个人转光电概述，学院重视数学物理能力
- 据2026年个人转光电概述，面试官会看第一学期成绩
- 据2026年个人转光电概述，可能追问第二学期期中成绩

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2026年个人转光电概述，自我介绍
- 据2026年个人转光电概述，高中印象深刻的事
- 据2026年个人转光电概述，多个问题选答且准备1分钟
- 据2026年个人转光电概述，涉及光学隐身、信息材料、海市蜃楼、冷热杯破裂等

Candidate Evidence Documents:
- Document ID: 13; Title: 转光电概述（新）; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [13] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, YEAR_SCOPED
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，指南建议利用往年卷、课后习题和考前习题课复习
- 据2026年个人转电子指南，指南建议大学物理重视书后习题

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，微积分I和II第一层次
- 据2026年个人转电子指南，大学物理I和II
- 据2026年个人转电子指南，电路分析
- 据2026年个人转电子指南，模拟电路
- 据2026年个人转电子指南，总评均不低于70

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, YEAR_MISMATCH
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, DEPARTMENT_SCOPED
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，5月底面试
- 据2026年个人转电子指南，1分钟个人陈述
- 据2026年个人转电子指南，老师轮流提问
- 据2026年个人转电子指南，问题可涉及大物、电分、模电、C语言

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — POLICY, YEAR_SCOPED, FALLBACK
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 据以2023级培养方案为背景的个人南赫手册，套路性强、计算量大
- 据以2023级培养方案为背景的个人南赫手册，强调应用和做题而非证明
- 据以2023级培养方案为背景的个人南赫手册，以作业和quiz为导向
- 据以2023级培养方案为背景的个人南赫手册，需要微积分I基础

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据以2023级培养方案为背景的个人南赫手册，观测
- 据以2023级培养方案为背景的个人南赫手册，数值模拟
- 据以2023级培养方案为背景的个人南赫手册，少量室内实验
- 据以2023级培养方案为背景的个人南赫手册，观测可用高速摄像机或天线阵

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据以2024级课程为参考的个人化生大类手册，微积分II二层次
- 据以2024级课程为参考的个人化生大类手册，Python或C语言二选一
- 据以2024级课程为参考的个人化生大类手册，大学化学B
- 据以2024级课程为参考的个人化生大类手册，大学化学实验
- 据以2024级课程为参考的个人化生大类手册，普通物理
- 据以2024级课程为参考的个人化生大类手册，马克思主义原理

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据以2024级课程为参考的个人化生大类手册，基础劳育10小时
- 据以2024级课程为参考的个人化生大类手册，每学期志愿10小时
- 据以2024级课程为参考的个人化生大类手册，完成大学生劳育考试

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 化生大类生存指南; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS, PARTIAL
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2025年个人汉文转专业指南，人文大类分流方向包括历史、哲学、新闻传播、汉语国际教育
- 据2025年个人汉文转专业指南，2025年汉语言文学回到人文大类分流，指南估计约有五个名额

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人整理的转AI风险材料，数学分析每周5学时
- 据个人整理的转AI风险材料，高等代数5学时
- 据个人整理的转AI风险材料，程序设计基础6学时
- 据个人整理的转AI风险材料，离散数学4学时
- 据个人整理的转AI风险材料，课表很满

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据以2023级培养方案为背景的个人南赫手册，基础课
- 据以2023级培养方案为背景的个人南赫手册，概论课
- 据以2023级培养方案为背景的个人南赫手册，专业核心
- 据以2023级培养方案为背景的个人南赫手册，地球系统学科交叉模块
- 据以2023级培养方案为背景的个人南赫手册，自选模块
- 据以2023级培养方案为背景的个人南赫手册，专业选修

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
Current reviewStatus: CONFIRMED

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
- 2026准入计划中2024级软件工程条目：微积分I、微积分II、线性代数须为第一层次并取得学分
- 2026准入计划中2024级软件工程条目：计算系统基础、C语言程序设计基础、软件工程与计算I、离散数学中至少两门取得学分

Candidate Evidence Documents:
- Document ID: 6; Title: 南京大学2026年全日制本科生跨大类（学院）专业准入计划及实施方案一览表; sourceType: OFFICIAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE, WRONG_SOURCE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [6] (no auto-mapped current IDs)
Reference answer required: YES — COMPOUND, POLICY
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — FACT, YEAR_SCOPED
Current reviewStatus: CONFIRMED

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
- 据以2025级经验为主的个人技科指南，大一鼓楼校区
- 据以2025级经验为主的个人技科指南，大二起苏州校区

Candidate Evidence Documents:
- Document ID: 19; Title: 南京大学技术科学试验班新生生存指南; sourceType: COMMUNITY; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [19] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人地学大类生存指南，大气17人
- 据个人地学大类生存指南，环境29人
- 据个人地学大类生存指南，地科13人
- 据个人地学大类生存指南，地海24人
- 据个人地学大类生存指南，转出39人

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据个人地学大类生存指南，需要一层次数学
- 据个人地学大类生存指南，微积分I一层次
- 据个人地学大类生存指南，微积分II一层次
- 据个人地学大类生存指南，线性代数

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2025级个人数理大类指南，解析几何课后题和考前突击可高分
- 据2025级个人数理大类指南，数学分析应最大投入并打牢课本例题作业
- 据2025级个人数理大类指南，高代重基础且赋分，若求后续学习也要打牢

Candidate Evidence Documents:
- Document ID: 待当前库核验（历史 ID 不沿用）; Title: 数理大类生存指北; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据更新至2025年的个人法学转专业指南，法理学导论
- 据更新至2025年的个人法学转专业指南，民法学总则
- 据更新至2025年的个人法学转专业指南，刑法学总论一
- 据更新至2025年的个人法学转专业指南，宪法学

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据更新至2025年的个人法学转专业指南，多数人笔试选择民法和刑法
- 据更新至2025年的个人法学转专业指南，大一下是重要阶段
- 据更新至2025年的个人法学转专业指南，需结合教材笔记和往年题全面备考

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL, WRONG
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, FALLBACK
Current reviewStatus: CONFIRMED

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
- (none; human review required)

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, COLLOQUIAL
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，微积分I一层次
- 据2026年个人转电子指南，大学物理I
- 据2026年个人转电子指南，电路分析
- 据2026年个人转电子指南，微积分II一层次
- 据2026年个人转电子指南，模拟电路
- 据2026年个人转电子指南，大学物理II
- 据2026年个人转电子指南，均不低于70分

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PASS, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: NO
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，面试包括限时一分钟自我陈述，随后老师轮流提问
- 据2026年个人转电子指南，提问可能涉及大学物理、电路分析、模拟电路、C语言，也可能根据陈述追问

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): PARTIAL, REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, FALLBACK
Current reviewStatus: CONFIRMED

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
- 据2026年个人转电子指南，六门准入课均70分以上
- 据2026年个人转电子指南，面试1分钟陈述加提问
- 据2026年个人转电子指南，可能问大物、电分、模电、C语言

Candidate Evidence Documents:
- Document ID: 14; Title: 转电子指北2026版; sourceType: PERSONAL; why relevant: 历史评测中曾引用与该题 sourceFile 同名/同标题的资料；请在当前 Document 列表核验角色、ID 与原文。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [14] (no auto-mapped current IDs)
Reference answer required: YES — COMPOUND, POLICY, EXPERIENCE
Current reviewStatus: CONFIRMED

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
- 据以2023级培养方案为背景的个人南赫手册，总学分以25以内为宜
- 据以2023级培养方案为背景的个人南赫手册，最多30学分
- 据以2023级培养方案为背景的个人南赫手册，考试课程10门以内

Candidate Evidence Documents:
- 未找到可安全映射的历史来源引用；按 sourceFile 与当前 Evidence 文档标题查找。
Historical outcome (not current benchmark result): REFUSAL_FALSE_NEGATIVE
Suggested answerable: true (review required)
Suggested expectedDocumentIds: [] (no auto-mapped current IDs)
Reference answer required: YES — EXPERIENCE, DEPARTMENT_SCOPED
Current reviewStatus: CONFIRMED

Human Decision:
answerable:
expectedDocumentIds:
expectedFactsStatus:
referenceAnswer:
reviewStatus: NEEDS_REVIEW
