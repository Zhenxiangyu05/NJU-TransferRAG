# Evidence 异步导入：隔离验收证据与检索可见性

本轮仅本地测试/源码检查，未连接生产、未执行生产 SQL、未部署、未提交或推送。

## A. 实际测试结果

执行环境：Java 21，Windows / PowerShell。

```powershell
$env:JAVA_HOME = 'C:\Users\yu205\.jdks\ms-21.0.11'
.\mvnw.cmd '-Dtest=ImportTaskMySqlIntegrationTest,EvidenceQdrantIntegrationTest,IncompleteEvidenceVisibilityCharacterizationTest,Canonical*Test,RagServiceTest,RetrievalServiceTest,EvidenceIndexIdempotencyTest,ImportTaskDispatcherTest' test
```

选定用例最终合计：88 个，PASS 72，FAIL/ERROR 0，SKIP 16。Qdrant 新增用例后另外执行了 MySQL/Qdrant 测试以刷新结果。

| 测试 | 总数 | PASS | SKIP |
|---|---:|---:|---:|
| ImportTaskMySqlIntegrationTest | 14 | 0 | 14 |
| EvidenceQdrantIntegrationTest | 2 | 0 | 2 |
| IncompleteEvidenceVisibilityCharacterizationTest | 8 | 8 | 0 |
| CanonicalImportControllerTest / CanonicalImportServiceTest / CanonicalFirstRagServiceTest | 30 | 30 | 0 |
| RagServiceTest / RetrievalServiceTest | 30 | 30 | 0 |
| EvidenceIndexIdempotencyTest / ImportTaskDispatcherTest | 4 | 4 | 0 |

首轮新增测试曾因 Mockito similaritySearch 重载匹配发生编译失败，已限定 SearchRequest 类型并复跑成功。

Docker 检查失败：`npipe:////./pipe/dockerDesktopLinuxEngine` 不存在，Testcontainers 找不到可用 Docker 环境。
本轮未启动 Docker Desktop，也未回退到 H2、独立非隔离数据库或生产连接。
**Maven 返回成功不代表跳过的集成测试通过。**

## B. MySQL 真实事务/并发

现有 14 项真实 MySQL 8 测试均 SKIP，尚未验证：

- file_hash/document_id 唯一键及并发登记竞争；
- Document + Task 原子登记与回滚；
- 并发领取、Chunk + INDEXING 检查点回滚；
- 失败重试、租约失效/旧 owner 拒绝写入；
- Legacy 核验任务与 Canonical 保护。

测试 JDBC 地址只由 Testcontainers 提供，没有生产/H2 fallback。

## C. Qdrant

两个隔离用例均 SKIP：

1. 实际稳定 ID 重复 upsert、部分前缀写入后补齐、保留其他文档 Point、Embedding 失败不删除存量。
2. 新快照 upsert 后，再提交同 ID 旧快照，读取 payload 证明当前入口没有外部写入 fencing。这个用例是风险刻画，不是安全修复。

现有内存模拟的幂等测试通过，只能证明代码调用/ID 设计，不能替代真实 Qdrant 行为。

## D. 真实 Java 进程终止/恢复

**NOT RUN / BLOCKED**：Docker 不可用，无法建立要求的持久隔离 MySQL 环境。
现有 simulatedWorkerRestartRecoversCommittedIndexCheckpoint 只是同 JVM 重建 Worker，不能作为真实重启验收。
本轮没有杀死用户的任何 Java 进程，未启动新的 8080 服务。

Docker 恢复后，独立进程演练方案（尚未实现/运行子进程 harness）：

1. 父测试启动隔离 MySQL 8 + Qdrant；测试连接仅传给测试子进程，不读 application-local/生产 env。
2. 子 JVM A 使用实际 ImportTaskStateService/Dispatcher/Worker；测试专用确定性 Embedding，不请求远程模型。
3. 使用合成测试文件提交任务；分别在 PARSING、已提交 INDEXING、Qdrant 仅写入前缀三处设置测试屏障。
4. 父进程确认持久化阶段后，仅对自己启动且记录的 JVM A PID 执行强制终止；两个容器继续运行。
5. 启动 PID 不同的 JVM B，JPA 使用 validate 而非 create/drop；等待真实租约到期，不依靠 Mockito 或手工 SQL 改过期时间。
6. 验证 B 扫描并恢复原任务：owner/generation 改变，attempt 增加；未提交解析重新执行，已提交 Chunk ID 不变；Point ID/数量不重复，最终 SUCCEEDED。
7. 同时验证登记已提交但尚未调度时崩溃的窗口。每个屏障场景独立测试，超时显式 FAIL。

