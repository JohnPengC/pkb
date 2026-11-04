# PKB — 个人知识库

把本地文档（docx / PDF / PPTX / XLSX / CSV / txt / md）和收藏网页，用 LLM 提炼为结构化知识文档，形成可纠错、可检索的个人知识库；并以 MCP Server 形式外挂到 Claude Code / Kimi Code 等 AI 编程工具，或在内置界面中基于知识库进行 RAG 问答。

## 功能

- **多源导入**：本地文件上传、URL 抓取，异步流水线（解析 → 提炼 → 入库），失败自动重试
- **知识文档**：一源一文档，Markdown 结构化（摘要/要点/术语/关键细节摘录/待确认），可溯源回原文
- **人工纠错**：在线编辑，保存自动生成版本快照，支持版本 diff 与回滚；保存后自动重新向量化
- **混合检索**：PGvector 向量检索 + 关键词匹配，提炼文档与原文**双索引**并行检索（结构 + 细节证据）；默认仅检索"已确认"文档（质量闸门）
- **RAG 问答**：流式回答，答案带 [编号] 引用，可点击跳转到来源文档
- **MCP Server**：暴露 `search_knowledge` / `get_document` / `list_documents` 三个工具
- **模型可切换**：OpenAI 兼容 API（Kimi/DeepSeek…）与本地 Ollama 通过配置切换；Embedding 切换提供一致性检测与一键重建索引

## 技术栈

Java 17 · Spring Boot 3 · Spring AI (MCP Server) · PostgreSQL 16 + PGvector · Apache Tika · jsoup · Vue 3 · Element Plus · Docker Compose

## 架构

```
上传/URL ──► 文本抽取 ──► LLM 提炼 ──► 知识文档(草稿) ──► 人工编辑/确认
                                   │                        │ 保存
                                   └──► 首次向量化 ◄── 重新切分+Embedding
                                                            │
                                              PGvector（检索默认仅 CONFIRMED）
                                                            │
                                    ┌───────────────────────┼───────────┐
                                 RAG 问答               MCP Server    文档查看
```

后端为模块化单体：`provider`（模型抽象）/ `ingestion`（导入与异步任务）/ `distillation`（LLM 提炼）/ `knowledge`（文档与版本）/ `retrieval`（切分、向量、检索）/ `chat`（RAG）/ `mcp`（MCP Server）。

## 快速开始

```bash
cp .env.example .env   # 填入聊天模型的 API key（默认 Kimi）
docker compose --profile local-llm up -d --build   # 含本地 Ollama（embedding 用 bge-m3）
docker exec pkb-ollama ollama pull bge-m3
# 打开 http://localhost:8080
```

说明：聊天模型走云端（Kimi 等 OpenAI 兼容端点）；**embedding 默认走本地 Ollama 的 bge-m3**（Kimi 没有 embeddings 接口，云端替代方案见 `.env.example` 注释）。bge-m3 拉取完成前导入的文档会提示向量化失败，拉取后点「模型切换」页的重建或调用 `POST /api/admin/reindex` 即可。

聊天也本地化（可选，需另拉 qwen2.5:7b）：

```bash
docker exec -it pkb-ollama ollama pull qwen2.5:7b
# .env 中把 CHAT_* 改为 .env.example 里的本地预设，重启 app
```

## MCP 挂载

Claude Code：

```bash
claude mcp add --transport sse pkb http://localhost:8080/sse
```

Kimi Code / 其他 MCP 客户端：以 SSE（或客户端支持的远程方式）添加 `http://localhost:8080/sse`。

挂载后在对话中即可让 AI 调用 `search_knowledge` 检索你的知识库。

## 开发

```bash
docker compose up -d postgres        # 仅启动数据库
mvn spring-boot:run                  # 后端 :8080
cd frontend && npm install && npm run dev   # 前端 :5173（代理 /api）
mvn test                             # 测试（Testcontainers，需 Docker）
```

## License

MIT
