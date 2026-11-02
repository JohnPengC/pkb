# docs/e2e-checklist.md — 手工验收清单（发布前全部走一遍）

## 环境
- [ ] `cp .env.example .env` 填入真实 API key 后，`docker compose up -d --build` 全部 healthy
- [ ] 打开 http://localhost:8080 能看到前端界面

## 导入与提炼
- [ ] 上传一个 < 50MB 的 docx：任务状态 排队中→解析中→提炼中→已完成，文档列表出现草稿
- [ ] 带表格的 docx：提炼结果中表格结构保留（Markdown 表格），关键术语不缺漏
- [ ] 上传 xlsx 或 pptx：提炼成功，表格/条目结构保留为 Markdown
- [ ] 上传相同文件：提示"该内容已导入过"，不产生重复
- [ ] 上传 .exe：提示"不支持的文件格式"
- [ ] 导入一篇服务端渲染的文章 URL：生成以页面标题开头的草稿
- [ ] 导入一个需要 JS 渲染的 SPA 页面：任务失败，错误信息可读，点"重试"按钮可再次执行
- [ ] 超长文档（数万字 txt）：提炼结果为整合后的单篇文档而非截断内容

## 编辑与版本
- [ ] 草稿点"确认无误"→ 状态变已确认
- [ ] 编辑内容保存（填备注）→ 版本 +1；版本历史里能看到 v1/v2 diff 着色正确
- [ ] 回滚到 v1 → 生成 v3，内容与 v1 一致
- [ ] 打标签 → 列表页按该标签过滤能搜到
- [ ] 详情页/列表页删除文档：二次确认后删除，列表即时消失
- [ ] 导入页删除记录：二次确认后任务与源记录清除，同内容文件可重新导入

## 检索与问答
- [ ] `/api/search?q=...` 默认不返回草稿；确认后可返回；`includeDraft=true` 草稿可返回
- [ ] 问答页提问：先出引用来源，再流式输出，回答带 [1] 标记，点击来源跳转详情
- [ ] 提问知识库没有的内容：回答"知识库中没有足够信息"，不编造

## MCP
- [ ] `claude mcp add --transport sse pkb http://localhost:8080/sse` 成功
- [ ] 在 Claude Code 中让其"搜索我的知识库里的 XX"→ 实际调用了 search_knowledge 并返回结果

## 模型切换
- [ ] `PUT /api/admin/model/chat` 切换 Chat 模型后，新问答使用新模型
- [ ] .env 换 embedding 模型重启后：`GET /api/admin/model` 显示 `indexConsistent=false`
- [ ] `POST /api/admin/reindex` 后恢复 `true`，检索仍正常
