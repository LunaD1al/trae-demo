# OpenIM 本机底座

本目录使用官方 `openimsdk/openim-docker` 的 `v3.8` tag 作为本机 OpenIM 底座。当前配置只面向 Demo 本机闭环：

- PC Web: `http://127.0.0.1:11001`
- OpenIM REST API: `http://127.0.0.1:10002`
- ChatServer API: `http://127.0.0.1:10008`
- ChatServer Admin API: `http://127.0.0.1:10009`
- MinIO external address: `http://127.0.0.1:10005`

ChatServer API 和 ChatServer Admin API 只作为 ChatScope 后端的内网依赖使用，不作为公网入口。`openim-chat` 的本机端口映射绑定到 `127.0.0.1`；后续公开部署时，验证码、注册、登录和 token 发放必须经过 ChatScope 后端，不能让公网用户直连 OpenIM ChatServer/Admin API。

## 启动

```bash
./scripts/openim/up.sh
./scripts/openim/wait-ready.sh
```

首次启动会拉取较大的 Docker 镜像。官方文档建议启动后等待 `30-60s` 再做健康检查；本仓库的 `wait-ready.sh` 会轮询 `openim-server`、`openim-chat` 健康状态，并验证管理员 token API。

## 初始化 Demo 账号与群

```bash
./scripts/openim/seed-demo.sh
```

脚本会完成：

- 通过 ChatServer Admin `/account/add_user` 创建 3 个能登录 PC Web 的真人账号。
- 通过 OpenIM `/user/add_notification_account` 创建机器人账号 `chatscope_bot`。
- 通过 OpenIM `/group/create_group` 创建群 `chatscope_demo_group`。
- 通过 OpenIM `/msg/send_msg` 验证真人群消息、真人私聊机器人、机器人 REST 发群消息、机器人 REST 发私聊消息。

脚本提交给 ChatServer 的用户密码为 MD5 值；PC Web 登录时仍输入下表中的明文密码。

默认账号：

| 角色 | 邮箱 | 密码 | userID |
| --- | --- | --- | --- |
| 真人 1 | `chatscope.alice@example.local` | `OpenIM123456` | `chatscope_alice` |
| 真人 2 | `chatscope.bob@example.local` | `OpenIM123456` | `chatscope_bob` |
| 真人 3 | `chatscope.carol@example.local` | `OpenIM123456` | `chatscope_carol` |
| 机器人 | - | - | `chatscope_bot` |

## 手动核验

1. 打开 `http://127.0.0.1:11001`。
2. 使用 Alice 或 Bob 的邮箱和密码登录。
3. 查看群 `ChatScope Demo Room` 或群 ID `chatscope_demo_group`，确认能看到种子脚本发出的群消息。
4. 用 Alice 打开与 `chatscope_bot` 的私聊，确认能看到脚本发出的私聊烟测消息。
5. 在 PC Web 手动发送一条群消息和一条给机器人的私聊，确认客户端侧路径可用。

## 停止

```bash
cd openim-docker
docker compose down
```

`openim-docker/.env` 将 `DATA_DIR` 设置为 `./data`，停止服务不会删除数据。需要重置数据时，先确认不再需要本机 OpenIM 数据，再删除 `openim-docker/data`。
