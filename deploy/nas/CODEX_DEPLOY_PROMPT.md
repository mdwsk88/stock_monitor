# Codex 自动部署提示词模板

下次如果你要让 Codex 自动部署到你自己的 NAS / 家庭服务器，可以把下面这段改成你的实际参数后再使用：

```text
请帮我把 stock-monitor 部署到我的 NAS，并在必要时自动修正 Docker 部署文件。

已知固定参数：
- SSH: ssh -p <nas-ssh-port> root@<nas-host>
- SFTP: sftp -P <nas-ssh-port> root@<nas-host>
- NAS 型号/架构: <nas-model>, <nas-arch>
- 本地构建平台要求: 如果本地是 Apple Silicon，而 NAS 是 x86_64，请使用 `linux/amd64`
- NAS Docker 命令: <remote-docker-bin>
- 部署目录: <remote-project-dir>
- Docker 数据目录: <remote-docker-root>
- compose 文件:
  - deploy/nas/docker-compose.yml
  - deploy/nas/docker-compose.a-stock-mcp.yml
- 本地环境文件:
  - deploy/nas/env/stock-web.env
  - deploy/nas/env/a-stock-mcp.env
- 默认镜像标签:
  - stock-monitor-web:latest
  - stock-monitor-a-stock-mcp:latest
- 服务端口:
  - stock-web: 8888
  - a-stock-mcp: 8091
- 健康检查:
  - http://127.0.0.1:8888/actuator/health
  - http://127.0.0.1:8091/actuator/health
- 可选公网 SSE 地址:
  - http://<public-host>:8091/sse

执行要求：
- 优先使用 deploy/nas/deploy-to-nas.sh 做一键部署。
- 如果脚本不适用，再手动执行构建、导出、传输、docker load、compose up。
- 不要打印 env 文件中的密钥或密码。
- 部署完成后检查：
  - docker compose ps 是否 healthy
  - 8888/8091 的 actuator 是否返回 UP
  - 如有需要，再做 SSE 建连或 MCP smoke test
```
