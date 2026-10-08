# Evidence 异步导入：生产发布前最终评审与发布方案

评审日期：2026-10-09（Asia/Shanghai）。**本文件是待批准方案，不是已执行发布记录。**

## 1. 结论与版本边界

> 最新发布阻塞修复轮次：以 `evidence-import-release-blockers-acceptance.md` 和新固定提交的产物记录为准。下面早期 300 PASS / 未提交版本描述保留为历史评审背景，不再指向最终候选版本。旧 `45d2de4ec746352cd4ddff0d176d7dbad2d96d10` 及其 JAR/验收证据保留，不用于本轮最终发布。

- `LOCAL_ACCEPTANCE_COMPLETE=true`：最新 MySQL 18 PASS、Qdrant raw 对照 2 PASS、冻结索引联合集成 4 PASS、真实 JVM 恢复 1 PASS；全量 Maven 303 项 / 300 PASS / 0 FAIL/ERROR / 3 SKIP（两个 opt-in Benchmark、一个 Windows symlink 条件跳过）；Canonical 31 PASS、RAG/Retrieval 30 PASS、管理端 6 PASS、build PASS。详细结果见 evidence-import-immutable-index-acceptance.md。
- **`PRODUCTION_RELEASE_READY=false`**：本地不可变快照加固已实现并补充隔离验收；仍待授权提交/推送固定版本、生产有效 schema/配置及备份恢复核验。不得把本地通过当作生产已准备完成。
- `PRODUCTION_DEPLOYED=false`。最新授权轮次已完成本地快照加固、测试和候选构建；未连接生产，未执行生产迁移、提交、推送、部署或配置变更。
- 本地当前 main HEAD 为 `bd1b2598c547951ec051f4ef80d0f674755a8900`，**实现仍在未提交工作区，不能把该 HEAD 当成包含异步导入的发布版本**。
- 目标仓库：`https://github.com/Zhenxiangyu05/NJU-TransferRAG.git`。未来另行批准后定义完整 `RELEASE_COMMIT`，所有 JAR、管理端 dist、migration 和测试记录绑定此 SHA。
- 用户提供生产基线：Document=28、CANONICAL=8、Chunk=462、EvidenceRef=15、Qdrant=462。本轮未现场复核，不能写为最新已验证数值。

可分开审批：

1. **A：只读兼容发布**：三份迁移 + 新后端门禁 + 管理端代码，`app.import-tasks.enabled=false`，不登记任何任务，不执行试上传。满足实际配置/备份检查后才可批准。
2. **B：异步导入试运行**：依据本地加固验收记录，再单独批准启用任务与一份授权测试资料。A 上线成功不代表 B 已获批准。

## 2. 实际代码与写入边界

本轮关闭开关保护：共享 `ImportTaskFeatureGuard` 固定读取进程启动配置。DocumentImportService 在保存文件前检查，EvidenceRegistrationService 在任何 Document/Task 查询或写入前检查；ImportTaskStateService 在 create/retry/claim/heartbeat/recoverExpired/requireOwned 前检查。stage/succeed/fail 和 Chunk/快照检查点通过 requireOwned 同时校验开关及 owner/generation。关闭时统一返回 HTTP 503 / `IMPORT_TASKS_DISABLED`，不修改既有 Task 或检查点。任务只读 view/GET 不受影响；Readiness Gate 不受开关影响；Canonical 不依赖此 guard。Dispatcher/线程池继续仅 true 时创建。

手工 Evidence 重切分、删除式 Index 及其删除入口保持不可绕过的 Service 冻结保护；不得通过关闭开关恢复这些写路径。

