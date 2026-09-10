# TransferRAG 实习冻结最终回归报告

## 1. 评测与恢复状态

- 固定 Ground Truth：`evaluation/test-cases.json`，共 82 题；本轮未修改问题、`expectedFacts` 或证据。
- 正式接口：`POST /api/rag/ask`；诊断结果保存 `/api/test/search?topK=10`。
- 82 条记录均为 `executionComplete=true`，caseId 唯一，JSON 可完整解析，HTTP 与解析错误为 0。
- 中断恢复时结果文件实际已经完成 82/82，因此没有重复调用任何 case。
- Maven：110 tests，0 failures，0 errors，0 skipped。
- Spring Boot 已使用恢复后的 V1 Answerability 正常启动，并以 `ddl-auto=validate` 避免启动时改写 schema。
- doc6、13、14、19、25、28、29 的 MySQL/Qdrant consistency 全部为 `OK`：140 个 MySQL Chunk 对应 140 个 Qdrant points，0 issue；未执行 reindex。

## 2. 总体结果

| 状态 | Baseline | 上一正式版 | 当前 | 相比上一正式版 |
|---|---:|---:|---:|---:|
| PASS | 36 | 51 | 51 | 0 |
| REFUSAL_FALSE_NEGATIVE | 38 | 24 | 21 | -3 |
| PARTIAL | 4 | 7 | 9 | +2 |
| WRONG | 1 | 0 | 0 | 0 |
| WRONG_SOURCE | 1 | 0 | 1 | +1 |
| YEAR_MISMATCH | 2 | 0 | 0 | 0 |
| DEPARTMENT_MISMATCH | 0 | 0 | 0 | 0 |
| HALLUCINATION | 0 | 0 | 0 | 0 |
| SYSTEM_ERROR | 0 | 0 | 0 | 0 |

- 当前 PASS：**51 / 82（62.20%）**。
- 相比 baseline 36 / 82（43.90%），增加 15 个 PASS，通过率提高 18.30 个百分点。
- 相比上一正式版 51 / 82（62.20%），PASS 数和通过率不变。
- 平均 `/api/rag/ask` 延迟约 8191 ms，中位延迟约 7880 ms。

## 3. 回归与改善

原 baseline 的 36 个 PASS 中保留 33 个。当前 baseline PASS regression 为：

- `TRAG-006`：`REFUSAL_FALSE_NEGATIVE`
- `TRAG-008`：`REFUSAL_FALSE_NEGATIVE`
- `TRAG-071`：`REFUSAL_FALSE_NEGATIVE`

相对 baseline，有 18 个原非 PASS 转为 PASS：

`TRAG-014、015、018、019、020、024、026、028、034、041、048、049、061、067、068、072、074、075`。

相对上一正式版：

- PASS regression 4 个：`TRAG-016、TRAG-029、TRAG-064、TRAG-071`。
- 非 PASS 转 PASS 4 个：`TRAG-020、TRAG-026、TRAG-048、TRAG-082`。
- 净 PASS 数不变，但具体案例发生了由 LLM 判断波动和生成完整性造成的交换。

## 4. 当前非 PASS

### REFUSAL_FALSE_NEGATIVE（21）

`TRAG-003、005、006、007、008、009、023、027、031、036、037、038、042、046、060、062、064、071、073、077、080`

### PARTIAL（9）

`TRAG-016、025、029、032、043、045、056、070、076`

其中：

- `TRAG-016` 遗漏“无违纪行为”。
- `TRAG-025、029` 回答了某一 cohort 的课程规则，但没有说明适用 cohort；Top10 同时存在另一套不同规则。
- `TRAG-043` 遗漏提前修读线性代数建议。
- `TRAG-045` 遗漏工科试验班自带一层次微积分。
- `TRAG-032、056、070、076` 分别遗漏五门大类课、往年题、3到4分钟提问和鼓楼开设信息。

### WRONG_SOURCE（1）

`TRAG-066` 的核心 Ground Truth 由 doc13/chunk88 直接支持：学院重视数学物理能力、面试官会看第一学期成绩，并可能追问第二学期期中成绩。回答同时引用 doc13/chunk87，将“26级光材二次拔尖”的光学题、高考及竞赛考量混入大一转专业面试，发生跨选拔场景污染。该问题不是学院或年份 metadata 错误，而是来源范围与生成阶段没有保持场景边界。

## 5. 根因统计

| 根因 | 数量 | caseId |
|---|---:|---|
| Generation completeness | 7 | TRAG-016、032、043、045、056、070、076 |
| Answerability | 5 | TRAG-008、009、037、060、064 |
| Retrieval | 4 | TRAG-005、036、042、080 |
| Source Authority | 4 | TRAG-006、007、073、077 |
| Evidence Window | 3 | TRAG-003、031、062 |
| Answerability / Cohort ambiguity | 2 | TRAG-023、027 |
| Entity Resolution | 2 | TRAG-038、046 |
| Year / Cohort qualification | 2 | TRAG-025、029 |
| Answerability / Nondeterministic | 1 | TRAG-071 |
| Source scope / Generation | 1 | TRAG-066 |

## 6. 按类别统计

| 类别 | 测试数 | PASS | Pass Rate |
|---|---:|---:|---:|
| 别名 | 1 | 1 | 100.00% |
| 风险经验 | 1 | 1 | 100.00% |
| 经验 | 5 | 1 | 20.00% |
| 课程规划 | 15 | 8 | 53.33% |
| 面试/机试 | 12 | 7 | 58.33% |
| 明确事实 | 8 | 7 | 87.50% |
| 培养方案 | 4 | 3 | 75.00% |
| 时间敏感 | 1 | 1 | 100.00% |
| 条件 | 6 | 3 | 50.00% |
| 学习经验 | 7 | 5 | 71.43% |
| 转专业政策 | 16 | 10 | 62.50% |
| 综合生存指南 | 4 | 4 | 100.00% |
| 组合问题 | 2 | 0 | 0.00% |

## 7. 验收结论

恢复后的 V1 Answerability 在总体 PASS 数上与上一正式版一致，Metadata、Entity Resolution、Year/Cohort 和索引一致性没有结构性退化。但本次新增 `TRAG-066 = WRONG_SOURCE`，不满足 `WRONG_SOURCE = 0` 的冻结底线。

因此本次验收状态为：**未达到 INTERNSHIP_FREEZE**。保留当前结果并停止，不启动新的 RAG iteration，也不在本轮继续修改代码。
