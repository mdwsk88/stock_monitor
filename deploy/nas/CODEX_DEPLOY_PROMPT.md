# Codex 自动部署提示词

下次如果你要我继续自动部署，可以直接把下面这段发给我：

```text
请帮我把 stock-monitor 部署到群晖，并在必要时自动修正 Docker 部署文件。

已知固定参数：
- SSH: ssh -p 20035 root@192.168.110.2
- SFTP: sftp -P 20035 root@192.168.110.2
- NAS 型号/架构: Synology DS218+, x86_64
- 本地构建平台要求: 这台 Mac 是 Apple Silicon，给 NAS 构建镜像必须使用 `linux/amd64`
- NAS Docker 命令: /usr/local/bin/docker
- 部署目录: /volume2/docker/stock-monitor
- Docker 数据目录: /volume2/docker
- compose 文件:
  - deploy/nas/docker-compose.yml
  - deploy/nas/docker-compose.a-stock-mcp.yml
- 本地环境文件:
  - deploy/nas/env/stock-web.env
  - deploy/nas/env/a-stock-mcp.env
- 默认镜像标签:
  - stock-monitor-web:latest
  - stock-monitor-a-stock-mcp:latest
- Dockerfile 支持:
  - APP_MODULE=stock-web | a-stock-mcp
  - APP_PORT=8888 | 8091
- 服务端口:
  - stock-web: 8888
  - a-stock-mcp: 8091
- 健康检查:
  - http://127.0.0.1:8888/actuator/health
  - http://127.0.0.1:8091/actuator/health
- MCP 公网 SSE 地址:
  - http://mdwsk8.top:8091/sse

执行要求：
- 优先使用 deploy/nas/deploy-to-nas.sh 做一键部署。
- 如果脚本不适用，再手动执行构建、导出、传输、docker load、compose up。
- 本地构建时默认用 `docker buildx build --platform linux/amd64 --load`，不要给 x86_64 群晖推送 arm64 镜像。
- 如果使用 `--skip-build`，不要盲目复用 `latest`；先核对本地镜像创建时间，或直接改用显式版本标签再部署。
- NAS 已开启 SFTP，端口同样是 20035；如果手动传文件，可直接用 scp/sftp，异常时再回退 tar-over-ssh。
- 不要打印 env 文件中的密钥或密码。
- 部署完成后检查：
  - docker compose ps 是否 healthy
  - 8888/8091 的 actuator 是否返回 UP
  - a-stock-mcp 的 /sse 是否可连
  - 如有需要，再做 initialize 和 tools/list 测试
```

## 今天确认过的关键信息

- 两个服务已经在 NAS 上跑通过一次，目录是 `/volume2/docker/stock-monitor`
- `stock-monitor-web` 和 `stock-monitor-a-stock-mcp` 当前都能启动并通过健康检查
- `a-stock-mcp` 的公网地址 `http://mdwsk8.top:8091/sse` 已验证可完成 SSE 建连、`initialize`、`tools/list`
- 当前 NAS 已开启 SFTP；之前传文件实际使用过 `COPYFILE_DISABLE=1 tar ... | ssh ... 'tar -xf -'`，仍可作为 fallback
- 历史回滚标签目前至少保留了 `stock-monitor-web:2026-03-14-amd64`