| 位置 / 方法 | 已实现 | 边界 |
|---|---|---|
| EvidenceRegistrationService.register/existing | Document+Task 短事务、哈希唯一键、Document 行锁、每 Document 唯一任务 | 重复登记不修改已存在 Document metadata；文件保存和 DB 提交非原子 |
| ImportTaskStateService.requireOwned/claim/heartbeat/recoverExpired | owner、generation、租约、状态校验及 DB 时间；过期恢复递增 generation | 只保护 DB，不能撤销已发出的网络请求 |
| EvidenceChunkPersistenceService.persistFirst/snapshot | 首次 Chunk+不可变输入+INDEXING 同事务；已有 Chunk 不重建 | snapshot 仅用于 Legacy 只读核验；新任务不再用它重新生成输入 |
| EvidenceTaskProcessor.process | INDEXING 重试跳过解析；网络位于 DB 事务外 | snapshot 后到实际 Qdrant 写入间仍可丢失租约 |
| EvidenceImportWorker / ImportTaskDispatcher | 每实例单 Worker、有界队列；实际运行中任务不因租约过期而本实例并发重试 | 非全局单 Worker；实例退出/多实例时旧外部请求仍可能晚到 |
| FrozenEvidenceIndexer + EvidenceSnapshotStateService | 首次向量在所有权短事务中持久化，随后直接重放 protobuf Point；重试无 Embedding；64 Point 有界批次 | 没有外部 fencing；晚到的受控请求只能重放同一已冻结内容 |
| ImportTaskManualGuard.checkIndexMutation + Chunk/VectorIndex Service | 服务层拒绝所有 Evidence 旧删除/重切分/重索引入口，包括无任务 Legacy | Canonical 保留原路径；这不是禁止运维直接 SQL/Qdrant 写入的机制 |
| EvidenceReadinessService + RetrievalService + RagService | MySQL 批量核验，V1/V2 fallback 过滤后裁剪，上下文前复核，Canonical EvidenceRef 也核验 | SUCCEEDED 后不比较 Point 与冻结输入；不校验向量/内容版本 |
| ChunkRepository.findMaxEffectiveYear* | 排除未发布新增 Evidence；保持 Legacy 原语义 | 不保证超过 256 候选预算的完整召回 |

### 手工入口与绕过风险

- 正常 `/api/documents/upload` 和 `/api/documents/import[/batch]` 已进入异步 facade；无 active Controller 调用旧 uploadDocumentIfAbsent 路径。
- `POST /api/documents` 仍可登记**仅元数据、无 Task**的 Evidence；请求不包含文件路径/正文，本身不生成 Point。它不是完整异步上传入口，不能把它当成任务就绪检查证明。
- 没有发现当前 Controller 的 Chunk 内容/Document metadata 修改接口。旧 createChunks/indexDocument/removeDocumentIndex 和诊断 add 入口已增加服务层 Evidence 拒绝；实体 setter、Repository 或管理员直接写库仍不构成数据库级不可变。
- Canonical 保留原同步链路；本次不改其 compensation。生产维护窗口也必须暂停 Canonical 管理写入，以免备份跨库错位。
- ImportTaskController.retry **不检查 enabled**。开关 false 不是“禁止所有管理写入”，也不是停止在途 Worker 的动态开关；在已有任务时仍可把 FAILED 改成 QUEUED。
- 管理边界依赖 Nginx loopback + Basic Auth，不是 Spring Security/JWT；不得开放后端 8080 或公网任务 API。

## 3. 外部写入风险与本地加固（2026-10-09 更新）

### 已验证的不变量

受控登记/重试不修改已有 Document metadata；首次 Chunk、序号、Point ID、文本、metadata、collection 和 model 随 INDEXING 检查点在同一事务写入 evidence_index_snapshot，保存 SHA-256 与格式版本。首次完整 Embedding 结果在所有权短事务中冻结为精确 protobuf Point 的 Base64 集合，保存独立 SHA-256。所有外部写入均发生在冻结事务成功返回之后；旧 owner 不能更新 DB 状态/快照。重试仅重放该集合，不重新读取文档标签或调用 Embedding。

### 无法保证的场景

1. Qdrant 仍是无条件 upsert，没有外部版本 fencing。raw upsert 对照测试仍证明它能覆盖较新写入，但实际新 Evidence Worker 的晚到请求只重放同一冻结 Point，不包含不同代际内容。
2. 管理员直接 SQL/Qdrant 写入、修改快照并重算 hash、其他应用写同一 UUID 不在保证范围。禁止运营期间直接改已冻结内容；未来编辑/重新索引必须另做版本设计。
3. 首次向量冻结前若崩溃，未保存的计算结果只能放弃；恢复可重新计算，但此前没有 Point 写出。冻结之后不再调用模型。此机制不承诺远程模型版本永不变化。
4. 多实例/重启后的外部重叠仍可能发生，但在所有实例均运行本加固版本、使用同一快照时内容相同。禁止混跑旧的重新 Embedding Worker。Cosine 存储会归一化向量，数值验收应使用归一化后的浮点容差。

