# TransferRAG Baseline vs Iteration 1 — Answerability

## 核心指标

| 指标 | Baseline | Iteration 1 | 变化 |
|---|---:|---:|---:|
| PASS | 36 | 37 | +1 |
| Pass Rate | 43.90% | 45.12% | +1.22 pp |
| REFUSAL_FALSE_NEGATIVE | 38 | 35 | -3 |
| False Refusal Rate | 46.34% | 42.68% | -3.66 pp |
| PARTIAL | 4 | 8 | +4 |
| WRONG | 1 | 0 | -1 |
| WRONG_SOURCE | 1 | 2 | +1 |
| HALLUCINATION | 0 | 0 | +0 |

## 原 16 个 Answerability 失败

- 完全修复为 PASS：3 个（TRAG-020, TRAG-037, TRAG-038）
- 仍非 PASS：13 个（TRAG-003, TRAG-005, TRAG-007, TRAG-009, TRAG-023, TRAG-024, TRAG-026, TRAG-029, TRAG-036, TRAG-046, TRAG-048, TRAG-068, TRAG-080）
- 其中 TRAG-009 从拒答变为 PARTIAL；TRAG-023 从拒答变为 WRONG_SOURCE。两者不计为修复。

## 原 36 个 PASS 的稳定性

- 仍为 PASS：31 / 36
- PASS → FAIL 回归：5 个（TRAG-008, TRAG-022, TRAG-033, TRAG-079, TRAG-082）
- TRAG-008：PASS → REFUSAL_FALSE_NEGATIVE。
- TRAG-022、TRAG-033、TRAG-079、TRAG-082：PASS → PARTIAL，均为关键事实遗漏。

## 新增高风险错误

- 新增 WRONG / WRONG_SOURCE / HALLUCINATION：1 个（TRAG-023）
- 当前总计：WRONG=0，WRONG_SOURCE=2，HALLUCINATION=0。
- TRAG-023 是新增 WRONG_SOURCE：放宽充分性后不再拒答，却采用经验资料并输出与官方 Ground Truth 冲突的规则。

## 结论

本次 V2 让 False Refusal Rate 下降 3.66 个百分点，但 Pass Rate 仅提高 1.22 个百分点；同时出现 5 个原 PASS 回归和 1 个新增 WRONG_SOURCE。说明多来源聚合与列表证据规则能修复少量误拒答，但结构化判定仍不稳定，且放宽后需要更强的来源权威性与关键事实一致性约束。按本轮约定，到此停止，不继续自动优化。
