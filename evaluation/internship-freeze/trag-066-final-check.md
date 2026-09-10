# TRAG-066 最终冻结裁决

## 1. 对比范围

- Question：转光电面试为什么要重视数学和物理成绩？
- expectedFacts：学院重视数学物理能力；面试官会看第一学期成绩；可能追问第二学期期中成绩。
- 上一正式版本：`REFUSAL_FALSE_NEGATIVE`，正确证据已位于 Top2，但最终拒答。
- 本次完整回归：`WRONG_SOURCE`，正确事实得到回答，但混入相邻选拔场景。

## 2. Chunk 场景判断

`doc13/chunk88` 是正确场景。其正文明确位于“大一转专业进光电”的课程与面试经验上下文，直接说明现代工程学院重视数学物理能力、面试官查看第一学期成绩，并会口头询问第二学期期中成绩，完整覆盖三个 expectedFacts。

`doc13/chunk87` 是语义相邻但不适用的场景。该 Chunk 开头和主体描述“26级二次拔尖情况”以及光材二次拔尖面试，包括高中经历、光学选答题、竞赛成绩和高考数学物理成绩。Chunk 后半段才进入“大一转专业进光电”，且因 Chunk 边界原因包含部分跨场景过渡文本。它与问题共享“光电、面试、数学、物理”等词，因此向量相关度更高，但不能用于证明大一转专业面试会考光学题或看高考、竞赛成绩。

## 3. Evidence Window 与污染链路

完整回归以及三次复核中，`chunk87` 均为 Retrieval rank 1，`chunk88` 均为 rank 2；两者同时进入生产 Top3 Evidence Window。

完整回归中的污染由多层共同作用：

1. Retrieval 基于语义相似度将相邻但场景不一致的 `chunk87` 放在 rank 1。
2. Answerability Source Pruning 在该次运行中保留了 `chunk87` 与 `chunk88`，没有排除二次拔尖场景。
3. Generation 随后使用 `chunk87`，把光学题、高考及竞赛考量写入大一转专业面试答案。

Retrieval 使错误场景进入候选，但真正形成 `WRONG_SOURCE` 还需要 Source Pruning 保留该来源并由 Generation 使用，因此不能归为单一层错误。

## 4. 三次复核结果

| 运行 | Evidence Hash | Top3 | 最终 Sources | chunk87 | chunk88 | 语义分类 |
|---|---|---|---|---:|---:|---|
| run1 | `6af01a9be68a08bcbfcc246db59a0cd60e1426b6e39bd5e391b9bfed5616f6d2` | 87、88、432 | 无 | 否 | 否 | `REFUSAL_FALSE_NEGATIVE` |
| run2 | `6af01a9be68a08bcbfcc246db59a0cd60e1426b6e39bd5e391b9bfed5616f6d2` | 87、88、432 | `doc13/chunk88` | 否 | 是 | `PASS` |
| run3 | `6af01a9be68a08bcbfcc246db59a0cd60e1426b6e39bd5e391b9bfed5616f6d2` | 87、88、432 | 无 | 否 | 否 | `REFUSAL_FALSE_NEGATIVE` |

三次 Top10 完全一致，顺序均为：`doc13/chunk87`、`doc13/chunk88`、`doc6/chunk432`、`doc6/chunk433`。三次均未在最终 Sources 中引用 `chunk87`；唯一成功回答仅引用正确的 `chunk88`。

## 5. 最终裁决

三次复核中 `WRONG_SOURCE` 复现次数为 0/3。按照既定冻结标准，本次完整 82 题回归中的 `TRAG-066` 判定为一次性 LLM/source-selection nondeterministic outlier，而不是可稳定复现的 source-scope limitation。

最终项目状态：**INTERNSHIP_FREEZE**。

相邻场景可能同时进入 Evidence Window 的风险已在 `known-limitations.md` 如实保留。本次未修改生产代码、MySQL、Qdrant、索引或 Ground Truth，也未执行其他评测案例。
