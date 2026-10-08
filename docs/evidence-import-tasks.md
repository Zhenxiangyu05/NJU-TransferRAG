# Evidence 持久化异步导入（本地实现，未上线）

## 启用边界

- `app.import-tasks.enabled=false` 为默认值：不启动调度器，不接受新的 Evidence 导入（503）。Canonical 使用原同步链路。
- `spring.jpa.hibernate.ddl-auto=validate`：新增 Entity 不自动建表。即使任务开关关闭，新 JAR 也需要提前存在正确的 import_task 表。**不能直接将本地 JAR 部署到尚未迁移的数据库。**
- 新接口与原文档管理接口一样依赖现有 Nginx 管理边界：后端监听 loopback；公网 `/api/` 拒绝；SSH tunnel + Basic Auth 的 `/admin/api/` 才代理任务接口。这里没有新增应用级登录机制，不得另行暴露 8080。
- 本次未执行生产迁移、部署或任何生产数据操作。

## 状态与 API

`QUEUED → PROCESSING → SUCCEEDED / FAILED`。

阶段：`PARSING → CHUNKING → INDEXING → COMPLETED`；旧 Document 没有任务时走只读 `VERIFY_EXISTING`。

- `POST /api/documents/import`（及旧 upload 入口）：校验/保存文件，短事务登记 Document + ImportTask；排队/处理返回 202，终态返回 200。
- `GET /api/import-tasks/{id}`：真实任务状态、阶段、尝试次数、可否重试、脱敏错误摘要，不返回原文或文件路径。
- `POST /api/import-tasks/{id}/retry`：FAILED 且允许重试时重新入队；默认最多 3 次领取尝试。已排队、处理中、完成的任务不会重复创建。
- 重复文件由已有 SHA-256 唯一约束裁决；唯一键竞争在失败事务退出后重新查找赢家。不能凭 Document 存在返回成功。
- Batch 结果分 success / failed / pending；QUEUED、PROCESSING 不计入 success。
- 管理端先展示服务器任务状态再轮询，网络错误保留 taskId 并允许继续查询；FAILED 显式重试，不自动循环上传。刷新后可重复提交同一文件找回原任务，不新增浏览器持久化。
- 任务开关启用时 Evidence 手工 chunks/index 一律 409；开关关闭但已有任务的 Evidence 也禁止手工重建。Canonical 链路不受该保护影响。

## 事务边界

1. 文件校验、流式 SHA-256、文件保存：无数据库事务。
2. 登记：Document 与 ImportTask 同一短事务。旧文档通过 Document 行锁串行登记唯一任务。
3. 调度：轮询数据库 QUEUED 行，进入有界单线程执行器；实际执行时加锁领取。进程在登记提交后、调度前退出，任务仍可由之后的扫描发现。
4. 解析/OCR/切分：事务外；沿用现有 chunk 算法。
5. 保存 Chunk：加锁并重新检查 owner_id、lease_generation、租约、Document ID；首次 Chunk 写入与 INDEXING 检查点同事务提交。
6. 索引：短事务取得脱离 JPA 的快照，然后事务外 Embedding / Qdrant add(upsert)。该专用入口没有 delete。
7. 完成/失败/心跳/重试/过期恢复：短事务和持久化状态，以数据库时间判断租约。

默认租约 90 秒、心跳 10 秒、执行时间上限 1800 秒，扫描间隔 1 秒，单实例 1 Worker、队列容量 8（配置校验 1..64）。
队列满只拒绝内存调度，不丢失数据库任务。FAILED 不无限自动重试：由重复上传或 retry 接口请求重试；崩溃导致的过期 PROCESSING 可自动重新排队，仍消耗原有限次数。

## 恢复、幂等与限制

- 过期恢复递增 lease_generation；旧执行者不能提交数据库状态/Chunk 或覆盖新执行者的数据库结果（不代表 Qdrant 外部写入 fencing）。
- 本实例实际仍执行的任务不会仅因过期而被恢复并行执行；超时/心跳失败会请求线程中断。**不保证第三方 SDK/OCR 必定响应中断**，完全阻塞的 Worker 可能需要人工排查或重启实例。
- 单 Worker 是每实例限制，**不是多实例全局单线程**。不同实例可能在租约过期后重叠执行外部请求。
- Qdrant Point ID 为 `UUID.nameUUIDFromBytes("nju-compass:evidence:<documentId>:<chunkId>")`。索引失败的重试沿用已提交 Chunk，不重新切分，不删除已有 Point。
- 已经发出的旧 Worker 外部请求不能被租约撤回；重复 upsert 使用同一 ID。**at-least-once，不是 exactly-once。**
- **不保证跨库原子写入**：Qdrant 仍可能部分成功；本地新增 readiness gate 在检索及上下文入口屏蔽未发布的新 Evidence。只有数据库任务 SUCCEEDED 才放行，不将跨库流程称为强一致或 exactly-once。
- Legacy 只比对文件路径边界、SHA-256、现有 Chunk 与 Qdrant metadata/数量；不重新解析、不 Embedding、不补写。无法证明解析完整性；发现不一致返回需要人工核验，不自动重导。
- 文件保存与数据库提交不是分布式事务；提交失败、并发输家或保存后进程退出可能留下未登记候选文件。为避免误删，**不自动删除候选文件**；保留供后续独立人工审计，本阶段没有清理任务。
- 错误摘要只保留阶段和固定代码，不存异常正文、凭据、provider body 或文档内容。

