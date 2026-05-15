# FkwMcpPlugin 使用说明

FkwMcpPlugin 是一个基于 FkWeChat 宿主脚本环境实现的 MCP Server 插件。它会在微信/FkWeChat 插件环境中启动一个本地 HTTP MCP 服务，让支持 MCP 的客户端通过工具调用访问微信相关能力，例如获取好友、群聊、消息，以及发送文本消息。

## 服务配置

默认配置位于 `mcp/mcp-config.json`：

```json
{
  "host": "0.0.0.0",
  "port": 8888,
  "path": "/mcp",
  "mcpServers": {
    "Fkw-Mcp": {
      "url": "http://localhost:8888/mcp",
      "transport": "streamable-http"
    }
  }
}
```

默认服务地址：

```text
http://localhost:8888/mcp
```

配置项说明：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `host` | `0.0.0.0` | 服务监听地址 |
| `port` | `8888` | 服务监听端口 |
| `path` | `/mcp` | MCP HTTP 接口路径 |
| `transport` | `streamable-http` | MCP 客户端连接方式 |

如果端口被占用，可以修改 `port`，并同步修改 MCP 客户端中的 URL。

## MCP 客户端配置

在支持 MCP 的客户端中添加如下配置：

```json
{
  "mcpServers": {
    "Fkw-Mcp": {
      "url": "http://localhost:8888/mcp",
      "transport": "streamable-http"
    }
  }
}
```

## 可用功能

插件当前提供以下 MCP 工具。

### send_app_msg

发送普通文本消息。

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `wxid` | string | 是 | 接收方 wxid，可以是好友 wxid 或群聊 id |
| `content` | string | 是 | 要发送的文本内容 |
| `async` | boolean | 否 | 是否异步发送，默认 `false` |

### get_my_user_info

获取当前登录用户的完整信息。

参数：无。

### get_friend_list

获取好友列表。

参数：无。

### get_group_list

获取群聊列表。

参数：无。

### get_group_members

获取指定群聊的成员列表。

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chatroom_id` | string | 是 | 群聊 id |

### get_group_notice

获取指定群聊公告。

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chatroom_id` | string | 是 | 群聊 id |

### get_messages

获取指定对话的消息列表，默认返回最近 50 条原始消息。

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `talker` | string | 是 | 对话 id，可以是好友 wxid 或群聊 id |
| `start_time` | number | 否 | 起始时间，具体含义取决于宿主消息接口 |
| `limit` | number | 否 | 返回数量，默认 50 |

### get_msg_count

获取指定对话的消息总数。

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `talker` | string | 是 | 对话 id，可以是好友 wxid 或群聊 id |

### get_app_info

获取当前插件和宿主应用信息。

参数：无。

### show_toast

在设备上弹出 Toast 提示。

参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `text` | string | 是 | Toast 显示文本 |

## 日志

插件运行日志写入：

```text
mcp/logs/mcp-server.log
```

可以通过日志确认插件是否加载成功、配置是否读取成功、MCP 服务是否启动、客户端请求是否进入。

常见日志包括：

```text
[FkwMcpPlugin] onLoad start
[FkwMcpPlugin] start server port=8888 path=/mcp
[FkwMcpPlugin] server started
[FkwMcpPlugin] client accepted ...
```

## 常见问题

### MCP 客户端连接不上

检查以下事项：

1. 插件是否已经在 FkWeChat 中启用。
2. `mcp/logs/mcp-server.log` 中是否出现 `server started`。
3. 客户端配置的 URL 是否和 `mcp-config.json` 一致。
4. 如果客户端不在手机本机运行，URL 中不要使用 `localhost`，应改为手机局域网 IP。
5. 手机和客户端设备是否在同一局域网。
6. 端口 `8888` 是否被占用或被网络环境拦截。

### 工具调用失败

检查以下事项：

1. 参数名是否正确，例如发送消息需要 `wxid` 和 `content`。
2. `wxid`、`chatroom_id`、`talker` 是否有效。
3. FkWeChat 宿主是否支持对应接口。
4. 查看 `mcp-server.log` 中的错误堆栈。