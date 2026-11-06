# 二期路线（Roadmap）

> 原则：**不自建轮子**。解析、OCR 等已有优秀方案的领域，直接评估选用成熟工具替换转换器插槽；
> 本系统专注"导入 → 知识管理 → 双索引检索 → RAG 问答 → MCP 外挂"这条主链路。

## P0 工程质量（开源门面）

- [ ] GitHub Actions CI（`mvn test` 需 Docker runner，Testcontainers 拉 pgvector）
- [ ] e2e 清单剩余项走查（xlsx/pptx 实文件、图片/音频导入实测）
- [ ] README 徽章、界面截图、CONTRIBUTING.md
- [ ] 知识库导出：全部知识文档打包为 MD bundle（防锁定、可迁移）

## P1 检索与问答质量

- [ ] 中文全文检索升级：`ILIKE` 子串匹配 → PG 全文索引 / jieba 分词（现中文无分词、无词形）
- [ ] 重复导入的"重新提炼"：同内容已导入后可切换 RAW↔DISTILL 模式重导（现需删除导入记录再传）
- [ ] 父子块 / 句子级引用：答案引用定位到原文句子，详情页点击高亮
- [ ] 多轮对话：会话历史与追问消解（现单轮 RAG）

## P2 导入源扩展

- [ ] JS 渲染页面抓取：headless 浏览器（Playwright）或 Reader 服务
- [ ] 跨维度 embedding 模型：动态维度迁移（现固定 1024 维）
- [ ] 邮件 / 视频导入（spec 排除项，按需）

## 待选工具（不自建，找到即换）

转换器链已预留插拔位（`pkb.converter.*` + 按扩展名路由），选定工具后只改命令与路由，不动主链路。

| 领域 | 候选 | 评估标准 |
|---|---|---|
| PDF 解析 / OCR | Docling（Apache-2.0）/ MinerU（AGPL）/ Marker / Unstructured / 商业 LlamaParse | 中文版面与表格保真度、扫描件 OCR、CPU 可跑、许可友好 |
| 图片中文字 | 随 PDF 方案一并评估（PaddleOCR 等） | 中文识别率、部署重量 |

## 不做（spec 明确排除，记录备查）

多用户与权限、MCP stdio 传输、批注式 AI 重写、主题聚合/知识图谱。
