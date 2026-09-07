# Transfer RAG Admin

独立的 Vue 3 + Vite 知识库文档导入管理页面。

## 本地启动

```bash
npm install
npm run dev
```

开发服务器会将 `/api` 请求代理到 `http://localhost:8080`。联调前请先启动
Spring Boot 后端及其依赖的 MySQL、Ollama 和 Qdrant。

## 生产构建

```bash
npm run build
```

构建结果输出到 `dist/`。
