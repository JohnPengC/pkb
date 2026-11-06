# 贡献指南

感谢你对 PKB 的关注。本项目是个人主导的开源项目，欢迎 Issue 与 Pull Request。

## 反馈问题

提交 Issue 时请附：复现步骤、期望行为、实际行为、相关日志（`docker compose logs app`）。

## 本地开发

```bash
docker compose up -d postgres        # 仅启动数据库
mvn spring-boot:run                  # 后端 :8080
cd frontend && npm install && npm run dev   # 前端 :5173（代理 /api）
```

- 提交前请跑通 `mvn test`（需要 Docker：Testcontainers 会拉取 `pgvector/pgvector:pg16`）
- 前端改动请确保 `npm run build` 通过
- 遵循 Conventional Commits（如 `feat: xxx` / `fix: xxx`）

## 行为准则

友善、就事论事；不接受带有个人信息的测试数据进入仓库。