## 测试与本地验收

### P0 发布门禁（本地，尚未生产迁移）

- `ImportTask.taskKind` 在原子登记时确定：`NEW_EVIDENCE` / `LEGACY_VERIFICATION`，setter 只允许初始化，JPA `updatable=false`。不根据 stage 判断发布语义；数据库管理员仍可修改列，不声称数据库级不可变触发器。
- 新任务 QUEUED/PROCESSING/FAILED 不可见，SUCCEEDED 可见；无任务 Legacy 与任何状态的 LEGACY_VERIFICATION 保持已有发布语义。UNKNOWN、缺失 Document 和非 Evidence 一律拒绝。
- `EvidenceReadinessService` 一次批量查询候选 ID，V1、V2 Evidence fallback 共用；RagService 在 Answerability 之前复核，并对 Canonical 的原始 EvidenceRef 做同样核验。查询异常直接失败，不退化为允许访问。
- Evidence 一次搜索预算为 `min(256, max(20, requestedCandidateCount * 4))`，过滤后再裁剪，最终业务 topK 不变。Canonical 搜索不变；over-fetch 本身不增加 Embedding 调用。超出候选预算的有效资料仍可能遗漏，不能声称穷尽召回。
- `ChunkRepository.findMaxEffectiveYear*` 使用关联任务 NOT EXISTS 条件排除未发布的新 Evidence，保留无任务和 Legacy 核验状态；其他 Chunk 聚合查询未发现。
- 独立迁移 `sql/evidence-readiness-schema.sql` 必须在 import_task 建表 SQL 之后执行。旧任务新增列为 UNKNOWN，**不能从 stage 自动回填**；上线前必须独立核实来源并完成有依据的分类。生产尚未执行该 SQL。
- 默认 `ddl-auto=validate` 不变；任务开关关闭也不能省略这两份 migration。回滚到不含门禁的旧 JAR 可能再次泄漏部分 Point，禁止把关闭 Worker 开关当作安全回滚。
- 索引重试复用持久化 Chunk，稳定 Point ID 和现有元数据映射；新增测试比较新/旧快照的文本和 payload。未实现 Qdrant 外部 fencing；未来若允许任务中修改 metadata、内容或模型变化，过期外部请求仍可能覆盖较新写入。

PowerShell（使用 Java 21，不启动新的 8080 服务）：

```powershell
$env:JAVA_HOME = 'C:\Users\yu205\.jdks\ms-21.0.11'
.\mvnw.cmd test
.\mvnw.cmd '-Dtest=ImportTaskMySqlIntegrationTest,EvidenceQdrantIntegrationTest' test
Push-Location transfer-rag-admin
npm test
npm run build
Pop-Location
```

- 新增 MySQL 集成测试只使用 Testcontainers MySQL 8，JDBC URL/凭据仅来自隔离容器；不加载 application-local 或生产凭据。
- Qdrant 集成测试只使用隔离容器和测试 collection；Embedding 为测试替身，不调用远程模型。
- Docker 不可用时，集成测试明确 SKIPPED，不计入通过。项目原有 H2 contextLoads 仅用于原应用启动回归，不能作为 MySQL 锁/事务/幂等验证。
- 重启测试模拟旧 Worker 消失后的租约恢复及新 Worker 继续 INDEXING；不是完整 JVM kill/restart 演练。
- 单元测试覆盖解析/Chunk 失败、旧租约、重试、队列满、Legacy 只读、部分 upsert 稳定 ID、异步 API 和管理员代理模板；模板测试不等价于线上鉴权验收。

## 后续独立生产步骤（需再次授权，尚未执行）

