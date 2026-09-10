# NJU Compass 用户端

NJU Compass 用户端是“南大校园知识助手”的问答界面，用于提交校园学习与院系问题，并展示后端基于知识库生成的带来源回答。

## 功能

- 校园知识问答与推荐问题。
- Markdown 回答安全渲染。
- `[S1]`、`[S2]` 等 citation 展示。
- Source Card 展示资料标题、来源类型、学院、专业和年份。
- 支持访问可用的原始资料或来源页面。
- 适配 Desktop、Tablet 与 Mobile。
- 保留非官方工具声明和重要事项提示。

## 本地开发

```bash
npm install
npm run dev
```

开发服务器将 `/api` 请求代理到本地后端。启动前请确认 NJU Compass 后端及其依赖服务可用。

## 生产构建

```bash
npm run build
```

构建产物输出到 `dist/`，该目录不应提交到 Git。