门禁阻止 QUEUED/PROCESSING/FAILED/UNKNOWN 新 Evidence 进入 Answerability、Prompt、Citation 和默认年份统计；快照另行保证受控重放内容一致。门禁本身不比对每个已发布 Point 的 hash，不保护直接改库，也不改变 Legacy 核验失败资料的既有可见性。

### 已实施的本地措施与仍需批准的发布

**A 只读阶段**：保持任务开关 false，确认 import_task 为空；停止管理员全部写操作，禁止另一个应用实例/手工脚本。此时没有新异步外部写入，不暴露新增陈旧写入场景。原 Evidence 上传暂时返回 503，不退回旧同步上传。

**B 的代码防护已实现**：只在新 Evidence 索引链路持久化并复用同一套精确索引输入，避免不同代际写不同 payload/vector：

- Chunk 检查点冻结文档 metadata、Chunk 文本和 Point payload，持久化摘要与格式版本；已有快照不得重建覆盖。
- 远程 Embedding 在事务外完成；取得合法 owner/generation 后，短事务将首次有效的完整 Point 集合写入 MySQL LONGTEXT；快照限制 16 MiB，超限拒绝而非截断。未完成登记的执行者不得发起 upsert。
- 后续所有重试/接管只写已登记的**同一 Point ID、payload、vector**，不再重新读取可变 metadata 或重新 Embedding；模型/维度指纹不匹配时拒绝，等待人工审查。
- Evidence Service 入口拒绝旧删除式索引、手工重新切分及重新 Embedding 路径，包括无 Task 的 Legacy，以消除检查后登记任务的竞争窗口。Legacy 阅读/检索/只读核验不变；手工重切分/重索引不再可用，这是必须告知管理员的兼容变化。
- 真实 MySQL/Qdrant 测试使用本地 HTTP 延迟代理接收旧 Worker 的精确 protobuf 请求后暂停，B 接管成功后才转发到真实 Qdrant。验证稳定 ID/数量、精确 payload、向量一致、旧 owner 无法改成功状态；额外验证旧 Embedding 计算结果不能提交竞争快照。真实 JVM kill/restart 验证冻结产物持久化及恢复不再 Embedding。

影响：新增独立 snapshot 表与第三份 additive Migration、附加存储及测试；不改 Qdrant collection、UUID、RAG、Citation 或历史数据。所有新增能力仅在本地，不是已上线能力。没有 conditional-write fencing、exactly-once 或跨库原子提交。

启用前必须确保没有仍运行旧 Worker 的实例。保留单实例和有界队列以控制资源，不将其解释为全局单执行者或外部 fencing。

## 4. Migration 审查与失败状态

顺序必须为：

1. `src/main/resources/sql/evidence-import-task-schema.sql`
2. `src/main/resources/sql/evidence-readiness-schema.sql`
3. `src/main/resources/sql/evidence-index-snapshot-schema.sql`
4. 检查完整 schema、UNKNOWN 数、约束及索引，再启动新 JAR。

结构要点：

