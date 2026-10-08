# Evidence 原文阅读

## 权限与接口

- SourceCard → `/sources/{documentId}?page={sourcePage}`。未记录页码时省略 `page`，不猜页码。
- `GET /api/public/sources/{id}`：仅返回通过审核的 Evidence 的阅读元数据。
- `GET /api/public/sources/{id}/file`：完整 PDF，inline、Range 支持、nosniff、no-store。
- `GET /api/public/sources/{id}/text`：完整 UTF-8 Markdown/TXT，以 JSON 传送，前端用转义的 `<pre>` 只读显示；不执行 HTML、不加载远程图片。
- 没有公开列表、上传、删除、编辑接口。未授权/不存在/Canonical 均返回同一类 404；文件缺失则友好提示，不接入或影响 RAG。
- 不复用管理接口；Nginx 仅增加 `/api/public/sources/` GET/HEAD 代理，不开放旧文档接口或管理接口。

## 批量授权清单（默认空，新增资料不会自动公开）

使用外部 JSON 文件，不改 MySQL schema、Document 数据或 Qdrant。
设置应用属性 `app.sources.public-manifest` 指向审核清单；默认未配置即拒绝全部。
`config/public-source-manifest.example.json` 是空清单示例，不含任何实际授权。

确认权利人允许**在本站公开展示完整原文**后，可一次加入多条记录：

```json
{
  "schemaVersion": 1,
  "documents": [
    {
      "documentId": 123,
      "sha256": "填写原始文件的64位SHA256",
      "authorizationStatus": "APPROVED",
      "authorizationNote": "填写许可依据、适用范围和可追溯记录位置",
      "reviewedBy": "填写审核人",
      "reviewedAt": "2026-10-08"
    }
  ]
}
```

上例只是格式，不是有效授权。不要因为 PERSONAL、OFFICIAL_PDF、公开群或公开网页来源而自动批准。
不要在可公开的代码仓库中存放私人授权邮件、签名、聊天记录或其他个人信息。
对每一份原始文件单独核验授权与隐私内容，再将确认后的多条记录批量加入同一清单。

清单按请求重读：撤下条目即可停止后续服务端读取；已交付到浏览器的文件无法追回。
SHA-256 绑定实际文件内容，文件更换/ID 复用不能沿用原授权。
清单无效、重复 ID、缺少审核依据或未知字段均 fail closed。
建议外部清单由维护者只写、服务用户只读，并通过原子替换发布。当前未确认可公开分发的文件，生产授权清单保持空/未配置，全部拒绝。
文件只从既定 upload-dir 中解析，校验真实路径，拒绝越界及指向目录外的符号链接。
PDF 限制 50 MiB，文本限制 2 MiB；不会截断文档冒充全文。

## 预览能力与限制

- PDF：完整文件，浏览器原生内嵌阅读器；`#page=N` 尽可能定位引用页，浏览器/移动端未必支持。提供新窗口阅读入口。
- Markdown/TXT：完整原始文本；Markdown 标记不渲染，也不执行其中脚本。只支持有效 UTF-8，不默默替换乱码。
- DOCX：不做网页转换；仅在公开授权、DocumentRole=EVIDENCE、路径边界及 SHA-256 全部验证通过后，提供原始 `.docx` 附件下载。保留安全的原始文件名；不是网页全文预览。
- 文件不公开时，不返回标题、绝对路径、原始存储文件名、授权记录或文件内容。
- 开放全文阅读必然允许用户保存已经公开的文件；并非防下载系统。

## 生产只读盘点（2026-10-08）

通过现有 localhost 管理接口读取 Document 1–28 的元数据，在服务器端检查文件，不复制原始资料；
结合已有 `canonical-source-map.json` 的 20 个 Evidence ID 和只读 Qdrant payload 进行分类复核。
当前管理 DTO 没有 documentRole。此前容器本地 socket 只读连接触发 1045；随后复用运行中应用凭据，经 MySQL TCP 路径读取表结构和目标角色成功，未改凭据、用户或数据库。
Qdrant 共扫描 462 points（无 vector），6、22–28 为 CANONICAL，9 无索引 point，不能仅依靠 Qdrant 枚举全部文件。
以下为现有导入清单和实际文件核验结果；20 个目标 DocumentRole 均已通过 MySQL TCP 只读查询核实为 EVIDENCE。新读取接口仍逐请求以数据库 DocumentRole 为最终门禁。