## E. 检索可见性：P0

源码位置：

- RetrievalService.executeSearch / executePrecomputed（约 322/339 行）：映射 Point 后只按 DocumentRole 过滤。
- RetrievalService.buildMetadataFilter（约 368 行）：department/year/cohort/role，不含任务完成状态。
- RagService.answerFromEvidence（约 207 行）：直接把候选内容送入 Answerability；通过后进入 final generation。
- RagService.loadDocuments（约 572 行）：批量补充 Document metadata，不查 ImportTask。
- EvidenceTaskProcessor.process（约 37 行）与 VectorIndexService.upsertEvidenceSnapshot（约 57 行）：先进行外部 add，返回后 Worker 才提交 SUCCEEDED。

结论：

| 场景 | 当前行为 |
|---|---|
| PROCESSING 且已写入部分 Point | 可能参与检索、进入 Answerability，若获批准则进入最终生成 |
| FAILED 且仍有部分 Point | 同上，不会因失败自动屏蔽 |
| Legacy 没有 ImportTask | 继续参与现有检索 |
| 旧 Worker 外部请求延迟完成 | DB fencing 不保护 Qdrant；同 ID 外部 upsert 存在覆盖风险 |

8 个新增**单元**刻画用例覆盖 V1 / V2 fallback，各自包含 PROCESSING、FAILED、LEGACY、SUCCEEDED。
使用真实 RetrievalService/RagService，VectorStore/Repository/LLM 为替身；Task 状态是测试夹具，未写真实数据库。
断言测试标记进入 Answerability 与 generation Prompt。PASS 表示成功复现缺口，**不是隔离数据库可见性验收通过**。

数据库 + Qdrant 可复现方案（Docker 恢复后）：

1. 隔离库原子登记新 Evidence，领取、提交 Chunk 检查点。
2. 只 upsert 第一条 Point，保持任务 PROCESSING。
3. 使用实际 RetrievalService + Qdrant search，固定本地 Embedding；记录返回 documentId，以及截获的两类 LLM context。
4. 使用实际 state.fail() 改为 FAILED，再次验证；不能用 Document.status 代替 Task.status。
5. 加入一个没有 Task 的 Legacy Document 作对照。
6. 批准修复后将 PROCESSING/FAILED 的预期改为不可见，Legacy/SUCCEEDED 可见；再测试所有过滤后空结果不调用生成。

### 最小修复建议（本轮未实施）

新增共享的数据库 readiness gate，批量查询候选 Evidence documentId 的任务状态：

- 无任务且 Document 存在：Legacy 兼容；
- 新导入任务 SUCCEEDED：可见；
- 新导入任务 QUEUED/PROCESSING/FAILED：不可见；
- Document 不存在或状态查询失败：fail closed，不默认放行。

对 V1 搜索和 V2 Evidence fallback 共用门禁，在候选排序/裁剪及 Answerability context 构建前执行；避免只在 SourceCard/Citation 层隐藏。
使用现有候选预算，不为改善结果调 TopK/threshold。候选全部被滤除时允许返回资料不足；隐藏候选占据 topK 的召回损失单独记录。
未完成修复不得启用生产异步导入。

Legacy 重新提交会产生 VERIFY_EXISTING 任务，因此不能机械地把“所有非 SUCCEEDED Task”当作新导入：
应明确保留既有 Legacy 发布语义，核验任务不应仅因排队临时下架已有资料；核验发现真实不一致后的隔离策略另行批准。
必须使用明确且不可变的任务来源/发布分类，不允许用可变 VERIFY_EXISTING 阶段推断来源。

## F. 上轮遗留风险（本轮修复进展见文末）

- **P0**：未完成/失败的新导入 Point 进入问答上下文。共享 readiness gate 是最小修复方向。
- **P1**：MySQL/Qdrant 真实测试及真实 JVM 重启尚未验收，阻止生产发布。
- **P1**：ChunkRepository.findMaxEffectiveYear* 未排除未发布 Chunk，可影响默认检索年份；门禁方案应覆盖年份统计，不只过滤最终 context。
- **P1**：稳定 ID 只防重复，不防旧外部写覆盖。当前重试复用不变 Chunk，通常是相同内容重复写；若未来允许改内容/metadata或模型漂移，不能视作已有版本保护。最小措施是保持索引输入不可变、禁止任务中编辑；更强代际发布需独立批准。
- **P1**：旧调用忽略线程中断时，本实例 Worker 可能长期被占用；不允许仅凭租约过期强行再开本地同文档执行者。