- import_task：BIGINT 主键；document_id 非空；status/stage VARCHAR(32)；attempt_count/max_attempts；owner_id、lease_generation、heartbeat_at/lease_until；next_attempt_at；固定错误摘要；chunk_count/indexed_chunks；创建/更新时间等。
- `uk_import_task_document(document_id)`；FK document(id) **ON DELETE RESTRICT**；`idx_import_task_ready(status,next_attempt_at,id)` 和 `idx_import_task_lease(status,lease_until,id)`。
- document.file_hash 原唯一约束保留。检查 Document ID 的 signed/type 与 FK 一致、表为 InnoDB。
- readiness 批量按 Document 主键 + Task 的 document_id 唯一索引连接；年份相关子查询也可使用该唯一索引。本次不额外添加无必要索引；上线前用 EXPLAIN 核实真实 schema 的计划。
- task_kind VARCHAR(32) NOT NULL DEFAULT UNKNOWN；新任务登记显式设置 NEW_EVIDENCE/LEGACY_VERIFICATION。setter 单次初始化、JPA updatable=false；SQL 没有不可变 trigger 或枚举 CHECK，不声称阻止管理员直接 UPDATE。
- UNKNOWN 不能按 stage/status/sourceType 推断。若存在已有任务，先独立核验登记来源；不能自动全置 NEW 或 LEGACY，更不能自动创建 28 个任务。预期本次首次建表为 0 行。
- snapshot 表以 task_id 为主键及 FK(import_task.id) RESTRICT；input_json/input_sha256 为只写一次输入，points_json/points_sha256 首次向量成功后只写一次。格式/collection/model/dimension 保存在带 hash 的内部 JSON 中，无公开读取接口，不保存凭据。
- 三份 SQL 只建表/加列，不触碰旧 Document/Chunk。CREATE IF NOT EXISTS/存在列检测已实测可重复执行，**不会自动修复一个已经存在但结构错误的表/列/索引**。
- MySQL DDL 不是跨文件事务；后续文件失败可能缺 task_kind 或 snapshot 表。保留成功的结构，排查后重复执行剩余脚本，再逐列核验；不要 DROP 或用 JPA update 修复。
- `ddl-auto=validate` 已在本地默认配置，但启动时必须检查生产 profile、外部配置、EnvironmentFile/命令行是否覆盖它。开关 false 仍会校验 ImportTask，不能省略迁移。
- schema 已完整而新应用未启动：旧 JAR 可继续读取原表；新表留空不会改旧业务。新应用 validate 启动失败：按第 7 节判断是否已有任务后回退，保留新增结构。

**兼容性证据与限制**：新增隔离 MySQL 测试从本地旧实体定义重建 document/chunk/evidence_ref/entity_alias 完整表，使用人工测试行，执行三份 SQL 两次，再以 Hibernate validate 启动并核验 Legacy 可见性、无自动建任务及 FK。UNKNOWN 旧任务测试仍默认拒绝。此模拟不是实际生产 Schema 漂移核验；发布前仍需只读比对生产 information_schema，不导出正文或凭据。

## 5. 备份与可恢复性（以下均待发布授权）

先停止管理员写操作并确认无外部写脚本。在正式维护窗口停止旧后端、确认旧 PID 退出，再取得跨库基线备份；只停止后端，不重启 MySQL/Qdrant/Docker/Nginx。
这避免旧同步导入在 dump 与 Qdrant snapshot 之间继续提交。不能把两份独立快照称为天然跨库一致。

在服务器创建 `/var/backups/nju-transfer-rag/evidence-import-<UTC时间戳>/`，root:root 0700，所有数据/凭据备份 0600；不在 Git 内，不向终端输出正文或 secret，不复制到未受保护环境。

### MySQL

- 使用 MySQL 8 客户端、当前**应用实际生效的 TCP Host/port/user**，先 SELECT 1；不退回曾认证失败的容器 root socket、不读取 Docker 全部环境、不更改账号。
- 用受控服务器端程序读取有效凭据，生成 root-only 临时 MySQL option file（host、port、user、password）。不要 source/打印整个 env，不用密码命令行参数，不让本地 PowerShell 展开密码。
- 下列变量需未来操作员核验后设置：BACKUP 为新建且已验证的备份目录；MYSQL_CNF 为 root-only 凭据 option file；不会在本轮执行。

```bash
set -Eeuo pipefail
sudo test -r "$MYSQL_CNF"
sudo mysqldump --defaults-extra-file="$MYSQL_CNF" \
  --single-transaction --quick --routines --triggers --events \
  --no-tablespaces --set-gtid-purged=OFF transfer_rag \
  | sudo tee "$BACKUP/transfer_rag.sql" >/dev/null
sudo chmod 600 "$BACKUP/transfer_rag.sql"
sudo test -s "$BACKUP/transfer_rag.sql"
sudo grep -q 'Dump completed' "$BACKUP/transfer_rag.sql"
```

