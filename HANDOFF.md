# HANDOFF — 交接说明（先读我）

> 给下一个会话的执行者（无论是 AI 代理还是彭川本人）：本文件是项目的唯一入口。
> 上一会话已完成需求探讨、设计评审与实施计划编写，本目录接手后直接按计划执行编码。

## 项目一句话

个人知识库系统（PKB）：把本地文档（docx/PDF/txt/md）和收藏网页用 LLM 提炼为结构化知识文档，支持人工编辑纠错（版本管理）、PGvector 混合检索、内置 RAG 问答，并以 MCP Server 外挂到 Claude Code / Kimi Code。Java 17 + Spring Boot 3 + Spring AI 模块化单体，Vue 3 前端，Docker Compose 部署。2 周冲刺，全程用 AI 辅助开发，最终开源到 GitHub（同时作为求职作品集）。

## 关键文件

| 文件 | 作用 |
|---|---|
| `docs/superpowers/specs/2026-11-01-personal-knowledge-base-design.md` | 设计文档（已获用户批准），需求的最终依据 |
| `docs/superpowers/plans/2026-11-01-personal-knowledge-base.md` | 实施计划（20 个 Task，含完整代码与 TDD 步骤），执行的最终依据 |

**两份文件已经过作者自查和用户评审，不要重新设计；发现计划与现实冲突时，按"执行提示"处理并记录偏差。**

## 如何开始

1. 本目录 `/Users/johnpeng/Code/pkb` 已 `git init`，上述两份文档已提交。
2. 打开实施计划，从 **Task 1: 项目骨架与基础设施** 开始，严格按 Task 顺序执行；每个 Task 末尾必须 commit，测试不通过不进入下一 Task。
3. 推荐执行方式：**子代理驱动**（每个 Task 派一个子代理实现，任务间审查）；也可会话内联执行。
4. 前置环境检查：Java 17、Maven 3.9+、Node 20+、Docker 运行中。

## 已锁定的关键决策（不要推翻，有冲突先记录再请示）

- 技术栈：Spring Boot 3.4 + **Spring AI**（非 LangChain4j，因官方 MCP starter）；PostgreSQL 16 + PGvector 单库；Vue 3 + Element Plus + md-editor-v3。
- 向量维度固定 **1024**；可切换的 embedding 模型必须同维度，跨维度是二期。
- 检索（含 RAG、MCP）**默认仅查 CONFIRMED 文档**，参数可放宽——这是"人工确认"质量闸门。
- 提炼完成即生成 v1 草稿并**立即首次向量化**；编辑保存 = 新版本快照 + 重新向量化；回滚 = 历史版本另存为新版本。
- 模型层一套 OpenAI 兼容实现；**Ollama 走其 OpenAI 兼容端点**（`http://localhost:11434/v1`）接入，这是有意简化。
- 异步任务用数据库任务表 + 线程池（**不上消息队列**）；异步触发用事务 afterCommit 回调。
- MVP 明确不做：邮件/音视频、JS 渲染页面抓取、主题聚合、批注式 AI 重写、多用户、MCP stdio、重复导入的重新提炼。
- 界面与错误文案用中文；commit 用 Conventional Commits。

## 注意事项

- **不要把真实 API key 提交进仓库**；`.env` 已在 `.gitignore`。`.env.example` 里的默认端点是 Kimi（`api.moonshot.cn`），可换任何 OpenAI 兼容服务。
- 依赖版本（Spring Boot 3.4.5 / spring-ai 1.0.1 / tika 2.9.2 等）若拉取失败或 API 不兼容，升级到最新稳定版并在 commit message 中记录偏差。
- 集成测试用 Testcontainers（需 Docker 拉取 `pgvector/pgvector:pg16`），首次较慢属正常。
- 计划中"重新向量化失败"只做了"保留旧索引 + 日志 + `/api/admin/reindex` 手动重建"，没有单独 UI 异常标记——这是已确认的取舍，不要补做。
- 发布前跑一遍 `docs/e2e-checklist.md`（Task 20 会创建）。
- **推送 GitHub 由彭川本人执行**，不要代为 push。

## 交接时的执行状态

- 需求/设计/计划：全部完成 ✅
- 编码：未开始，下一个会话从 Task 1 开始。
