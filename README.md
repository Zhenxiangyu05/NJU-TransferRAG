# NJU Compass / 南京大学校园知识助手

面向南京大学校园资料的可信知识检索与问答系统。用户可以查询转专业、培养方案等已收录资料，获得带来源的回答；资料不足时，系统拒绝猜测。本项目并非南京大学官方服务，重要事项请以学校及院系最新通知为准。

## Online Demo

[南京大学校园知识助手](http://124.220.159.31/)

知识库管理端采用独立鉴权，不公开凭据。

## V2: Canonical Knowledge + Evidence

```text
User Query
    ↓
Query Rewrite / Entity Resolution / Metadata Filter
    ↓
Canonical Retrieval → Relevance Gate → Answerability
    ├─ 依据充分 → Answer → Evidence Citation
    └─ 依据不足 → Evidence Retrieval → Answerability
                                  ├─ 依据充分 → Answer → Evidence Citation
                                  └─ 依据不足 → 拒答
```

`CANONICAL` 是经人工或 AI 整理并审核的高密度知识单元，优先用于检索；`EVIDENCE` 是原始 PDF、DOCX、Markdown、TXT 资料。Canonical 的每条 fact 通过 `EvidenceRef` 关联原始 Evidence Document，因此最终 Citation 指向证据，而不是把整理知识卡当作官方原文。旧版缺少 `documentRole` 的向量仍按 Evidence 兼容。

文档链路：导入 → 解析 / OCR → Chunk（Canonical 按 section 和 fact 构建）→ 远程 `bge-m3` Embedding → Qdrant；MySQL 保存文档、Chunk、EvidenceRef 与元数据。问答链路结合 `department`、`major`、`policyYear`、`cohortYear` 等过滤条件，通过 Relevance Gate 和 Answerability 检查后生成回答。Canonical 不足时回退到原始 Evidence；同一查询的 Embedding 在两阶段复用，重复的 Evidence Citation 会去重。

### Safety and reliability

- 没有充分证据时 fail-closed，不用模型常识补全校园政策。
- Canonical 的 `sourceType=CURATED`，不冒充 `OFFICIAL`；引用追溯到真实 Evidence。
- `policyYear`（政策年份）、`cohortYear`（适用年级）与 `effectiveYear`（检索年份）保持不同语义。
- `app.rag.canonical-first-enabled` 可关闭 Canonical-first，恢复 V1 Evidence-only 检索路径。
- 真实资料可能过时或不完整；Citation 方便核验，但不替代官方通知。

## Technology and production architecture

| Layer | Stack |
|---|---|
| Backend | Java 21、Spring Boot、Spring AI |
| Data / retrieval | MySQL 8、Qdrant |
| AI | 远程 `bge-m3` Embedding、OpenAI-compatible Chat API |
| Frontend | Vue、Vite（用户端与独立管理端） |
| Deployment | Docker Compose、Nginx、systemd、腾讯云 |

```text
Internet
   ↓
Nginx :80 ── Vue static / protected admin
   └── /api/ → Spring Boot 127.0.0.1:8080
                    ├── MySQL 127.0.0.1:3306
                    ├── Qdrant 127.0.0.1:6333/6334
                    └── Remote AI APIs
```

MySQL、Qdrant 和 Spring Boot 不直接暴露公网。管理端由 Nginx Basic Auth 保护。Spring Boot 由 systemd 托管，MySQL / Qdrant 由 Docker Compose 托管并配置自动恢复。当前部署运行在约 2C4G 云服务器上；本地 Ollama 曾导致内存压力，生产 Embedding 已迁至远程 API。上线过程包含 MySQL 备份、Qdrant snapshot、幂等 schema migration 与旧向量 payload backfill；这些操作不是日常发布步骤。

## Version evolution

- **v1.0.0**：Raw Evidence RAG。原始资料 → Chunk → 向量检索 → 回答 → Citation。
- **v2.0.0**：Canonical-first Retrieval + Evidence fallback。fact-level provenance 与 Evidence Citation 减少重复资料和原始 Chunk 噪声，同时保留旧资料覆盖能力。

## Repository layout

```text
src/main/            Spring Boot、SQL migration
src/test/            自动化测试
transfer-rag-web/    用户端
transfer-rag-admin/  受保护的知识库管理端
scripts/             部署与维护脚本
evaluation/          离线评测与回归记录
```

## Local development

需要 Java 21、Node.js / npm、MySQL 和 Qdrant，以及可用的 OpenAI-compatible Chat / `bge-m3` Embedding 服务。复制根目录 [`.env.example`](.env.example) 中的变量名到本地私有环境配置，填入自己的值；不要提交真实凭据。`application.properties` 默认使用 localhost MySQL / Qdrant，但上传路径为 Linux 生产路径，本地运行时应覆盖 `APP_UPLOAD_DIR` 或相应配置。仓库中的 Docker Compose 引用服务器 `/etc/` 环境文件，不能直接当作无配置的本地启动命令。

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

用户端或管理端分别进入 `transfer-rag-web/`、`transfer-rag-admin/`，执行 `npm ci`、`npm run dev`。生产构建使用 `npm run build`。

## Deployment

通过 Git 发布，经测试并推送后，在已配置好服务环境文件的生产服务器执行：

```bash
git pull --ff-only origin main
./scripts/deploy.sh
```

部署脚本构建后端及两套前端、检查 Nginx、重启后端并运行 HTTP smoke test。数据库 migration、Qdrant backfill 和 Canonical 导入均需单独审查，不属于日常 `deploy.sh`。不要将 API Key、数据库密码、Basic Auth 密码、上传资料或备份提交到 Git。