检查所有业务表 InnoDB、dump 命令无失败、备份 SHA-256；dump 完成标记不是恢复证明。优先在受保护的隔离实例测试恢复并核对计数；若未做恢复，记录“可恢复性未实测”，不得以文件非空判为已验证。客户端/必要权限不可用时停止发布，不临时轮换密码。

### Qdrant

- 先记录生产实际版本、transfer_chunks point count、1024/Cosine、collection status 与配置；本地测试版本 1.18.0 不代表生产版本。
- 后端停写后，通过 localhost 的官方 collection snapshot API 创建 snapshot；记录返回的服务端名称，下载到 root-only 备份目录，保存大小/SHA-256/对应计数与版本。创建 snapshot 是生产写操作，必须在后续批准窗口执行。
- 下载不是重建 collection；不删除原 snapshot、Point、collection 或 Volume。鉴权如存在使用安全配置文件，不输出 key。
- 使用生产同版本的受保护隔离 Qdrant 验证 snapshot 恢复流程；使用合成数据可先演练 API，但不能声称实测恢复了这份备份。恢复到正式 collection 必须再次批准，不在普通应用回滚中自动执行。
- snapshot 不可用或未证明恢复路径时标记阻塞，用户决定延期或明确风险豁免，不静默跳过。

### JAR / Admin / 配置 / 文件

- 私下核验 systemd 有效 ExecStart/WorkingDirectory/EnvironmentFile，记录**真实活动 JAR 路径**（不假设它就是 target 的新文件）；备份 JAR 和 SHA-256。
- 归档 `/var/www/admin/`，保留旧 index.html 与全部 hash assets；备份当前 Nginx 配置、服务 unit/drop-in 和运行配置。含凭据的 env/认证文件仅 root-only 服务器备份，不打印、不下载到 Git。
- 原文授权清单、上传原文件目录必须保留，记录权限/hash。首次迁移不修改它们；若已有合格文件备份，引用其时间戳与恢复位置，不能让 DB 备份被误当成原文件备份。
- 后续恢复 DB/Qdrant 全备份会覆盖之后写入，涉及潜在数据丢失，必须先保存故障现场并单独批准；普通回滚不恢复全库。

## 6. 安全发布步骤

### 准备（维护窗口之外）

1. 本地 `git status`、`git diff --check`、逐文件审查 secret/Gold/业务范围。保留三个已有未跟踪 benchmark review 文件等无关改动；禁止 git add -A/stash/reset/clean。本轮不提交。
2. 后续获得 Git 授权后，明确列出实现、测试、SQL、管理端和文档路径提交；正常 push main，记录 RELEASE_COMMIT。若落实第 3 节修复，须重新执行全部集成/回归，不能沿用旧数量。
3. 服务器工作区必须 clean；`git fetch origin`、`git pull --ff-only origin main` 后 HEAD 必须等于批准 SHA。若 origin 超前或网络失败，停止或使用已验证的**同 SHA** bundle fast-forward；不自动部署更晚提交。
4. 构建不能覆盖正在运行的 JAR。由固定 SHA 的 git archive 导出到新建、明确验证的独立 build 目录，用 Java 21 构建 `./mvnw package -DskipTests`，记录产物 SHA-256；服务器不运行容器集成测试。
5. 在同一 staging 源码中构建管理端 `npm ci`、`npm test`、`npm run build`，确认 `/admin/assets/` base。不重建公众前端，不改 HTTPS、ICP备案号、授权清单或 AI 配置。Maven 构建内存按 2C4G 条件限制并监控，保持现有后端堆限制。
6. 复核线上有效路由、后端 loopback、管理员 tunnel/Basic Auth、公网 8080/5173 不开放；核验真实 schema 与新版本 validate 兼容性。候选 JAR 必须检查不含 application-local.properties：根工作区 Maven 默认打包会带入该 ignored 文件，不能直接发布它；只允许全新、无 ignored 配置的固定源码 staging 构建。

