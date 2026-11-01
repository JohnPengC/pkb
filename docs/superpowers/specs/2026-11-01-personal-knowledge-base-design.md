# 个人知识库系统（PKB）设计文档

- 日期：2026-11-01
- 状态：待评审
- 作者：彭川（AI 辅助协作）

## 1. 背景与目标

### 1.1 项目动机

个人本地积累了大量资料（文档、收藏网址等），内容分散、难以检索利用。本项目将这些资料解析、提炼为结构化的"知识文档"，形成可持续维护的个人知识库，并作为 AI 工具的外挂知识源使用。

同时，本项目作为求职作品集：验证并展示"Java 后端 + AI Agent 全栈"能力，覆盖 Spring AI、RAG、PGvector、MCP Server、AI 辅助开发流程等关键词，全程使用 Codex + Kimi Code 辅助开发并开源。

### 1.2 目标（MVP）

1. 支持本地文档（docx/PDF/txt/md）和收藏网址两类输入源，解析原文并由 LLM 提炼为 Markdown 知识文档，一源一文档，可溯源。
2. 提供 Web 界面查看知识文档，支持在线编辑纠错，保存自动重新向量化，保留版本历史（diff/回滚）。
3. 提供两种知识消费方式：MCP Server（供 Claude Code / Kimi Code 等外挂调用）和内置 RAG 问答界面。
4. 模型层做 Provider 抽象，云端 OpenAI 兼容 API 与本地 Ollama 可配置切换。
5. Docker Compose 一键部署，代码开源至 GitHub。

### 1.3 非目标（MVP 明确不做）

- 邮件、音视频输入源（二期）
- 需要 JS 渲染 / 强反爬页面的抓取（二期可接 Playwright）
- 主题自动聚合、wiki 式多源合并
- 批注式 AI 重写纠错（二期）
- 多用户、权限体系（单用户本地应用）
- MCP stdio 独立进程模式（MVP 仅 Streamable HTTP）
- 重复导入的重新提炼（重复导入仅提示已存在；重新提炼与人工编辑存在冲突，二期设计）
- 跨向量维度的 embedding 模型切换（MVP 固定 1024 维，见 3.2）

### 1.4 约束

- 开发节奏：全职投入，2 周冲刺交付可演示版本。
- 技术栈：Java 17 + Spring Boot 3.x + Spring AI（锁定，理由见 2.5）；前端 Vue 3。
- 全程使用 Codex + Kimi Code 辅助开发，开发过程本身也是简历素材。

## 2. 总体架构

### 2.1 架构形态

模块化单体：一个 Spring Boot 应用，按模块分包，各模块通过清晰的 Java 接口通信。PostgreSQL + PGvector 单库同时承载业务数据与向量。Docker Compose 编排 app + postgres（Ollama 可选外挂）。

取舍：相比微服务，2 周内可把精力集中在可演示的功能亮点上；模块边界清晰，未来可拆分。

### 2.2 模块划分

| 模块 | 职责 |
|---|---|
| `ingestion` | 文件上传（docx/PDF/txt/md）、URL 抓取；文本抽取（Apache Tika 统一处理文档，jsoup 处理网页） |
| `distillation` | 调用 LLM 把原文提炼为结构化知识文档（标题、摘要、要点、关键术语、溯源引用）；Prompt 模板化；超长文档分段提炼后汇总 |
| `knowledge` | 知识文档 CRUD、标签、版本历史（每次保存存快照，diff 按需计算，支持回滚） |
| `retrieval` | 文档切分（chunking）、embedding、PGvector 存储、向量检索 + 关键词模糊匹配混合检索 |
| `chat` | RAG 问答：检索 → 组装 Prompt → 流式输出 → 标注引用来源 |
| `mcp` | 基于 Spring AI MCP Server（Streamable HTTP），暴露 `search_knowledge`、`get_document`、`list_documents` 三个工具 |
| `provider` | 模型抽象层：Chat 与 Embedding 的 Provider 接口，云端 OpenAI 兼容 API 与本地 Ollama 可配置切换；embedding 模型标识管理 |

### 2.3 数据流

```
上传/URL ──► 文本抽取 ──► LLM 提炼 ──► 知识文档(草稿 v1) ──► 人工编辑/确认
                                   │                        │ 保存
                                   └──► 首次向量化 ◄── 重新切分+Embedding
                                                            │
                                              PGvector（检索默认仅 CONFIRMED）
                                                            │
                                    ┌───────────────────────┼───────────┐
                                 RAG 问答               MCP Server    文档查看
```

### 2.4 前端页面（Vue 3 + Element Plus）

