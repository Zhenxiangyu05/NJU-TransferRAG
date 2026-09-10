# NJU Compass 知识库管理

NJU Compass 管理端负责知识库文档导入与导入状态展示，不承担用户问答功能。

## 功能

- 上传 PDF、DOCX、Markdown、TXT 文件。
- 填写资料标题、Department、Year、SourceType 与 Scope metadata。
- 展示文件类型、大小和导入进度。
- 调用后端完成解析、Chunk、Embedding 与索引流程。
- 展示 duplicate detection 结果，避免重复创建 Document。
- 展示 Document ID、Chunk 数量与索引状态。

请仅将学校或院系正式发布的资料标记为官方来源。

## 本地开发

```bash
npm install
npm run dev
```

开发服务器将 `/api` 请求代理到本地后端。联调前请确认后端、MySQL、Ollama 和 Qdrant 可用。

## 生产构建

```bash
npm install
npm run build
```

构建产物输出到 `dist/`，该目录不应提交到 Git。
