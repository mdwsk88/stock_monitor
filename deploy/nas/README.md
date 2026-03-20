# Stock Monitor NAS Deployment

本方案针对 `Synology DS218+`，当前前提是：

- 主要部署 `stock-web`
- 可选附加部署 `a-stock-mcp`
- NAS 已安装并运行本机 MySQL / MariaDB
- `stock-web` 仅内网访问，`a-stock-mcp` 可按需做端口转发对外提供 SSE
- NAS 内存已升级到 `6G`

## 设计说明

- `stock-web` 自带启动抓取和全部定时任务，只允许单实例运行。
- `a-stock-mcp` 是独立的 MCP SSE 服务，读取同一业务库，不依赖 `stock-web` 进程存活。
- 采用 `host network`，这样容器里可以直接访问 NAS 本机 `127.0.0.1:3307` 的数据库。
- 容器时区和 JVM 时区都固定为 `Asia/Shanghai`，避免定时任务错位。
- JDBC 连接串里的 `serverTimezone` 建议与 NAS 上数据库实例的真实时区一致；不确定时先沿用项目当前默认的 `UTC`。
- 健康检查使用 `/actuator/health`，适合 Docker 和群晖容器管理器监控。
- 不要在 `docker-compose.yml` 里强制改成 `json-file` 等其他日志驱动，保留群晖默认日志驱动，`Container Manager` 才能直接在界面里显示容器日志。
- DS218+ 的 CPU 更适合“运行容器”，不适合在 NAS 上做 Maven 构建；镜像建议在本地构建后导入 NAS。
- 当前 NAS 上的 Docker 可执行文件是 `/usr/local/bin/docker`。
- 当前 NAS 已开启 SFTP，端口同样是 `20035`；手动上传可直接使用 `scp` / `sftp`，脚本仍默认使用 tar-over-ssh。

## 目录规划

推荐在 NAS 上创建目录：

```text
/volume2/docker/stock-monitor/
├── docker-compose.yml
├── docker-compose.a-stock-mcp.yml
└── env/
    ├── a-stock-mcp.env
    └── stock-web.env
```

## 一次性准备

1. 在本地复制环境变量模板：

```bash
cp deploy/nas/env/stock-web.env.example deploy/nas/env/stock-web.env
```

如果要部署 `a-stock-mcp`，再执行：

```bash
cp deploy/nas/env/a-stock-mcp.env.example deploy/nas/env/a-stock-mcp.env
```

2. 填写对应环境文件中的真实密钥与数据库密码。

3. 确认 NAS 本机数据库允许 `127.0.0.1:3307` 连接，且目标库已经存在。

4. 如果目标库是空库，再手动执行初始化脚本：

```bash
stock-web/src/main/resources/sql/init.sql
```

注意：`init.sql` 会删除旧表，不适合已有数据的库。

## 一键部署脚本

项目已内置一键脚本：

```bash
./deploy/nas/deploy-to-nas.sh
```

默认行为：

- 在本机构建 `stock-monitor-web:latest` 和 `stock-monitor-a-stock-mcp:latest`
- 默认使用 `docker buildx build --platform linux/amd64 --load`，适配当前群晖 `x86_64`
- 导出到 `deploy/nas/dist/`
- 默认通过 tar-over-ssh 上传到 `root@192.168.110.2:20035`
- 在 `/volume2/docker/stock-monitor` 下执行 `docker load` 和 `docker compose up -d`
- 等待两个服务都进入 `healthy`

常用示例：

```bash
./deploy/nas/deploy-to-nas.sh --only stock-web
./deploy/nas/deploy-to-nas.sh --only a-stock-mcp
./deploy/nas/deploy-to-nas.sh --skip-build
./deploy/nas/deploy-to-nas.sh --platform linux/amd64
./deploy/nas/deploy-to-nas.sh \
  --stock-web-image stock-monitor-web:2026-03-19-amd64 \
  --a-stock-mcp-image stock-monitor-a-stock-mcp:2026-03-19-amd64
```

查看脚本帮助：

```bash
./deploy/nas/deploy-to-nas.sh --help
```

说明：`--skip-build` 会优先复用本地同标签镜像；如果本地镜像已经被清掉，但 `deploy/nas/dist/` 里还保留对应 tar，也会直接复用现成产物继续部署。若标签仍是浮动的 `latest`，建议先确认本地镜像创建时间或直接改用显式版本标签，避免把旧镜像再次部署到 NAS。

如果你后续想直接让我自动部署，参数整理见：

```text
deploy/nas/CODEX_DEPLOY_PROMPT.md
```

## 本地构建镜像

### stock-web

在项目根目录执行：

```bash
docker buildx build --platform linux/amd64 --load -t stock-monitor-web:latest .
```