**禁止直接执行现有完整 `scripts/deploy.sh`**：它会调用 apply-nginx-security.sh，用仓库仅 HTTP 的模板覆盖当前站点，并同步公众前端、删除 dist 之外文件、先更新 Admin 后重启后端。它不能保护此次 HTTPS 和渐进发布要求。`/api/test/vector-store` 也只返回 Bean initialized，不实际检查 MySQL/Qdrant，不足以验收就绪。

### 正式维护窗口（需要后续批准）

7. 暂停所有管理写入，确认没有在途上传/Canonical 导入/手工脚本及第二实例。必要的临时管理写入拦截配置须另行批准，只收紧、不放开接口；单靠任务开关不能挡住所有写入。
8. 保持 Nginx 静态服务运行，停止旧后端并确认旧 PID/子进程退出；从此公众问答及原文 API 暂不可用。完成第 5 节备份、恢复可行性检查和数据基线记录。
9. 三份独立 migration 顺序执行；客户端使用同一安全 MySQL option file，显式目标 transfer_rag。逐列检查类型/default/nullability、唯一键、外键/index、UNKNOWN 数和 import_task/snapshot 总数。初次应为 0；任何意外行或结构不符先停止。
10. 固定任务开关 **false** 和有效 ddl-auto **validate**（包含外部覆盖检查），保持 canonical-first 原值，不改模型/参数。将已构建 JAR 安全复制到发布位置；若 systemd 原路径固定，只在旧 PID 完全退出后以 `.next` 同文件系统原子替换已备份的精确 JAR，不覆盖源码或运行中的 JAR。无需修改 unit 时不修改 unit。
11. 仅启动 `nju-transfer-rag.service`。确认新 PID、活动产物 SHA；先等 8080 loopback LISTEN、启动无 schema/DB 认证异常，再独立 MySQL SELECT/计数、Qdrant状态核验；脱敏记录异常类/错误代码，不输出 prompt/正文/密钥。
12. 后端可用后更新管理端：备份已在第 8 步完成；只追加/覆盖明确 dist 产物，旧 hash assets 保留，最后原子替换 Admin index.html。不要 rsync --delete，不改 `/var/www/nju-transfer-rag`，不重载无变更的 Nginx。
13. 完成以下 A 阶段验收，维持任务开关 false。任何关键失败按第 7 节回滚，不开启 B。

第 9 步的待批准命令模板（RELEASE_DIR 必须是已核验固定 SHA 的独立 staging 源码目录，不是任意当前分支）：

```bash
set -Eeuo pipefail
test -s "$RELEASE_DIR/src/main/resources/sql/evidence-import-task-schema.sql"
test -s "$RELEASE_DIR/src/main/resources/sql/evidence-readiness-schema.sql"
test -s "$RELEASE_DIR/src/main/resources/sql/evidence-index-snapshot-schema.sql"
sudo mysql --defaults-extra-file="$MYSQL_CNF" --database=transfer_rag \
  < "$RELEASE_DIR/src/main/resources/sql/evidence-import-task-schema.sql"
sudo mysql --defaults-extra-file="$MYSQL_CNF" --database=transfer_rag \
  < "$RELEASE_DIR/src/main/resources/sql/evidence-readiness-schema.sql"
sudo mysql --defaults-extra-file="$MYSQL_CNF" --database=transfer_rag \
  < "$RELEASE_DIR/src/main/resources/sql/evidence-index-snapshot-schema.sql"
sudo mysql --defaults-extra-file="$MYSQL_CNF" --database=transfer_rag --batch --execute="
  SELECT COUNT(*) AS documents FROM document;
  SELECT COUNT(*) AS canonical FROM document WHERE document_role='CANONICAL';
  SELECT COUNT(*) AS chunks FROM chunk;
  SELECT COUNT(*) AS evidence_refs FROM evidence_ref;
  SELECT COUNT(*) AS import_tasks FROM import_task;
  SELECT task_kind,status,COUNT(*) AS tasks FROM import_task GROUP BY task_kind,status;
  SHOW CREATE TABLE import_task;
  SHOW CREATE TABLE evidence_index_snapshot;
  SHOW INDEX FROM import_task;
  SHOW INDEX FROM document;"
```

