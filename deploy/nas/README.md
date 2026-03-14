# Stock Monitor NAS Deployment

本方案针对 `Synology DS218+`，当前前提是：

- 只部署 `stock-web`
- NAS 已安装并运行本机 MySQL / MariaDB
- 容器仅内网访问，不对公网开放
- NAS 内存已升级到 `6G`

## 设计说明

- `stock-web` 自带启动抓取和全部定时任务，只允许单实例运行。
- 采用 `host network`，这样容器里可以直接访问 NAS 本机 `127.0.0.1:3306` 的数据库。
- 容器时区和 JVM 时区都固定为 `Asia/Shanghai`，避免定时任务错位。
- JDBC 连接串里的 `serverTimezone` 建议与 NAS 上数据库实例的真实时区一致；不确定时先沿用项目当前默认的 `UTC`。
- 健康检查使用 `/actuator/health`，适合 Docker 和群晖容器管理器监控。
- 不要在 `docker-compose.yml` 里强制改成 `json-file` 等其他日志驱动，保留群晖默认日志驱动，`Container Manager` 才能直接在界面里显示容器日志。
- DS218+ 的 CPU 更适合“运行容器”，不适合在 NAS 上做 Maven 构建；镜像建议在本地构建后导入 NAS。

## 目录规划

推荐在 NAS 上创建目录：

```text
/volume1/docker/stock-monitor/
├── docker-compose.yml
└── env/
    └── stock-web.env
```

## 一次性准备

1. 在本地复制环境变量模板：

```bash
cp deploy/nas/env/stock-web.env.example deploy/nas/env/stock-web.env
```

2. 填写 `deploy/nas/env/stock-web.env` 中的真实密钥和数据库密码。

3. 确认 NAS 本机数据库允许 `127.0.0.1:3306` 连接，且目标库已经存在。

4. 如果目标库是空库，再手动执行初始化脚本：

```bash
stock-web/src/main/resources/sql/init.sql
```

注意：`init.sql` 会删除旧表，不适合已有数据的库。

## 本地构建镜像

在项目根目录执行：

```bash
docker build -t stock-monitor-web:latest .
```

可选：如果想带版本号：

```bash
docker build -t stock-monitor-web:2026-03-14 .
```

## 导入镜像到群晖

### 方案 A：导出 tar 再导入 NAS

本地执行：

```bash
docker save -o stock-monitor-web-latest.tar stock-monitor-web:latest
```

把 tar 包传到 NAS 后，在 NAS 上执行：

```bash
docker load -i stock-monitor-web-latest.tar
```

### 方案 B：推送到镜像仓库

如果你有私有仓库，可以直接推送，再在 NAS 上拉取。

## 在 NAS 上部署

1. 上传以下文件到 `/volume1/docker/stock-monitor/`：

- `deploy/nas/docker-compose.yml`
- `deploy/nas/env/stock-web.env`

2. SSH 登录 NAS：

```bash
ssh <nas-user>@<nas-ip>
```

3. 进入部署目录并启动：

```bash
cd /volume1/docker/stock-monitor
docker compose up -d
```

4. 查看状态：

```bash
docker compose ps
docker compose logs -f stock-web
```

## 验证清单

### 容器健康检查

```bash
curl http://127.0.0.1:8888/actuator/health
```

正常返回应包含：

```json
{"status":"UP"}
```

### 内网访问

在局域网内浏览器访问：

```text
http://<nas-ip>:8888/actuator/health
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
3. 在 NAS 上执行：

```bash
cd /volume1/docker/stock-monitor
docker compose up -d
```

## 回滚流程

如果你保留了旧镜像标签，例如 `stock-monitor-web:2026-03-13`：

1. 修改 `docker-compose.yml` 中的镜像标签
2. 重新执行：

```bash
docker compose up -d
```

## 资源建议

- 当前 `JAVA_OPTS` 默认给到 `768m` 最大堆，适合 `DS218+ 6G + 本机数据库` 的组合。
- 如果观察到 GC 偏频繁，可升到 `-Xmx1024m`。
- 如果 NAS 同时跑了更多服务，优先保守到 `-Xmx512m`。

## 安全建议

- 不要做路由器端口映射，不要把 `8888` 暴露到公网。
- 如果 DSM 防火墙已启用，仅允许家庭局域网网段访问 `8888`。
- 真实的 `stock-web.env` 不要提交到 Git。
