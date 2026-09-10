# Metadata Consistency — After

核验时间：2026-09-08。使用 `GET /api/test/metadata-consistency/{documentId}`、MySQL 只读查询和 Qdrant scroll 交叉验证。

## doc13

- Consistency status：`OK`
- MySQL Document：department=`现代工程与应用科学学院`，year=2026，scope=`DEPARTMENT`，sourceType=`COMMUNITY`
- MySQL Chunk：2 个；chunkDepartment 对应字段均为 `现代工程与应用科学学院`
- Qdrant Point：2 个，与 MySQL Chunk 按 chunkId 一一对应
- Qdrant metadata：department=`现代工程与应用科学学院`，chunkDepartment=`现代工程与应用科学学院`，year=2026，effectiveYear=2026，scope=`DEPARTMENT`，sourceType=`COMMUNITY`
- Blocking issues：0
- Warnings：2；chunk87、88 的 major 仍为 NULL。原始 DOCX 未出现可靠 canonical 专业/实验班全称，因此按“不凭常识猜测”要求保留为空；MySQL 与 Qdrant 在该字段一致。

## doc14

- Consistency status：`OK`
- MySQL Document：department=`电子科学与工程学院`，year=2026，scope=`DEPARTMENT`，sourceType=`PERSONAL`
- MySQL Chunk：13 个；department 均为 `电子科学与工程学院`，major/program 均为原文明示的 `电子信息类`
- Qdrant Point：13 个，与 MySQL Chunk 按 chunkId 一一对应
- Qdrant metadata：department=`电子科学与工程学院`，chunkDepartment=`电子科学与工程学院`，major=`电子信息类`，year=2026，effectiveYear=2026，scope=`DEPARTMENT`，sourceType=`PERSONAL`
- Blocking issues：0
- Warnings：0

## Reindex idempotency

使用现有 `POST /api/documents/{id}/index` 流程连续执行两次：

- doc13：每次 indexedChunks=2；最终 MySQL Chunk=2，Qdrant Point=2
- doc14：每次 indexedChunks=13；最终 MySQL Chunk=13，Qdrant Point=13

第二次 reindex 后 point 数量未增长，没有重复 Document、Chunk 或 Qdrant Point。

## Consistency checker coverage

只读检查覆盖 documentId、document.department、document.year、scope、sourceType、chunkId、chunk.department、chunk.major、policyYear，以及 Qdrant 的 documentId、chunkId、department、chunkDepartment、major、year、policyYear、effectiveYear、scope、sourceType。输出 `OK` 或具体 `QDRANT_MISSING`、`MISMATCH`、重复/额外 point，并将无法可靠填写的 MySQL optional metadata 单列为 warning。

## Tests

- `MetadataConsistencyServiceTest`：通过
- Maven 全量：16 suites，84 tests，0 failures，0 errors，0 skipped
