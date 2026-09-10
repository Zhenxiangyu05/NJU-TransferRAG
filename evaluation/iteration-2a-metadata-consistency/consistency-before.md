# Metadata Consistency — Before

采集时间：2026-09-08。数据来自 MySQL、Qdrant `transfer_chunks` 和新增只读 endpoint `GET /api/test/metadata-consistency/{documentId}`。

## Baseline gate

- Answerability 三个 Iteration 1 文件相对当前 Git baseline 无 diff，当前为 Baseline V1。
- `evaluation/iteration-1-answerability/` 的 comparison、report、results、failures 四个文件完整保留。
- 生产代码中未发现 candidateTopK、Neighbor Expansion、authority rerank 等 Retrieval V2 实现。

## doc13

- MySQL Document：department=`现代工程学院`，year=2026，scope=`DEPARTMENT`，sourceType=`COMMUNITY`。
- MySQL Chunk：2 个（chunkId 87、88）；department、major、policyYear 均为 NULL。
- Qdrant：2 个 point，与 MySQL Chunk 数量及 chunkId 一一对应。
- Qdrant department=`现代工程学院`、year=2026、scope=`DEPARTMENT`、sourceType=`COMMUNITY`。
- Qdrant chunkDepartment、major、policyYear、effectiveYear 缺失。
- Consistency endpoint：`MISMATCH`，2 个 blocking issue（两个 point 均缺 effectiveYear），4 个 MySQL optional metadata warning。
- Canonical semantic issue：学院名称应统一为 `现代工程与应用科学学院`。

原始 DOCX 和已解析全文均没有出现完整字符串“光电信息科学与工程”或“光电系统信息材料实验班”；只明确出现“光材二次拔尖班”“转光电”“现工院”。因此本轮不会凭推断写入 canonical major/program，major 保持 NULL，并在 after 报告中保留 warning。

## doc14

- MySQL Document：department=`电子信息类`，year=2026，scope=`DEPARTMENT`，sourceType=`PERSONAL`。
- MySQL Chunk：13 个（chunkId 89～101）；department、major、policyYear 均为 NULL。
- Qdrant：13 个 point，与 MySQL Chunk 数量及 chunkId 一一对应。
- Qdrant department=`电子信息类`、year=2026、sourceType=`PERSONAL`。
- Qdrant chunkDepartment、major、policyYear、effectiveYear、scope 缺失。
- Consistency endpoint：`MISMATCH`，26 个 blocking issue（13 个 effectiveYear missing + 13 个 scope missing），26 个 MySQL optional metadata warning。
- Canonical semantic issue：`电子信息类` 是原文明确支持的专业大类，不应放在 department；学院应为 `电子科学与工程学院`。

## TRAG-070

`转电子指北.pdf` 在原始资料目录存在，但 MySQL/Qdrant 没有对应 Document/Chunk。本轮不导入，继续记录为 `Index/Data Quality — missing document`。