1. Docker 恢复后完成全部隔离集成测试和真实进程重启演练；检查现有 schema 与 Hibernate validate 的兼容性。
2. 备份数据库和当前 JAR/配置，在独立批准的窗口仅执行 `src/main/resources/sql/evidence-import-task-schema.sql`。
3. 核对 import_task 字段/索引/唯一键/FK；IF NOT EXISTS 只保证重复执行不重建表，**不会修复错误的既有表结构**。
4. 保持 ddl-auto=validate，确认生产外部配置没有覆盖成 update/create；部署适配的管理端与 JAR 后再显式开启任务开关。
5. 仅用新授权测试文件检查异步状态、失败重试、安全边界、资源使用；不重导已有 20 份 Evidence，不动 Canonical/EvidenceRef/Qdrant 存量。
6. 记录单 Worker 在 2C4G 上与在线 RAG 争用 CPU/内存/远程模型配额的实际情况，再决定是否调整限额。


## 本轮文件清单

本地实施文件（不包含原有的三份未跟踪 Gold Review 文档）：

- `docs/evidence-import-tasks.md`
- `src/main/java/com/yu/transferrag/config/ImportTaskExecutionConfig.java`
- `src/main/java/com/yu/transferrag/controller/ImportTaskController.java`
- `src/main/java/com/yu/transferrag/entity/ImportTask.java`
- `src/main/java/com/yu/transferrag/entity/ImportTaskStage.java`
- `src/main/java/com/yu/transferrag/entity/ImportTaskStatus.java`
- `src/main/java/com/yu/transferrag/repository/ImportTaskRepository.java`
- `src/main/java/com/yu/transferrag/service/EvidenceChunkPersistenceService.java`
- `src/main/java/com/yu/transferrag/service/EvidenceImportProcessor.java`
- `src/main/java/com/yu/transferrag/service/EvidenceImportWorker.java`
- `src/main/java/com/yu/transferrag/service/EvidenceLegacyVerifier.java`
- `src/main/java/com/yu/transferrag/service/EvidenceRegistrationService.java`
- `src/main/java/com/yu/transferrag/service/EvidenceTaskProcessor.java`
- `src/main/java/com/yu/transferrag/service/ImportTaskDispatcher.java`
- `src/main/java/com/yu/transferrag/service/ImportTaskManualGuard.java`
- `src/main/java/com/yu/transferrag/service/ImportTaskStateService.java`
- `src/main/resources/sql/evidence-import-task-schema.sql`
- `src/test/java/com/yu/transferrag/controller/EvidenceImportApiTest.java`
- `src/test/java/com/yu/transferrag/controller/ImportTaskControllerTest.java`
- `src/test/java/com/yu/transferrag/service/EvidenceChunkPersistenceServiceTest.java`
- `src/test/java/com/yu/transferrag/service/EvidenceImportWorkerTest.java`
- `src/test/java/com/yu/transferrag/service/EvidenceIndexIdempotencyTest.java`
- `src/test/java/com/yu/transferrag/service/EvidenceLegacyVerifierTest.java`
- `src/test/java/com/yu/transferrag/service/EvidenceQdrantIntegrationTest.java`
- `src/test/java/com/yu/transferrag/service/EvidenceTaskProcessorTest.java`
- `src/test/java/com/yu/transferrag/service/ImportTaskDispatcherTest.java`
- `src/test/java/com/yu/transferrag/service/ImportTaskManualGuardTest.java`
- `src/test/java/com/yu/transferrag/service/ImportTaskMySqlIntegrationTest.java`
- `src/test/java/com/yu/transferrag/service/ImportTaskStateServiceTest.java`
- `transfer-rag-admin/src/importTaskPolling.js`
- `transfer-rag-admin/test/importTaskPolling.test.js`
- `pom.xml`
- `src/main/java/com/yu/transferrag/controller/DocumentController.java`
- `src/main/java/com/yu/transferrag/controller/DocumentImportController.java`
- `src/main/java/com/yu/transferrag/dto/BatchDocumentImportItemResponse.java`
- `src/main/java/com/yu/transferrag/dto/BatchDocumentImportResponse.java`
- `src/main/java/com/yu/transferrag/dto/DocumentImportResponse.java`
- `src/main/java/com/yu/transferrag/repository/DocumentRepository.java`
- `src/main/java/com/yu/transferrag/service/ChunkService.java`
- `src/main/java/com/yu/transferrag/service/DocumentImportService.java`
- `src/main/java/com/yu/transferrag/service/DocumentService.java`
- `src/main/java/com/yu/transferrag/service/QdrantMetadataReader.java`
- `src/main/java/com/yu/transferrag/service/VectorIndexService.java`
- `src/main/resources/application.properties`
- `src/test/java/com/yu/transferrag/controller/DocumentControllerTest.java`
- `src/test/java/com/yu/transferrag/service/DocumentImportServiceTest.java`
- `src/test/java/com/yu/transferrag/service/DocumentServiceTest.java`
- `transfer-rag-admin/package.json`
- `transfer-rag-admin/src/App.vue`