这些命令不包含密码值，也不打印文档正文。SHOW 的结果需与实体逐列比对；命令成功不能替代 schema 审查。

预计影响：需要停机维护窗口，不保证零停机。窗口长度取决于备份、DDL 和启动实际耗时；静态首页可继续显示，问答、原文后端 API 和管理 API 在后端停机期间不可用。**不重启 MySQL、Qdrant、Docker或 Nginx。**

### A：不写知识库的验收标准

- HTTPS 根站、HTTP/www 跳转与证书不变；首页、/legal、/privacy、/feedback、/sources/16 为 200，标题/布局正确。
- 20 份原文仍按既有清单授权；抽查 PDF inline/Range 206、Markdown 全文、DOCX attachment、hash 校验；不自动授权新增资料。
- 公网 `/admin/`、`/admin/api/` 为 403；旧 `/api/documents/4` 404；公开 `/api/import-tasks/{id}` 与 retry 不可代理（预期 404）；公开 sources 非 GET/HEAD 拒绝。SSH tunnel 管理页面未认证 401，已认证 GET 正常；不创建任务来测试。
- 新后端门禁开启且 import_task=0，历史 Evidence 正常。用少量已有已通过案例测试：一个正常 Canonical（如机考准备，Evidence 2/PERSONAL）及一个已知 Evidence fallback。检查真实回答、资料年份、Citation Evidence ID/sourceType、retrievalLayer/fallback 与实际相关性；只 HTTP 200 或空泛回答不合格。不运行全量 Gold。
- 查询错误不得从未知状态 fail-open；线上不人为破坏数据库来验证，该项引用隔离测试及配置审核。
- 数据仍为 28 / 8 / 462 / 15 / 462；import_task=0；不因启动创建任何历史任务；现有原文件/hash/manifest不变。

### B：单独批准的受控试运行

- 前提：第 3 节防护选择落实、A PASS、写入授权明确。选一份无敏感信息、拥有明确使用权且哈希不与旧文件重复的真实 Evidence，记录标题/来源/授权、预期事实/Chunk 范围；没有材料就停止，不能编造资料。
- 另行批准切换任务开关 true并重启后端；保持单实例/单 Worker，固定资料和模型配置；不重复导入旧 20 份资料，不创建新的 Canonical。
- 管理端提交后返回 taskId 和真实状态，轮询 QUEUED→PROCESSING→SUCCEEDED；可能快速跳过某状态，未观察到不等于已验证。不得通过改生产状态、制造故障或放慢模型来伪造证据。
- 使用受保护的只读检索诊断：QUEUED/PROCESSING 时该 ID 必须被门禁排除；SUCCEEDED 后检查全量 Chunk/Point 对应及可检索事实、真实回答/Citation。若状态太短无法观察，引用本地确定性测试并记录生产该项“未观测”，暂停进一步批量导入而非宣称全部实测。
- 重复上传只能复用同一 Document/Task，返回实际 QUEUED/PROCESSING/SUCCEEDED；失败时根据预算返回 FAILED/重新排队，不能凭 Document 存在报成功。失败注入仅在隔离环境，本阶段不人为制造生产 Embedding/Qdrant 错误。
- 文件不自动公开阅读；Readiness SUCCEEDED 不等于版权公开授权。
- 记录导入前后各项增量，旧 28/462/15/462 数据不得减少；测试资料及 Point 保留，不自动删除“清理测试”。处理 QUEUED/FAILED 数、lease/heartbeat、CPU/heap/GC、provider错误率；全部符合才讨论下一批。

## 7. 安全回滚（禁止统一恢复旧 JAR）

### A. 尚未产生任何任务/新 Chunk/Point

冻结管理写入、停止并确认新进程退出，核验 import_task=0 且基线、原文件、原授权未改变。才允许恢复准确的旧 JAR 和 Admin index/assets，保持有效旧运行配置。保留新增空表/列；不要反向 DROP。
旧应用不查询新表，新增结构本身不要求恢复全库。若 ddl-auto 覆盖导致旧 schema 改动，不满足此条件，停止自动回滚。
迁移第一份成功第二份失败且旧版本仍可用时，可保持/恢复旧版本服务，待另一个批准窗口补齐迁移。

