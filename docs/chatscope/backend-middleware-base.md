# ChatScope 后端中间件底座

本文只覆盖 ChatScope 后端本地开发所需 PostgreSQL、Redis、Kafka 与 OpenIM 内网访问配置，不实现业务 API。

## Consul 发现结果

2026-06-20 从 `http://100.89.110.91:8500` 只读查询到：

| 能力 | Consul 服务 | 地址 | 当前结论 |
| --- | --- | --- | --- |
| PostgreSQL | `db-postgres` | `100.89.110.91:5432` | passing，优先复用，密码从 Dell 本机 secret 注入 |
| Kafka | `mq-kafka` | `100.89.110.91:9092` | passing，优先复用 |
| Redis | `cache-redis` | `100.105.213.32:6379` | passing，优先复用；后端默认必须使用该 Consul 地址，不连接本机 Docker Redis |

因此本地默认使用 `local-consul`，另保留一个手动 fallback profile：

- `local-consul`：PostgreSQL/Kafka/Redis 复用 Consul 中的 Tailscale 服务，Redis 使用 `cache-redis` 当前 passing 地址 `100.105.213.32:6379`。
- `local-fallback`：PostgreSQL/Redis/Kafka 都用本机 Docker Compose，仅在明确指定该 profile 时使用。

## 配置文件

| 文件 | 用途 |
| --- | --- |
| `configs/chatscope-backend/application-local.yml` | Spring Boot 本地 profile 配置 |
| `configs/chatscope-backend/chatscope.local-consul.env.example` | 复用 Consul PostgreSQL/Kafka/Redis 的 env 模板 |
| `configs/chatscope-backend/chatscope.local-fallback.env.example` | 全本机 fallback env 模板 |
| `configs/chatscope-backend/chatscope-middleware.local.compose.yml` | 本机 PostgreSQL/Redis/Kafka fallback |
| `scripts/chatscope/verify-middleware.sh` | PostgreSQL/Redis/Kafka 连通性与 Kafka produce/consume 验证 |

这些文件不包含真实密钥。`DEEPSEEK_API_KEY`、`CHATSCOPE_ADMIN_TOKEN`、Consul PostgreSQL/Redis 密码都必须从本机安全位置或密码管理器注入。

## Dell PostgreSQL

当前 Dell 部署状态：

| 项 | 值 |
| --- | --- |
| 容器 | `platform-postgres` |
| systemd unit | `platform-dell-postgres.service` |
| Compose | `/srv/platform/compose/dell-postgres.compose.yml` |
| 数据目录 | `/srv/platform/postgres/data` |
| secret 文件 | `/srv/platform/postgres/secrets/postgres.env` |
| Consul 服务 | `db-postgres` |
| 地址 | `100.89.110.91:5432` |
| 数据库 | `chatscope` |
| 用户 | `chatscope_app` |

本地不保存 Dell PostgreSQL 密码。需要完整 SQL 验证时，从 Dell secret 或密码管理器注入：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://100.89.110.91:5432/chatscope
export SPRING_DATASOURCE_USERNAME=chatscope_app
export SPRING_DATASOURCE_PASSWORD="<secret-from-password-manager>"
scripts/chatscope/verify-middleware.sh local-consul
```

## OpenIM 内网边界

ChatScope 后端可以访问 OpenIM API，但公开用户不应直连 OpenIM ChatServer/Admin API。

- 本机：`CHATSCOPE_OPENIM_CHAT_API_BASE_URL=http://127.0.0.1:10008`，`CHATSCOPE_OPENIM_ADMIN_API_BASE_URL=http://127.0.0.1:10009`。
- 后续公开部署：反向代理只暴露 Light Web Demo 与 ChatScope Backend 公开 API。
- OpenIM ChatServer、Admin API、Mongo、Redis、Kafka、PostgreSQL 只允许 Docker 内网、Tailscale 或后端所在内网访问。
- 验证码、注册、登录和 token 发放必须经 ChatScope 后端 Abuse Guard，不允许公网绕过后端调用 OpenIM 原生入口。

`openim-docker/docker-compose.yaml` 中 `openim-chat` 的 `10008/10009` 已改为 `127.0.0.1` 绑定；当前运行容器需重启后才会应用端口绑定变化。

## 本机 fallback 启动

```bash
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml up -d
```

验证：

```bash
scripts/chatscope/verify-middleware.sh local-fallback
```

等价手动命令：

```bash
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T postgres pg_isready -U chatscope -d chatscope
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T postgres psql -U chatscope -d chatscope -c "select 1;"
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T redis redis-cli -a chatscope_dev_only PING
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Kafka produce/consume 手动烟测：

```bash
TOPIC="chatscope.middleware.smoke.$(date -u +%Y%m%d%H%M%S)"
MESSAGE="chatscope-smoke-$(date -u +%Y%m%dT%H%M%SZ)"
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic "$TOPIC" --partitions 1 --replication-factor 1
printf '%s\n' "$MESSAGE" | docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T kafka /opt/bitnami/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$TOPIC"
docker compose -f configs/chatscope-backend/chatscope-middleware.local.compose.yml exec -T kafka /opt/bitnami/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic "$TOPIC" --from-beginning --timeout-ms 10000 --max-messages 1
```

## Consul 复用验证

注入 PostgreSQL/Redis 密码并验证：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://100.89.110.91:5432/chatscope
export SPRING_DATASOURCE_USERNAME=chatscope_app
export SPRING_DATASOURCE_PASSWORD="<secret-from-password-manager>"
# Redis 地址来自 Consul 服务 cache-redis，不使用本机 Docker Redis。
export SPRING_DATA_REDIS_HOST=100.105.213.32
export SPRING_DATA_REDIS_PORT=6379
export SPRING_DATA_REDIS_PASSWORD="<secret-from-password-manager>"
export SPRING_KAFKA_BOOTSTRAP_SERVERS=100.89.110.91:9092
scripts/chatscope/verify-middleware.sh local-consul
```

如果只想确认 Consul 与端口：

```bash
curl --noproxy '*' -fsS 'http://100.89.110.91:8500/v1/health/service/db-postgres?passing=1'
curl --noproxy '*' -fsS 'http://100.89.110.91:8500/v1/health/service/mq-kafka?passing=1'
curl --noproxy '*' -fsS 'http://100.89.110.91:8500/v1/health/service/cache-redis?passing=1'
nc -vz 100.89.110.91 5432
nc -vz 100.89.110.91 9092
nc -vz 100.105.213.32 6379
```

当前本机已有 `kafka` 容器时，可用它作为 Kafka CLI，对 Consul Kafka 做 produce/consume：

```bash
KAFKA_CLI_CONTAINER=kafka scripts/chatscope/verify-middleware.sh local-consul
```

## Dell 部署脚本

`scripts/chatscope/deploy-dell-postgres.sh` 是 Dell PostgreSQL 的幂等部署脚本。它只在 Dell 本机生成 secret，不会把密码写入仓库。
