# BaiduChatFilter 插件

基于百度内容安全（内容审核平台）文本审核 API 的游戏内聊天过滤插件。  
玩家发送的每条聊天消息在广播前会同步调用百度接口审核，命中配置的结论类型则拦截，并给玩家发送提示。  
玩家加入服务器时还会审核其昵称，命中配置的结论类型则直接踢出。

## 原理

RW-HPS 服务器在广播聊天消息前会执行 `Data.core.admin.filterMessage()` 过滤管道（见 `GameVersionServer.receiveChat`）。本插件通过 `Administration.addChatFilter()` 注册过滤器，返回 `null` 即拦截消息：

```kotlin
val messageOut = Data.core.admin.filterMessage(player, message) ?: run {
    packet.status = Control.EventNext.STOPPED
    return
}
```

命令消息（以 `.` `-` `_` 开头）不经过该过滤管道，因此本插件不影响命令。

昵称过滤通过监听 `PlayerJoinEvent` 实现：玩家加入时调用百度内容安全 API 审核 `player.name`，命中 `blockConclusionTypes` 即调用 `kickPlayer` 踢出。

## 快速开始

### 1. 申请百度智能云密钥

1. 注册 [百度智能云](https://cloud.baidu.com/) 并完成实名认证。
2. 在 [百度 AI 开放平台](https://ai.baidu.com/) 创建应用，获取 **API Key** 和 **Secret Key**。
3. 开通「内容审核平台」服务（文本审核免费额度有限，超出后按量计费）。

### 2. 部署插件

```bash
./gradlew :plugin:BaiduChatFilter:copyToPlugins
```

首次启动后会在 `data/plugins/BaiduChatFilter/` 生成 `BaiduChatFilterConfig.json`，填入密钥：

```json
{
  "enabled": true,
  "apiKey": "你的 API Key",
  "secretKey": "你的 Secret Key"
}
```

重启服务器（或控制台执行 `chatfilter reload`）生效。

## 配置

`data/plugins/BaiduChatFilter/BaiduChatFilterConfig.json`

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用过滤 |
| `apiKey` / `secretKey` | 空 | 百度智能云应用密钥 |
| `skipAdmin` | `true` | 管理员消息不过滤 |
| `failOpen` | `true` | API 请求失败/超时时放行消息；`false` 则拦截 |
| `blockConclusionTypes` | `[2, 3]` | 需要拦截的结论类型（1=合规, 2=不合规, 3=疑似, 4=审核失败），同时作用于聊天消息与昵称 |
| `blockMessage` | 您的消息包含违规内容，已被拦截 | 拦截时发给玩家的提示 |
| `filterName` | `true` | 是否启用昵称过滤，昵称违规时踢出 |
| `nameKickMessage` | 您的昵称包含违规内容，已被服务器踢出 | 昵称违规时发给玩家的踢出提示 |
| `nameKickDurationSeconds` | `0` | 昵称违规踢出的封禁时长（秒），`0` 表示仅踢出不封禁 |
| `timeoutSeconds` | `5` | 单次 API 调用超时（秒）。审核为同步阻塞，建议 3-8 秒 |
| `cacheMaxSize` | `1000` | 审核结果缓存条数上限 |
| `cacheMinutes` | `10` | 审核结果缓存有效期（分钟），降低重复请求与 QPS 消耗 |
| `debug` | `false` | 打印每条消息的审核详情日志 |

## 控制台命令

```
chatfilter status           查看状态与配置
chatfilter reload           重新加载配置文件
chatfilter test <文本>      直接调用百度接口测试一段文本（不经过真实聊天）
chatfilter enable           启用过滤
chatfilter disable          禁用过滤
```

## 说明与限制

- 审核调用为**同步阻塞**，发生在玩家连接线程上，请求耗时（含超时）内该玩家无法收发其他网络包。建议 `timeoutSeconds` 不要设得过大。昵称审核发生在 `PlayerJoinEvent` 的 IO 协程中，不会阻塞玩家连接线程，但会延迟该玩家完成加入流程。
- 免费版文本审核有 QPS 限制（约 2 QPS），启用 `cacheMaxSize`/`cacheMinutes` 缓存可显著减少重复请求。昵称与聊天消息共用同一结果缓存，相同昵称重复加入不会重复请求。
- `failOpen=true` 时，网络故障/超时不会误杀正常玩家消息或误踢玩家，但可能漏放违规内容；追求严格可改为 `false`。
- 超长消息（>20000 字节，百度接口上限）直接放行，服务端另有 `maxMessageLen` 限制。