## G. 生产迁移 / 回滚检查清单（未执行）

### 迁移前

- Docker 恢复后全部隔离测试 PASS，包括真实进程演练；P0 readiness 问题修复验收。
- 独立生产授权、维护窗口、数据库可恢复备份、当前 JAR/前端/配置备份。
- 核验 Document.file_hash 唯一键、Document FK 类型及当前 schema 与 Hibernate validate 兼容性。
- 记录现有 Document/Chunk/EvidenceRef/Qdrant 数量；不重导、不 backfill、不清数据。

### 迁移与切换

- 仅执行增量 import_task SQL；重复执行后核对字段、唯一键、扫描索引及 RESTRICT FK。IF NOT EXISTS 不是 schema 修复机制。
- 保持 ddl-auto=validate；检查外部配置没有覆盖为 update/create。
- 新代码默认任务开关 false；在数据库和新管理端适配验证后再单独批准启用。
- 检查 loopback 后端、SSH tunnel + Basic Auth 管理接口、公网任务 API 拒绝访问。

### 回滚

- 先停接收新 Evidence 导入；单纯把调度开关关掉不等于正在执行的 Worker 已退出。
- 明确终止/等待所有新版本执行者，避免遗留 Qdrant 网络写入；保留任务、Chunk、Point 和新增表。
- 若启用后已经生成任务/部分 Point，不可直接回滚到没有 readiness gate 的旧 JAR：旧检索可能再次暴露未完成资料。须准备含门禁的兼容回滚版本或经批准暂停问答。
- 不 DROP/TRUNCATE import_task，不删除 Chunk/Point，不清 Docker volume；保留检查点供恢复。
- 再次核对旧业务可用性、数据数量和管理员保护；不要把应用回滚描述成数据自动回滚。

## H. 本轮 P0 Readiness Gate 本地修复

新增 `ImportTaskKind`、`DocumentReadinessView`、`EvidenceReadinessService` 和独立 additive migration
`src/main/resources/sql/evidence-readiness-schema.sql`。原始任务来源登记一次，JPA 不更新该字段；
既有任务迁移为 UNKNOWN，拒绝访问且等待独立来源核验，不根据 mutable stage 进行推断。

V1 / V2 Evidence fallback 在排序裁剪前批量过滤；RagService 在进入 Answerability 之前复核，
Canonical 引用未发布 Evidence 时不生成该 Citation/context。DB 故障 fail-closed。
默认年份两项 JPQL 聚合也排除未发布的新 Evidence，不移除 Legacy 核验中的资料。

旧的 8 项 leak reproducer 已改成修复断言，涵盖新任务状态、Legacy、Legacy verification、
UNKNOWN 及 DB 失败；另补批量查询、bounded over-fetch、Canonical provenance 和稳定快照测试。
MySQL 集成测试新增真实 readiness/年份状态转换断言，并复核独立 migration 可重复执行。

Docker pipe 仍不可用：MySQL/Qdrant 集成测试保持 SKIP；不以现有 H2 contextLoads 或 Mockito
代替真实集成验收。真实 JVM kill/restart 仍未验收。上轮测试数量为历史证据，本轮最终数量以
`target/surefire-reports/TEST-*.xml` 为准。

剩余边界：Qdrant 部分 Point 仍存在，但在本地门禁下不进入 RAG；不具备外部写入 fencing。
重试复用 Chunk 与索引 metadata 的测试不等价于跨库强一致，也不保证模型漂移或未来编辑时
旧写入不会覆盖。候选 over-fetch 上限 256，不保证预算以外的 exhaustive recall。

后续生产步骤必须独立批准：备份 -> import_task schema -> readiness schema -> 核验 UNKNOWN
任务来源 -> Hibernate validate -> 隔离集成/JVM 重启验收 -> 新代码切换。此次全部未执行。

### 本轮最终可执行验收结果