可选：如果想保留一个可回滚的版本标签：

```bash
docker buildx build --platform linux/amd64 --load -t stock-monitor-web:2026-03-19-amd64 .
```

说明：`deploy/nas/docker-compose.yml` 默认使用 `stock-monitor-web:latest`。如果你想固定到某个版本标签，启动前显式设置 `STOCK_WEB_IMAGE` 即可。

### a-stock-mcp

在项目根目录执行：

```bash
docker buildx build --platform linux/amd64 --load --build-arg APP_MODULE=a-stock-mcp --build-arg APP_PORT=8091 -t stock-monitor-a-stock-mcp:latest .
```

可选：如果想保留一个可回滚的版本标签：

```bash
docker buildx build --platform linux/amd64 --load --build-arg APP_MODULE=a-stock-mcp --build-arg APP_PORT=8091 -t stock-monitor-a-stock-mcp:2026-03-19-amd64 .
```

说明：`deploy/nas/docker-compose.a-stock-mcp.yml` 默认使用 `stock-monitor-a-stock-mcp:latest`。如果你想固定到某个版本标签，启动前显式设置 `A_STOCK_MCP_IMAGE` 即可。

## 导入镜像到群晖

### 方案 A：导出 tar 再导入 NAS

本地执行 `stock-web`：

```bash
mkdir -p deploy/nas/dist
docker save -o deploy/nas/dist/stock-monitor-web-latest.tar stock-monitor-web:latest
sha256sum deploy/nas/dist/stock-monitor-web-latest.tar > deploy/nas/dist/stock-monitor-web-latest.tar.sha256
```

本地执行 `a-stock-mcp`：

```bash
mkdir -p deploy/nas/dist
docker save -o deploy/nas/dist/stock-monitor-a-stock-mcp-latest.tar stock-monitor-a-stock-mcp:latest
sha256sum deploy/nas/dist/stock-monitor-a-stock-mcp-latest.tar > deploy/nas/dist/stock-monitor-a-stock-mcp-latest.tar.sha256
```

把 tar 包传到 NAS 后，在 NAS 上分别执行：

```bash
docker load -i stock-monitor-web-latest.tar
docker load -i stock-monitor-a-stock-mcp-latest.tar
```

如果你导出的是版本化标签，也按同样方式处理，例如：

```bash
docker save -o deploy/nas/dist/stock-monitor-web-2026-03-19-amd64.tar stock-monitor-web:2026-03-19-amd64
sha256sum deploy/nas/dist/stock-monitor-web-2026-03-19-amd64.tar > deploy/nas/dist/stock-monitor-web-2026-03-19-amd64.tar.sha256

docker save -o deploy/nas/dist/stock-monitor-a-stock-mcp-2026-03-19-amd64.tar stock-monitor-a-stock-mcp:2026-03-19-amd64
sha256sum deploy/nas/dist/stock-monitor-a-stock-mcp-2026-03-19-amd64.tar > deploy/nas/dist/stock-monitor-a-stock-mcp-2026-03-19-amd64.tar.sha256
```

### 方案 B：推送到镜像仓库

如果你有私有仓库，可以直接推送，再在 NAS 上拉取。

## 在 NAS 上部署

1. 上传以下文件到 `/volume2/docker/stock-monitor/`：

- `deploy/nas/docker-compose.yml`
- `deploy/nas/env/stock-web.env`

如果要部署 `a-stock-mcp`，再上传：

- `deploy/nas/docker-compose.a-stock-mcp.yml`
- `deploy/nas/env/a-stock-mcp.env`

2. SSH 登录 NAS：

```bash
ssh -p 20035 root@192.168.110.2
```

3. 只部署 `stock-web`：

```bash
cd /volume2/docker/stock-monitor
docker compose up -d
```

4. 只部署 `a-stock-mcp`：

```bash
cd /volume2/docker/stock-monitor
docker compose -f docker-compose.a-stock-mcp.yml up -d
```

5. 部署 `stock-web + a-stock-mcp`：

```bash
cd /volume2/docker/stock-monitor
docker compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml up -d
```

6. 如果这次导入的是版本化镜像标签，例如 `stock-monitor-web:2026-03-19-amd64` 和 `stock-monitor-a-stock-mcp:2026-03-19-amd64`，则改成：

```bash
cd /volume2/docker/stock-monitor
export STOCK_WEB_IMAGE=stock-monitor-web:2026-03-19-amd64
export A_STOCK_MCP_IMAGE=stock-monitor-a-stock-mcp:2026-03-19-amd64
docker compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml up -d
```

如果只部署 `a-stock-mcp`，则改成：

```bash
cd /volume2/docker/stock-monitor
export A_STOCK_MCP_IMAGE=stock-monitor-a-stock-mcp:2026-03-19-amd64
docker compose -f docker-compose.a-stock-mcp.yml up -d
```