### B. 已有任务或部分 Point

1. 立即冻结所有管理写入和 retry；保存故障现场（Task/Chunk ID、状态、generation、Point清单/日志摘要），不要删数据。
2. 关闭调度需应用重启才生效，且 shutdownNow/线程 interrupt 不证明旧请求消失。停止当前进程/其服务子进程，确认无其他实例/写脚本；Qdrant 已受理请求仍可能晚到，不能仅等待一个固定秒数就宣称排空。
3. **只能切到保留完整 Readiness Gate 的已验证兼容 JAR**，任务开关 false，保留所有 Task/Chunk/Point。FAILED/QUEUED/PROCESSING/UNKNOWN 继续不可见；旧无任务 Legacy 可见。已有 SUCCEEDED 无疑点时保持服务；若其 payload 完整性可疑而当前代码无版本隔离，暂停问答直到进一步批准的修复，不靠门禁虚假保证。
4. 回退首选**本加固 JAR 保持 tasks.enabled=false**：同一 schema、同一门禁与只读公开功能，关闭 Dispatcher/执行线程池，不删除记录，保留冻结快照。退回无快照的旧 Worker 版本或只因旧 JAR 能启动就启用其 Worker 都禁止。此本地 artifact 尚未绑定批准 commit，部署前核验其 hash 并备份。若 JAR 本身不可用且没有经过同样验收的门禁兼容备用产物，保持维护停机，不恢复无门禁旧 JAR。
5. 不能删除 Task 使文档变成“无任务 Legacy”，不能把任务批量改成 LEGACY/SUCCEEDED，不能清 Point/恢复旧快照来掩盖部分索引。
6. 重新启用处理时，先核验运行者退出、索引输入/模型一致、来源类型和任务预算，再批准 owner-fenced 的恢复；不重导或重切分旧 Chunk。

即使只产生了 Legacy 核验任务，也应走 B 的保守路径核验，不擅自认定所有任务都无写入。全库/snapshot恢复属于另一种数据恢复操作，有覆盖风险，必须额外批准。

## 8. 发布批准条件与本轮交付

最新本地准备状态：管理端只更新 lockfile 内 source-map-js 1.2.1→1.2.2，不改布局、Vue/Vite 版本或业务行为；依赖和开关阻塞项的测试证据见新验收记录。旧版 retry 在 false 时可重新排队的限制已在新版本 Service 层修复，不能用旧版产物替代新固定版本。管理写入冻结仍用于维护窗口阻止 Canonical 等其他业务写入，不再是修复 disabled retry 的临时替代。

安全降级版本仍采用新加固 JAR + tasks=false，并实际隔离启动验证。它不是独立较老的兼容版本；若该 JAR 自身不可运行，只能维持维护状态，不恢复无门禁旧 JAR。三份 Migration 不变：task→readiness→snapshot，ddl-auto=validate；开启处理与真实资料试导入仍须分别审批。

正式启用前的阻塞项：

1. 本地快照防护已经实现；发布前确认全部运行实例与回退产物使用同一加固链路，禁止混跑旧 Worker。
2. 用户授权提交/推送并冻结实际 RELEASE_COMMIT/产物；清晰排除所有未提交无关文件与 secret。
3. 实际生产有效 schema/profile/JAR 路径/HTTPS安全规则核验，以及安全备份与可恢复性证明。
4. 门禁兼容回滚 JAR 准备；管理写入冻结与维护窗口批准。
5. A/B 分别批准，B 的真实测试 Evidence 与使用权限由用户提供；不能提前插入测试记录。

最新加固轮次仅修改本地 Evidence 索引与手工写入保护、测试和方案；未修改 Gold、Canonical 数据或任何生产状态。原三个 Benchmark review 文件保留。

`LOCAL_ACCEPTANCE_COMPLETE=true`

`PRODUCTION_RELEASE_READY=false`

`PRODUCTION_DEPLOYED=false`