- Java 21 `mvnw.cmd -q -DskipTests compile`：PASS。
- 全量 `mvnw.cmd -q test`：295 项，274 PASS，0 FAIL/ERROR，21 SKIP。
- Readiness unit：24 PASS（修复原 leak reproducer 18 项 + 门禁服务 4 项 + V1/V2 over-fetch 2 项）。
- Canonical：31 PASS（导入 controller 1、导入 service 7、Canonical-first 23，含新增未发布 provenance 拒绝）。
- RagService / RetrievalService 原回归：30 PASS。管理端 `npm test`：6 PASS，`npm run build`：PASS。
- MySQL 8：16 SKIP；Qdrant：2 SKIP；另外 3 项为原有 opt-in 测试。Docker Desktop Linux pipe 不存在。
- 年份 aggregate 的真实 MySQL 行为在上述 16 项中等待 Docker；现有 H2 contextLoads 只验证应用装配/查询解析，非 MySQL 集成验收。
- 真实 JVM kill/restart：未执行。未 commit、push、SSH、生产 migration 或部署。

### Docker 环境复核与再次验收（2026-10-08 23:19，本地）

- Docker CLI 29.7.2 已安装，当前 context 为 desktop-linux；default 和 desktop-linux
  的本地 named pipe 均不存在。未切换 context，也未使用远程引擎。
- WSLService 正常运行；Ubuntu-24.04 和 docker-desktop 均为 WSL2，状态 Stopped。
- 正常执行 `docker desktop start --timeout 45`，返回
  `Failed to start Docker Desktop / Docker Desktop is still starting: context deadline exceeded`。
  Desktop/backend 进程已启动，但引擎仍未就绪。未 reset、prune、unregister、清理容器或 Volume。
  后端日志确认存在 timeout 标记，但不足以确认更深层启动根因。
- 定向运行 MySQL/Qdrant 集成测试：Testcontainers 2.0.5 报
  `Could not find a valid Docker environment`；MySQL 16 SKIP、Qdrant 2 SKIP，实际执行 0。
- 按 Docker 不可用边界停止集成及真实 JVM kill/restart 演练；未创建替代数据库或连接生产。
- 再次全量 Maven：295 项，274 PASS、0 FAIL/ERROR、21 SKIP；其中 Canonical 31 PASS，
  RAG/Retrieval 30 PASS，Readiness 24 PASS。原有 H2 contextLoads 不计作真实集成验收。
- UNKNOWN 默认拒绝规则及迁移 SQL 已源码复核，相关 unit PASS；真实 MySQL 迁移兼容性仍待验收。
- 当前重试路径复用持久化 Chunk，重复登记不覆盖 Document metadata，手工 Chunk/Index 接口
  被任务互斥阻止。因此在这些受控路径且模型不变时，重试输入可保持一致；但快照不是持久化
  payload 摘要/版本锁，Document/Chunk 字段仍可被绕过应用的操作修改，模型输出也非不可变。
  不存在外部 fencing，不能把上述路径约束解释为所有写入场景已安全。
- `LOCAL_ACCEPTANCE_COMPLETE=false`。阻塞：本地 Docker 引擎恢复、18 项真实集成执行、
  UNKNOWN 真实迁移场景、真实 JVM 终止恢复；生产迁移/部署仍需后续独立授权。
- 本轮仅更新本验收记录，无业务代码修改、commit/push、Gold 修改或生产操作。

### Docker 恢复后的最终隔离验收（2026-10-08 23:36）

本节为最新结果，替代此前 Docker 阻塞结论。Engine 29.7.2、Testcontainers 2.0.5
均正常连接本地引擎；只创建 Testcontainers 所属的随机端口测试容器，无生产连接。

- 原有 MySQL 8.0.44 16 项全部实际 PASS、0 SKIP；增加旧表迁移场景后为 **17 PASS**。
  覆盖文件唯一约束竞争、Document+Task 原子性、独占领取、Chunk 回滚、解析/索引失败重试、
  租约恢复及陈旧 owner 拒绝、Legacy 核验、Readiness 和默认年份聚合。
- 新增 migrationAddsUnknownToPreexistingCompletedTaskWithoutStageInference 在独立旧 schema
  上执行两份实际 SQL；COMPLETED/SUCCEEDED 任务新增 task_kind 后仍为 UNKNOWN，重复 migration
  不改旧状态或行数。实际 readiness 查询拒绝 UNKNOWN，即使其状态 SUCCEEDED。
- Qdrant 1.18.0：**2 PASS、0 SKIP**。稳定 ID 的部分前缀写入、重复 upsert、Embedding
  失败保留已有 Point 均通过；另一个测试明确证明旧 payload 可以覆盖新 payload，非 fencing PASS。
