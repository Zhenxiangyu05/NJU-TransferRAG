# Evidence 异步导入：版本冻结与生产只读预检查

核验日期：2026-10-09。仅本地提交、构建与隔离验收；未 push，未执行生产迁移、部署、备份创建或服务重启。

## 生产现场（实际只读结果）

- Git HEAD：`bd1b2598c547951ec051f4ef80d0f674755a8900`；工作区 clean。
- 活动 JAR：`/home/ubuntu/projects/NJU-TransferRAG/target/Transfer-RAG-0.0.1-SNAPSHOT.jar`。
- 活动 JAR SHA-256：`b4aae5a78b83ebf5b9a3e2602e0f69cd0e2a5c23499e9734602f9dc2f337106f`；无 application-local 配置。
- systemd：User=ubuntu，WorkingDirectory 为生产项目，EnvironmentFile=/etc/nju-transfer-rag.env，Restart=on-failure；JVM -Xms128m -Xmx768m。
- 旧 JAR 打包配置 ddl-auto=update；候选源码为 validate。canonical-first 的有效环境覆盖为 true。切换时必须继续核验有效配置，不能沿用 update 覆盖。
- Spring Boot、Nginx、Docker active；MySQL/Qdrant 容器运行；3306、6333、6334、8080 均仅 loopback；未发现 5173 监听。
- MySQL 只读计数：Document=28，Canonical=8，Chunk=462，EvidenceRef=15；无 import_task / evidence_index_snapshot 表。
- 复用运行进程的有效 DB_PASSWORD，经容器内 mysql 客户端 TCP 连接容器自身网络地址，认证成功。未输出密码、Docker 环境或全文。宿主机无 mysql 客户端；不能直接照搬使用宿主机 mysql 的命令。
- document.id / chunk.id 为 signed BIGINT；file_hash VARCHAR(64) 有唯一索引；现有 document/chunk/evidence_ref/entity_alias 的列、索引及 FK 已读取，与新增表引用类型匹配。
- Qdrant transfer_chunks：green，462 points，1024 维，Cosine；collection 不重建。
- HTTPS 首页、legal、privacy、feedback、sources/16 均 200；公网 admin、admin/api 均 403，api/documents/4、api/import-tasks/1 均 404。
- 服务器 loopback 经既有 Basic Auth：GET /admin/ 与 /admin/api/documents/16 均 200。GET /admin/api/documents 返回 405，因为没有该列表 GET；不将其误报为鉴权故障。
- Nginx 公开 sources 为独立 GET/HEAD 代理；管理员仍 loopback allow + deny all + Basic Auth；双域名证书路径、443 与 HTTP 跳转配置保留。此次没有修改或 reload。

## Migration 审查

顺序：

1. evidence-import-task-schema.sql
2. evidence-readiness-schema.sql
3. evidence-index-snapshot-schema.sql

均为 additive DDL，无 DROP、DELETE、TRUNCATE、历史任务 backfill 或向量修改。CREATE IF NOT EXISTS 与 task_kind 列存在性判断支持正常结构重复执行，但不会修复同名错误表、缺失索引或错误字段类型。DDL 会隐式提交，不能声称三份 SQL 整体事务回滚。

task_kind 默认 UNKNOWN；读取门禁明确拒绝 UNKNOWN，即使状态 SUCCEEDED 也不获得发布权限。无任务的历史 Evidence 保持可见；LEGACY_VERIFICATION 不因核验失败下线；不会启动时为全库创建任务。

失败后保留已创建结构，逐列核验后在另一个批准窗口续执行；不得反向删除表或覆盖全库。新 JAR 必须在三份迁移全部完成后才能用 validate 启动。

## 默认关闭与发布限制

- app.import-tasks.enabled=false；Dispatcher / 执行线程池仅 true 时创建。上传/import/batch 在文件保存和登记前拒绝，HTTP 503，不创建新任务。
- **限制：ImportTaskController.retry 没有开关判断，关闭调度时仍可把已有 FAILED 任务重新置 QUEUED。** 首次发布任务表为空时不产生该问题，但有任务后的安全回退必须冻结管理员 retry/所有写入；仅关闭调度不够。后续可单独批准最小开关保护，不在此次冻结中偷偷修改业务代码。
- 手工 Evidence Chunk/Index/Delete 已禁止绕过冻结；Canonical 调用链保持现有逻辑。运维直接 SQL/Qdrant 写入、混跑旧 Worker 不属于冻结保证。
- Readiness Gate 始终生效，不依赖调度开关。未完成任务 Point 不参与问答，不能把门禁称为外部请求 fencing。
- 同一冻结快照复用 Chunk、vector、payload；没有 exactly-once 或 MySQL/Qdrant 跨库原子事务。

## 备份与恢复：后续需批准，当前未执行

已有备份目录已只读核验，包括 source-reader-20261008T062742Z（旧 JAR、前端、Nginx）与 HTTPS 备份目录。旧历史 gzip 中有一个仅 20 字节的失败备份，不可视为有效数据库备份。

正式发布前需要新的同一窗口备份，而不能把历史文件存在等同于恢复可行：

1. 冻结管理写入，核验单实例与在途请求；一致性 mysqldump 使用 --single-transaction --quick --routines --triggers --events --no-tablespaces，目标 transfer_rag。复用有效凭据，通过远程 Python 子进程环境 MYSQL_PWD + docker exec -i -e MYSQL_PWD，密码不放参数、不打印。dump 的文件权限 0600，目录 0700；严格检查退出码，不能仅以非空 gzip 成功判断。
2. gzip -t、解压检查 dump 完成标记与 DDL；在另一个受保护的隔离 MySQL 8 中恢复并核验表结构/计数/FK，再判可恢复。不得未经批准把生产内容搬入本地测试。
3. Qdrant POST /collections/transfer_chunks/snapshots 创建快照，GET snapshots 检查名称/大小并下载/hash；在受保护的隔离相同版本 Qdrant 恢复核验 count/config/point IDs。此次仅 GET collection，不创建快照。
4. 在服务停机后复制精确旧 JAR、Admin index/assets、systemd unit、环境文件（root-only）和完整 Nginx/证书引用配置至时间戳目录，记录 SHA-256/权限；不输出环境文件内容。此前配置备份不替代当前窗口备份。
5. 候选与回退 JAR 均从固定 commit 的 git archive 全新目录构建。不能部署根工作区 target JAR，因为 ignored application-local.properties 会被 Maven 打包。

回退 A：无新增任务/Chunk/Point，确认基线后可恢复准确旧 JAR，保留 additive 空表。

回退 B：任何任务或部分 Point 已产生，只使用带门禁与快照的兼容 JAR，tasks=false，并冻结全部管理员写入/retry，确认所有旧执行者退出。保留 Task/Chunk/Snapshot/Point；不能删除任务制造 Legacy，可疑 SUCCEEDED 完整性应停止问答进一步核验。若候选 JAR 本身故障且没有独立已验证的门禁兼容备用版本，保持维护停机，不恢复无门禁旧 JAR。

## 冻结范围与测试

提交限于异步导入 Java、测试、三份 SQL、管理端轮询及本文/验收/发布文档。原三个 Benchmark review 文件保持本地未跟踪且不提交；不纳入敏感配置、日志、数据库或构建产物。正式 commit、候选 JAR hash、固定源码构建与隔离运行结果见本轮最终交付记录。

当前生产发布仍需：批准后 push、维护窗口、实际一致性备份与隔离恢复证明、最终有效开关/validate 确认、管理写入冻结及有任务时 retry 防护。此次不执行上述生产操作。