已知现有 Evidence **20** 份；文件存在 **20**；支持在线完整阅读 **17**（11 PDF、6 Markdown）；支持原件下载 DOCX **3**。
元数据 sourceUrl 全部为空。尚未提供任何再次公开展示授权记录，因此 **20** 份全部待确认，本轮批准公开数 **0**。
“支持在线阅读/下载”表示格式和文件条件具备，不代表已开放访问，也不等同于已逐文件检查 PDF 浏览器渲染或内容隐私。

| ID | 标题 | 来源类型 | 格式 | 文件 | 原发布链接 | 展示授权 |
|---|---|---|---|---|---|---|
| 1 | 2026转软件工程常见问题 | PERSONAL | MD | 存在 | 未记录 | 待确认 |
| 2 | 2026转软件工程机考准备 | PERSONAL | MD | 存在 | 未记录 | 待确认 |
| 3 | 2026转软件工程面试情况 | PERSONAL | MD | 存在 | 未记录 | 待确认 |
| 4 | 2026转软件工程申请要求 | PERSONAL | MD | 存在 | 未记录 | 待确认 |
| 5 | 2026转软件工程时间安排 | PERSONAL | MD | 存在 | 未记录 | 待确认 |
| 7 | 2026智能科学与技术培养方案 | OFFICIAL_PDF | PDF | 存在 | 未记录 | 待确认 |
| 8 | 法学院转专业指南 | PERSONAL | DOCX | 存在 | 未记录 | 待确认 |
| 9 | 文学院汉语言文学转专业指南 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 10 | 人文大类汉语言文学分流指南 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 11 | 化学与生命科学类生存手册 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 12 | 地球科学与资源环境大类生存指南 | PERSONAL | DOCX | 存在 | 未记录 | 待确认 |
| 13 | 南京大学赫尔辛基大气学院生存手册 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 14 | 转电子指南 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 15 | 转光电试验班概述 | PERSONAL | DOCX | 存在 | 未记录 | 待确认 |
| 16 | 转计算机科学与技术专业的难处 | PERSONAL | MD | 存在 | 未记录 | 待确认 |
| 17 | 技术科学试验班生存指南 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 18 | 数学学院生存手册 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 19 | 数理科学类生存指南 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |
| 20 | 南京大学2026转专业准入计划表 | OFFICIAL_PDF | PDF | 存在 | 未记录 | 待确认 |
| 21 | 匡亚明学院生存指南 | PERSONAL | PDF | 存在 | 未记录 | 待确认 |

## 发布边界

只为此功能开放 `/api/public/sources/` 的 GET/HEAD 代理；保留 `/api/documents/` 的 404 规则及 `/admin/`、`/admin/api/` 的本机和 Basic Auth 限制。不要开放整个 `/api/`。
公开清单只加入已经核实可在本站再分发的文件；未确认授权时必须维持空清单和默认拒绝。

## 本地测试

- Java 测试：临时目录、模拟 Repository、独立 MockMvc；无生产数据库、无模型调用。
- Web：`npm test`（Node 内置测试 + Vite SSR）、`npm run build`；不引入新 UI/测试框架。
- `node tests/previewPublicSources.mjs` 提供本地 5174 端口的隔离 UI fixtures（ID 900001–900004），拦截所有 `/api/` 请求，不连接任何真实服务。用于检查文本、PDF frame、DOCX 提示和未授权提示，不能用作真实知识资料。
- 原文阅读相关 Maven：30 项中 29 通过、1 跳过、0 失败；跳过项是 Windows 无符号链接创建权限限制。
- Web 测试 10/10 通过；build 通过。浏览器验证文本末段、HTML 转义、无外部图片节点，以及桌面/手机无横向溢出。
- PDF 文件响应、Range 206 和页码 URL 已测试；当前 Codex 内嵌浏览器只显示深色 PDF frame，未能确认原生 PDF 渲染。需要在普通 Chrome/Edge 的原生阅读器补做视觉验收；页面保留新窗口阅读入口。本轮不声称已逐份验证 11 个生产 PDF 的渲染。
