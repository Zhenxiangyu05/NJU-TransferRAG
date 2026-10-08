# Evidence 异步导入：最后两项发布阻塞修复验收

日期：2026-10-09。范围仅本地依赖与任务写入保护、测试、提交和候选构建。未 push，未连接生产 MySQL/Qdrant，未执行生产 Migration/部署/重启。

旧候选 `45d2de4ec746352cd4ddff0d176d7dbad2d96d10`、旧 JAR 与 `target/releases/45d2de4ec746/VERIFICATION.md` 保留。本轮最终版本以本文件所在新 commit 及新目录 VERIFICATION.md 的 SHA-256 为准；不复用旧候选作为最终产物。

## 1. 管理端依赖

实际 npm audit 公告：GHSA-68fv-2mgg-jv7q，indexed source-map section offsets 导致 event-loop denial of service；受影响 `>=1.0.0 <1.2.2`。不是根据包名推断。

实际引入路径：Vue compiler-core/compiler-sfc，以及 Vite → PostCSS。执行 `npm update source-map-js --package-lock-only --ignore-scripts`，lockfile 仅修改该 package 的 version/resolved/integrity：1.2.1→1.2.2。不改 package.json、Vue/Vite 版本、页面布局或其他依赖。

`npm ci`、`npm audit`、`npm audit --omit=dev`、`npm ls source-map-js`、`npm test`、`npm run build` 实际完成：所有路径去重至 1.2.2，High=0，Critical=0，总漏洞=0；管理端 6 PASS、build PASS。没有执行 audit fix --force。npm 的 esbuild allow-scripts 提醒不是漏洞，也没有擅自修改全局执行策略。

## 2. 统一开关保护

新增 ImportTaskFeatureGuard；默认 false，实例启动时固定，不伪装成运行时热切换。

- DocumentImportService：保存文件前检查（普通、批量、旧上传入口复用）。
- EvidenceRegistrationService.register/resolveHash：任何查询/写入 Document/ImportTask 前检查，包括 Legacy 核验任务创建。
- ImportTaskStateService：create、retry、claim、heartbeat、recoverExpired、requireOwned 检查；stage/succeed/fail 及 Chunk/快照持久化复用 requireOwned，同时保留 owner_id/lease_generation 检查。
- 关闭时统一 HTTP 503 / code=IMPORT_TASKS_DISABLED / 安全中文业务提示；异常在任何写入之前抛出，不先改数据库后报错。
- GET 任务状态与只读 readyIds 可用；状态/检查点不删除，关闭时不会自动领取任务。
- Dispatcher/线程池仍条件注册；即使手动调用 Worker，claim 也被拒绝，处理器不执行。
- Readiness Gate 与开关无依赖；Legacy 可见，UNKNOWN 即使 SUCCEEDED 也不可见。
- Canonical 原同步逻辑不改。既有手工 Evidence Chunk/Index 删除式路径仍由 Service 冻结保护拒绝，不因开关关闭而放开。

## 3. 实际回归

| 验证 | 实际结果 |
|---|---|
| Maven 全套 | 312 discovered；309 PASS / 0 FAIL / 0 ERROR / 3 SKIP |
| MySQL 8 | 19 PASS，含关闭开关时真实 SQL 状态/检查点不变 |
| Qdrant raw 对照 | 2 PASS |
| MySQL/Qdrant 快照与延迟旧写入 | 4 PASS |
| 真实 JVM kill/restart | 1 PASS |
| Canonical | 31 PASS |
| RAG/Retrieval | 30 PASS |
| 新开关 Service 单测 | 6 PASS |
| 新开关 HTTP 单测 | 2 PASS |
| Admin | 6 PASS / build PASS / audit 0 |

新增真实 MySQL 测试先在启用状态登记并保存 INDEXING 快照、标记 FAILED，然后以另一个 validate-only ApplicationContext 复用该隔离数据库、关闭开关。retry/claim/recover/register/resolveHash 均拒绝；完整 Task 行、快照行、Document/Chunk 数量完全不变，GET/view 正常。不是 H2 或 Mockito 替代 MySQL。

初次新单测的 Legacy projection 使用 Mockito 默认 Long=0，导致“无任务” fixture 不准确；明确 stub taskId=null 后全套通过，未改变门禁实现。3 SKIP 仍为两个 opt-in Benchmark 和一个 Windows symlink 条件跳过，不属于数据库/Qdrant 验收跳过。

## 4. 固定版本构建与安全降级验收

先检查 diff、敏感字面量与提交范围，再创建本地 commit；三个未跟踪 Benchmark review 文件不纳入，保留原哈希。只用该 commit 的 git archive 全新目录执行 Maven package（含全套测试），不复制 ignored application-local.properties / target / 上传资料。正式产物需 ZIP 排除敏感配置并登记 JAR/admin archive SHA-256。

另用已有只含生产 DDL 的本地结构副本和合成数据，运行固定候选的真实 executable JAR，验证三份 Migration 重复执行、ddl-auto=validate、默认关闭调度、GET 任务查询、HTTP retry/import 503 且 SQL 不变、Legacy/SUCCEEDED 检索和 Source Reader。诊断代码、结果和新产物版本记录保存至新 target/releases 目录，不覆盖旧版本。本地 Provider stub 仅验证传输/门禁，不声称额外真实模型质量验收。

安全降级使用这份实际启动验证的加固 JAR，tasks=false，保留 Task/Snapshot/Chunk/Point 和 Readiness Gate。它不是独立旧兼容版本；若此 JAR 自身故障，没有另一已验证门禁兼容 JAR 时须保持维护停机，不恢复无门禁旧 JAR。

## 5. 发布门槛

三份 Migration 未修改，顺序 task→readiness→snapshot，全部 additive，UNKNOWN 默认拒绝，无历史任务 backfill，正式 ddl-auto=validate。旧历史文档不会自动重新导入。

本地依赖与 disabled-write 阻塞已修复；实际发布仍需下一轮明确批准 push、生产一致性备份与受保护隔离恢复验证、维护窗口、Migration 和切换。默认关闭的只读上线与开启处理/受控试导入分别审批；不运行完整 Gold Benchmark。

不宣称外部 Qdrant 版本 fencing、exactly-once 或跨库强一致；冻结快照保证受控重放同内容，不能防止直接运维写入或旧版本混跑。
