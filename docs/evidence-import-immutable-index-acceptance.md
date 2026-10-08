# Evidence 不可变索引快照：本地加固验收

日期：2026-10-09。仅本地代码、隔离容器、文档和候选产物；没有生产连接、迁移、部署、commit 或 push。保留原有全部未提交文件及三个 Benchmark review 文件，未修改 Gold。

## 1. 实现与不变量

- `EvidenceChunkPersistenceService.persistFirst`：合法 owner/generation、NEW_EVIDENCE、EVIDENCE 角色以及无既有 Chunk/快照的前提下，一次短事务保存 Chunk、不可变输入和 INDEXING 检查点。失败整体回滚。
- 新增 `EvidenceIndexSnapshot` / Repository / `EvidenceSnapshotStateService` / `EvidenceSnapshotCodec` / `FrozenEvidenceIndexer`；新增第三份 `evidence-index-snapshot-schema.sql`。
- 输入包含格式版本、collection、模型名、确定性 Point UUID、Chunk 文本和原有完整索引 metadata（Document/Chunk ID、序号、role、来源类型、年份、学院、专业、section、scope 等）。输入 JSON 及 SHA-256 与 Chunk 一起持久化，不在重试中重新构造。
- 首次 Embedding 在数据库事务外完成；只有当前 owner/generation 可以在短事务内登记首次完整结果。精确 Point protobuf（向量、payload、UUID）保存为 Base64 JSON 及独立 SHA-256；模型名、维度和 collection 一起保存。登记成功前没有任何 Qdrant 写入。
- 完成向量冻结后，重试/接管不再 Embedding、不读取可变 metadata，只重放冻结 Point；64 Point 一批，无先删除。模型名或 collection 改变、快照缺失/损坏、非法向量都会失败关闭，不静默重建快照。
- 输入或 Point JSON 超过 16 MiB 拒绝，不截断；超限资料需要管理员核查。不存在快照但已在 INDEXING 的旧任务需要人工审查。
- 首次向量结果未持久化便崩溃时，该未保存结果只能丢弃；恢复可以从已冻结文本重新计算，此时此前没有 Point 写出。并发旧计算结果若失去所有权，不得登记、更不得写 Qdrant。
- `EvidenceTaskProcessor` 新任务索引只调用 FrozenEvidenceIndexer；Legacy 仍使用旧 snapshot 方法做只读核验。
- 服务层 `checkIndexMutation` 拒绝 Evidence 手工重新切分、删除索引及重新索引，包括无 Task 的 Legacy，消除任务登记竞争窗口。移除旧公开 raw upsert helper。Canonical 原链路与 compensation 保留。
- Readiness Gate、稳定 UUID、owner/generation、租约、队列和年份过滤不变。Task SUCCEEDED 前不可进入 V1/V2 Evidence 上下文。

这是受控请求的**相同内容重放**，不是 Qdrant 外部版本 fencing、exactly-once 或 MySQL/Qdrant 原子事务。不能保护直接修改数据库/快照/Point、其他程序伪造同 UUID、混跑旧 Worker、未来未设计的资料编辑流程。

## 2. 最终测试结果

最终完整源码运行 `mvnw.cmd -q package`：**303 项，300 PASS，0 FAIL/ERROR，3 SKIP**，打包成功。

| 验收 | PASS | FAIL | SKIP | 实际边界 |
|---|---:|---:|---:|---|
| ImportTaskMySqlIntegrationTest | 18 | 0 | 0 | MySQL 8.0.44 随机端口隔离库 |
| EvidenceQdrantIntegrationTest | 2 | 0 | 0 | Qdrant 1.18.0 raw-client 对照，证明幂等及无外部 fencing |
| FrozenEvidenceIntegrationTest | 4 | 0 | 0 | 真实 MySQL + Qdrant，延迟请求/竞争快照/重试/完整性 |
| EvidenceJvmRestartIntegrationTest | 1 | 0 | 0 | 强制终止真实子 JVM，再启动另一个进程恢复 |
| Canonical 回归 | 31 | 0 | 0 | 原 controller/import/编排测试 |
| RagService / RetrievalService 回归 | 30 | 0 | 0 | 原检索与问答回归 |
| Source Reader | 29 | 0 | 1 | 原服务/Controller；Windows 下 symlink 用例未执行 |
| 管理端 npm test | 6 | 0 | 0 | 任务轮询；build PASS |

分类有交叉，不能把上表加总为 Maven 总数。另两项 SKIP 为原 opt-in Benchmark Trace，未启用、未运行 Benchmark。原 H2 contextLoads 仅用于应用装配，不计为 MySQL 集成证明。

### 延迟写入的真实时序

1. A 持久化精确 Point，然后向本地网络代理发送真实 HTTP protobuf 请求；代理接收后暂缓转发。
2. 在隔离 MySQL 中模拟 A 租约过期，B 通过真实 StateService 恢复领取。
3. 测试主动改变隔离 Document metadata/Chunk 文本，证明 B 仍使用原始输入和已冻结向量，而非重新读取这些值。
4. B 向真实 Qdrant 写入并标记 SUCCEEDED，门禁允许该 Document。
5. 放行 A 的网络请求，代理将精确请求转发到真实 Qdrant；最终 Point 数只增加预期的 1，Chunk ID 不变，payload 精确相同、向量数值相同；A 无法覆盖任务最终状态。
6. 独立竞争测试让 A 的 Embedding 延迟并返回不同向量；B 完成首次冻结后，A 的陈旧结果被 owner/generation 拒绝，冻结 hash 不变。