- 新增 EvidenceJvmRestartIntegrationTest：**1 PASS、0 SKIP**，在隔离 MySQL/Qdrant 上启动
  真正 JVM A/B，使用实际 StateService、EvidenceTaskProcessor、Worker、Dispatcher。
  解析与 Embedding 为固定测试输入，不调用生产 AI；不加载应用配置，不监听 8080。
  A 提交 INDEXING 检查点及首个 Point 后阻塞，父测试仅强制终止其 Process 对象；B 使用
  Hibernate validate 保留 schema，等待真实租约过期后恢复（不通过 SQL 手工改过期时间）。
  最新全量运行 PID **29912 -> 32572**：attempts=2，Chunk ID 保持，Point 从 1 补齐至 2，
  PROCESSING 时门禁拒绝，SUCCEEDED 后允许。子进程都已结束。
- 全量 Maven：**297 项，294 PASS、0 FAIL/ERROR、3 SKIP**。
  3 SKIP 是原有 BenchmarkTraceInterviewTest、BenchmarkTraceSmokeTest、PublicSourceServiceTest
  的 opt-in 场景；本轮 MySQL/Qdrant/JVM 集成均无跳过。
- Canonical 回归 31 PASS，RAG/Retrieval 30 PASS，Readiness unit 24 PASS。
  管理端 npm test 6 PASS，npm run build PASS。git diff --check PASS。
- 既有 SupportPilot 16 个容器和原 qdrant / sleepy_hodgkin 的 ID、停止状态与检查前一致；
  未操作既有 Volume，未运行 prune/compose/reset/生产 migration/deploy。

剩余风险不被此次 PASS 消除：无 Qdrant 外部 fencing，无跨库事务或 exactly-once；受控重试
复用相同 Chunk 输入，但不能保证绕过应用修改 metadata、模型漂移或未来编辑时旧网络写入安全。
候选 over-fetch 上限 256，不保证预算外召回。Legacy 核验失败按既有发布语义保留可见性。

**LOCAL_ACCEPTANCE_COMPLETE=true**（本轮要求的隔离验收），**PRODUCTION_DEPLOYED=false**。
生产仍需独立发布方案及授权：备份、迁移顺序、UNKNOWN 分类核验、validate 配置和安全回滚。
本轮只新增/扩展测试夹具和验收文档，未改生产业务代码，未 commit/push 或修改 Gold。

### 不可变索引加固后的最新验收（2026-10-09）

此前“重试重新读取 metadata / 再次 Embedding”的风险已在新 Evidence Worker 路径改为持久化输入和精确 Point 重放。详见 `evidence-import-immutable-index-acceptance.md`，它是本轮最新验收证据。

- 最终 `mvnw.cmd -q package`：303 项，300 PASS、0 FAIL/ERROR、3 SKIP。
- MySQL 18 PASS；Qdrant raw-client 对照 2 PASS；真实 MySQL/Qdrant 冻结索引联合测试 4 PASS；真实 JVM kill/restart 1 PASS。集成没有跳过。
- Canonical 31 PASS，RAG/Retrieval 30 PASS，Source Reader 29 PASS/1 Windows symlink SKIP，管理端 6 PASS、build PASS。
- 真实延迟请求在 B 成功后才写入 Qdrant，最终 payload/vector 仍匹配冻结快照。另验证不同向量的旧 Embedding 结果不得覆盖快照；重启后禁止再次 Embedding，仍成功恢复。
- 完整旧实体定义 Schema 的隔离升级、三份 Migration 重复执行、Hibernate validate、UNKNOWN 默认拒绝、Legacy 可见性全部通过；没有复制生产数据。
- 删除生产 raw upsert helper，旧手工 Evidence 删除/切分/索引在 Service 层统一拒绝；Legacy 阅读/只读核验不变，Canonical 原同步链路不变。
- 检查发现工作区打包包含 ignored application-local.properties，因此该 JAR 不可发布。另从无 ignored 配置的新 staging 构建候选 JAR，已确认无该文件，记录源码与产物 hash。
- 不存在 Qdrant fencing / exactly-once / 跨库事务；直接改库、改 Point、混跑旧 Worker不受保护。正式部署仍阻塞于版本提交授权、生产结构/配置比对、备份恢复核验及维护窗口批准。

LOCAL_ACCEPTANCE_COMPLETE=true；EXTERNAL_WRITE_RISK_CONTROLLED=true（当前受控新 Evidence 链路）；INTEGRATION_TESTS_PASSED=true；PRODUCTION_RELEASE_READY=false；PRODUCTION_DEPLOYED=false。