7. 查看状态：

```bash
docker compose ps
docker compose logs -f stock-web
docker compose -f docker-compose.a-stock-mcp.yml ps
docker compose -f docker-compose.a-stock-mcp.yml logs -f a-stock-mcp
docker compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml logs -f a-stock-mcp
```

## 验证清单

### 容器健康检查

```bash
curl http://127.0.0.1:8888/actuator/health
curl http://127.0.0.1:8091/actuator/health
```

正常返回应包含：

```json
{"status":"UP"}
```

### 内网访问

在局域网内浏览器访问：

```text
http://<nas-ip>:8888/actuator/health
http://<nas-ip>:8091/actuator/health
```

### MCP 接入地址

局域网内的 MCP Client 可直接接：

```text
http://<nas-ip>:8091/sse
http://<nas-ip>:8091/mcp/message
```

当前已验证的公网转发地址：

```text
http://mdwsk8.top:8091/sse
```

### 手动触发接口

由于你不需要外网访问，接口只建议在家庭内网内使用：

```text
http://<nas-ip>:8888/api/morning-report/push/us/morning
http://<nas-ip>:8888/api/morning-report/push/a/morning
http://<nas-ip>:8888/api/morning-report/push/a/evening
http://<nas-ip>:8888/api/morning-report/push/us/evening
http://<nas-ip>:8888/api/morning-report/push/all
```

## 升级流程

1. 在本地重新构建镜像
2. 导出并导入 NAS，或推送到镜像仓库
3. 如果沿用 `stock-monitor-web:latest`，只升级 `stock-web` 时在 NAS 上执行：

```bash
cd /volume2/docker/stock-monitor
docker compose up -d
```

4. 如果只升级 `a-stock-mcp`，执行：

```bash
cd /volume2/docker/stock-monitor
docker compose -f docker-compose.a-stock-mcp.yml up -d
```

5. 如果同时升级 `stock-web + a-stock-mcp`，执行：

```bash
cd /volume2/docker/stock-monitor
docker compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml up -d
```

6. 如果这次切换到版本化标签，则在启动前设置：

```bash
cd /volume2/docker/stock-monitor
export STOCK_WEB_IMAGE=stock-monitor-web:2026-03-19-amd64
export A_STOCK_MCP_IMAGE=stock-monitor-a-stock-mcp:2026-03-19-amd64
docker compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml up -d
```

如果只切 `a-stock-mcp` 的版本标签，则改成：

```bash
cd /volume2/docker/stock-monitor
export A_STOCK_MCP_IMAGE=stock-monitor-a-stock-mcp:2026-03-19-amd64
docker compose -f docker-compose.a-stock-mcp.yml up -d
```

## 回滚流程

如果你保留了旧镜像标签，例如 `stock-monitor-web:2026-03-14-amd64` 和 `stock-monitor-a-stock-mcp:2026-03-14-amd64`：

1. 在 NAS 上切回旧标签
2. 重新执行：

```bash
cd /volume2/docker/stock-monitor
export STOCK_WEB_IMAGE=stock-monitor-web:2026-03-14-amd64
export A_STOCK_MCP_IMAGE=stock-monitor-a-stock-mcp:2026-03-14-amd64
docker compose -f docker-compose.yml -f docker-compose.a-stock-mcp.yml up -d
```

如果只回滚 `a-stock-mcp`，则改成：

```bash
cd /volume2/docker/stock-monitor
export A_STOCK_MCP_IMAGE=stock-monitor-a-stock-mcp:2026-03-14-amd64
docker compose -f docker-compose.a-stock-mcp.yml up -d
```

## 资源建议

- 当前 `JAVA_OPTS` 默认给到 `768m` 最大堆，适合 `DS218+ 6G + 本机数据库` 的组合。
- 如果观察到 GC 偏频繁，可升到 `-Xmx1024m`。
- 如果 NAS 同时跑了更多服务，优先保守到 `-Xmx512m`。
- `a-stock-mcp` 默认给到 `512m` 最大堆，通常足够；如果只是轻量问答，可先保守到 `-Xmx384m`。

## 安全建议

- 不要把 `8888` 暴露到公网。
- `8091` 如果需要公网访问，优先再挂一层 HTTPS 反向代理，不建议长期裸露 HTTP。
- 如果 DSM 防火墙已启用，仅允许家庭局域网网段访问 `8888`。
- 真实的 `stock-web.env` 不要提交到 Git。
- 真实的 `a-stock-mcp.env` 也不要提交到 Git。
- `deploy/nas/dist/` 下的镜像 tar 和校验文件只作为本地分发产物保留，不要提交到 Git。