延迟代理是测试用网络设施，不加入应用，也不是生产 gRPC fencing。Embedding 使用确定性测试模型，不调用任何生产 AI provider；Qdrant 写入/读取以及数据库状态是真实的。

### JVM 崩溃恢复

实际 Worker/Dispatcher 在 JVM A 完成输入/向量冻结并写入一个 Point 后阻塞；父测试仅强制终止自己创建的进程。JVM B 使用 Hibernate validate，等待真实租约过期后恢复。B 的测试 Embedding 若被调用会直接失败，实际恢复 PASS。

核验 Input/Point hash、Chunk ID、PROCESSING/SUCCEEDED 门禁、最终每个 Point 的 payload 和向量。Qdrant Cosine 会归一化存储，因此该用例按数学归一化结果、1e-6 浮点容差比较；不是将不同 protobuf 类型直接作对象相等比较。

## 3. Migration 与默认关闭兼容

顺序：task schema -> readiness schema -> index snapshot schema -> 结构/UNKNOWN 核验 -> 新应用 validate。

新增隔离测试从本地旧实体定义重建完整 document/chunk/evidence_ref/entity_alias 表，使用人工测试行；三份 SQL 执行两次，行数不减、不自动登记任务；新实体 Hibernate validate 通过；Legacy 无任务仍可见；snapshot FK 拒绝不存在的 taskId。

UNKNOWN 旧任务仍默认拒绝，即使 status=SUCCEEDED，不按 stage 推断。默认 tasks.enabled=false 的装配测试证明 Dispatcher 不存在，Readiness/PublicSource Bean 正常。Canonical、RAG、Source Reader 独立回归通过。

这不是生产 information_schema 漂移核验，不复制生产数据。真正发布前仍需批准只读结构比对。

## 4. 候选产物与安全回退

工作区仍未提交，HEAD `bd1b2598c547951ec051f4ef80d0f674755a8900` 不含本次实现，不得用它标识发布代码。

发现根目录 Maven 打包会包含被 Git 忽略的 application-local.properties。未读取/输出其内容；该工作区 JAR **不可发布**，没有删除或改变它及本地配置。

另建全新本地 staging，仅复制 `git ls-files --cached --others --exclude-standard` 选择的 src、pom、wrapper；不复制 ignored 配置、target、数据库或上传资料。候选 JAR 必须核验没有 application-local.properties，并绑定源码指纹及 JAR SHA-256。其具体 hash/路径见下面产物记录。

安全降级首选同一加固 JAR + tasks.enabled=false，保留门禁和三份 additive schema，停止并确认所有旧执行进程退出，暂停所有管理写入/retry，保留 Task/快照/Chunk/Point。不能恢复无门禁旧 JAR，也不能启用无快照旧 Worker。若本加固 JAR 本身不可用且没有经过同等验收的门禁兼容备用版本，则保持维护停机，不能假称具备自动跨版本回滚。

### 实际候选产物记录

- staging：`C:\Users\yu205\AppData\Local\Temp\nju-evidence-release-36da338dad354d97ac3b8fa33bb1ec3f`
- jar：该目录下 `target\Transfer-RAG-0.0.1-SNAPSHOT.jar`
- 源码集合 SHA-256：`34F1D44171B28FC721DE019D23560094A735DFBFB9F09AD4AC304E23D592B176`
- JAR SHA-256：`94B227FB1EE1130780E8E4E4049B128F993A10A0AB48182EC4579F1793C53EE2`
- `LocalConfigCopied=false`，ZIP `LocalConfigPackaged=false`；隔离 staging package 成功。
- 此产物是未提交工作区的受控候选，不是已批准 release commit，也未复制到服务器。临时目录不能充当长期生产备份；获得提交授权后仍需从固定 commit 的干净 staging 重新构建、核验 hash、登记正式回退产物。

提交/推送、生产配置/结构检查、备份恢复演练、维护窗口及测试 Evidence 导入均需下一轮单独批准。因此本地外部写风险受控，但正式发布准备仍未完成。

## 5. 本地复现

```powershell
$env:JAVA_HOME = 'C:\Users\yu205\.jdks\ms-21.0.11'
.\mvnw.cmd '-Dtest=ImportTaskMySqlIntegrationTest,EvidenceQdrantIntegrationTest,FrozenEvidenceIntegrationTest,EvidenceJvmRestartIntegrationTest' test
.\mvnw.cmd package
Set-Location transfer-rag-admin
npm test
npm run build
```

以上命令只供本地复现；生产服务器不能使用该容器测试方式或直接发布工作区 JAR。

`EXTERNAL_WRITE_RISK_CONTROLLED=true`（仅限当前受控新 Evidence 路径）

`INTEGRATION_TESTS_PASSED=true`

`LOCAL_ACCEPTANCE_COMPLETE=true`

`PRODUCTION_RELEASE_READY=false`

`PRODUCTION_DEPLOYED=false`