- 上传/导入页：文件上传、URL 提交、导入任务进度列表（2s 轮询）
- 文档列表页：按标签/状态/时间筛选
- 文档详情页：Markdown 查看 + 在线编辑（md-editor-v3）+ 版本历史（diff 视图、回滚）+ "确认无误"按钮
- RAG 问答页：流式回答（SSE）+ 引用来源展示

### 2.5 框架选型

锁定 **Spring AI**（而非 LangChain4j）：有官方 MCP Server starter（`spring-ai-starter-mcp-server`），与 Spring Boot 集成最顺，且同为简历关键词。

## 3. 数据模型（PostgreSQL + PGvector）

### 3.1 表结构

**source_document**（原始来源）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid PK | |
| source_type | varchar | FILE / URL |
| file_name | varchar | 文件名（FILE 时） |
| url | text | 原始链接（URL 时） |
| content_hash | varchar(64) | 原文内容 SHA-256，用于去重 |
| storage_path | varchar | 原文存储路径（本地 `storage/` 目录） |
| created_at | timestamptz | |

**import_task**（导入任务，异步状态机）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid PK | |
| source_document_id | uuid FK | |
| status | varchar | PENDING / EXTRACTING / DISTILLING / COMPLETED / FAILED；COMPLETED = 知识文档草稿已生成 |
| error_message | text | 失败原因（可读） |
| retry_count | int | 已重试次数，上限 3 |
| created_at / updated_at | timestamptz | |

**knowledge_document**（知识文档，与 source_document 一对一）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid PK | |
| source_document_id | uuid FK UNIQUE | 溯源 |
| title | varchar | 提炼标题 |
| summary | text | 摘要（冗余存储，列表页展示用） |
| content | text | 当前 Markdown 正文（= 最新版本快照） |
| status | varchar | DRAFT / CONFIRMED；首次手动保存或点"确认无误"后转 CONFIRMED |
| current_version | int | 当前版本号 |
| created_at / updated_at | timestamptz | |

**knowledge_document_version**（版本快照）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid PK | |
| document_id | uuid FK | |
| version_no | int | 从 1 递增；提炼成稿即 v1 |
| content | text | 该版本完整快照 |
| change_note | varchar | 保存备注（可空） |
| created_at | timestamptz | |

**document_chunk**（向量块）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | uuid PK | |
| document_id | uuid FK | |
| chunk_index | int | 块序号 |
| content | text | 块文本 |
| embedding | vector(1024) | PGvector，MVP 固定 1024 维 |
| embedding_model | varchar | 生成该向量的模型标识 |
| created_at | timestamptz | |

**tag / document_tag**：标签表与关联表（多对多）。

### 3.2 关键设计说明

- **向量维度固定 1024**：PGvector 列维度建表时固定，MVP 可切换的 embedding 模型必须为同维度（如 qwen 系/bge 系的 1024 维模型）；跨维度模型切换需改表结构，列为二期。
- **Embedding 模型标识**：`document_chunk.embedding_model` 记录生成模型；切换 embedding 模型时检测不一致，界面提示"需重建索引"，提供一键重建（后台任务逐文档重新向量化）。
- **diff 不存储**：版本间 diff 由 java-diff-utils 按需计算，只存快照。
- **去重**：按 `content_hash` 判断；重复导入提示"已存在对应知识文档"，提供跳转，不重复创建（重新提炼为二期）。

## 4. 核心流程设计

### 4.1 导入与提炼流程（异步）

1. 用户上传文件或提交 URL → 抽取/抓取内容，计算 `content_hash` 去重 → 创建 `source_document` 与 `import_task`（PENDING）。
2. 后端线程池异步执行：`EXTRACTING`（Tika/jsoup 抽取纯文本）→ `DISTILLING`（LLM 提炼；超出模型上下文则分段提炼 → 汇总成稿）→ 生成 `knowledge_document`（v1，DRAFT）→ 任务置 `COMPLETED`。
3. 草稿生成后立即执行首次向量化（见 4.2 的同一切分+embedding 链路），保证草稿也可被检索验证。
4. 失败处理：自动重试最多 3 次（指数退避），最终失败置 `FAILED` 并记录可读错误，支持手动重试。
5. 前端以 2s 轮询展示任务进度。

### 4.2 编辑确认与重新向量化

1. 用户在前端编辑 Markdown → 保存 → `knowledge` 模块写入新版本快照（`version_no + 1`）、更新正文与 `current_version`；首次保存或点击"确认无误"将文档置为 CONFIRMED。
2. 保存触发异步重新向量化：删除该文档旧 chunks → 按新内容切分 → embedding → 写入新 chunks。
3. 重新向量化失败时保留旧 chunks，文档标记索引异常，可手动重试；检索时旧内容仍可查（降级可读）。
4. 回滚 = 将指定历史版本另存为新版本（不物理删除历史）。

### 4.3 RAG 问答流程

1. query → embedding → PGvector 向量检索 top-k + 关键词 ILIKE 模糊匹配 → 合并去重排序（MVP 用简单加权；zhparser 中文全文索引列为二期增强）。
2. **检索范围默认仅 CONFIRMED 文档**，请求参数可放宽至包含 DRAFT（用于验证未确认的提炼质量）。
3. 组装 Prompt（含检索块与来源元数据）→ 流式输出（SSE）→ 前端展示回答与引用来源列表，可点击跳转到对应知识文档。

### 4.4 MCP Server

- 基于 `spring-ai-starter-mcp-server`，Streamable HTTP 传输，与主应用同进程、独立端点。
- 暴露工具：`search_knowledge(query, top_k)`、`get_document(id)`、`list_documents(filter)`；检索范围同样默认仅 CONFIRMED。
- Claude Code / Kimi Code 以远程 MCP 方式挂载。

### 4.5 模型 Provider 抽象

- 定义 `ChatProvider` 与 `EmbeddingProvider` 接口；实现：`OpenAiCompatibleProvider`（Kimi/DeepSeek 等）、`OllamaProvider`。
- 配置文件中声明当前激活的 Provider 与模型；支持管理接口热切换 Chat 模型。
- Embedding 切换走 3.2 的重建索引流程，不做静默切换。

## 5. 错误处理

| 场景 | 策略 |
|---|---|
| 不支持的文件格式 / 文件超限 | 导入即拒绝，返回可读错误与支持清单（上限 50MB） |
| 抓取失败（超时/404/反爬） | 任务置 FAILED，错误信息可读，支持手动重试 |
| LLM 调用失败 | 任务自动重试 3 次（指数退避）；不做跨 Provider 自动 fallback，避免行为不可预期 |
| 重新向量化失败 | 保留旧 chunks，标记索引异常，可手动重试 |
| Embedding 模型切换 | 检测向量模型不一致 → 界面提示 + 一键重建索引 |
| LLM 提炼产出质量差 | 以人工编辑纠错兜底（核心设计：AI 起草、人确认） |

## 6. 测试策略

- 单元测试（JUnit 5）：文本抽取、切分、hash 去重、版本快照与 diff、Prompt 组装。
- 集成测试（Testcontainers + pgvector 镜像）：Repository 层、向量检索链路、RAG 端到端（Mock LLM Provider）。
- 契约测试：`ChatProvider` / `EmbeddingProvider` 各实现满足统一接口行为（Mock 外部 API）。
- E2E：手动验收清单走查核心流程（导入 → 提炼 → 编辑 → 问答 → MCP 调用）。

## 7. 部署与开源

- Docker Compose 编排：`app`（Spring Boot jar）、`postgres`（`pgvector/pgvector:pg16`）、可选 `ollama`；`storage/` 卷挂载保存原文。
- GitHub 开源：README（含架构图、截图、快速开始）、LICENSE（MIT）、`.env.example` 配置模板、演示数据说明。

## 8. 里程碑（2 周）

| 时间 | 内容 |
|---|---|
| Day 1-2 | 项目骨架、DB schema、Provider 抽象、Docker Compose 打通 |
| Day 3-5 | ingestion + 导入任务状态机 + distillation（文件 + URL，含超长分段） |
| Day 6-8 | knowledge 模块（CRUD/版本/diff/回滚）+ 前端文档页面 |
| Day 9-10 | retrieval + RAG 问答（后端 + 前端流式界面） |
| Day 11-12 | MCP Server + Claude Code / Kimi Code 联调 |
| Day 13 | 索引重建、错误处理完善、README 与演示数据 |
| Day 14 | 收尾：开源整理（README/LICENSE/截图）、简历素材沉淀 |

## 9. 技术选型清单

| 类别 | 选型 |
|---|---|
| 语言/框架 | Java 17、Spring Boot 3.x、Spring AI（含 MCP Server starter） |
| 数据库 | PostgreSQL 16 + PGvector |
| 文本抽取 | Apache Tika、jsoup |
| diff | java-diff-utils |
| 前端 | Vue 3 + Vite + Element Plus + md-editor-v3 |
| 测试 | JUnit 5、Testcontainers |
| 部署 | Docker Compose |
| 开发方式 | Codex + Kimi Code 辅助开发，GitHub 开源 |
