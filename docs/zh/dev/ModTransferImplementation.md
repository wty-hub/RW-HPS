# TXJS Mod 传输实现设计

本文记录 RW-HPS 对 TXJS/RWPP v4 Mod 自动传输协议的服务端实现，供维护、协议升级、故障定位和后续测试使用。部署与配置操作见 [TXJS Mod 传输](../run/ModTransfer.md)。

## 1. 目标与范围

该功能解决以下问题：TXJS 客户端加入启用了 Mod 的 RW-HPS Headless 房间时，如果本地缺少房间要求的 Mod，可以直接向房主服务器请求并完成下载、校验、落盘和重载。

服务端负责：

- 在加入握手中声明 TXJS/RWPP 协议能力。
- 将已加载的 Mod 刷新为可传输的 `ModCatalog` 快照。
- 将目录型 Mod 归档缓存，并为每个条目提供大小与 SHA-256。
- 使用 64 KiB 分块、ACK 窗口、会话超时和并发上限调度发送。
- 在请求非法、文件不可读或发送失败时断开连接，避免客户端永久等待。

服务端不负责客户端落盘和重载。TXJS 客户端收到完整数据后执行 SHA-256 校验，以 `{name}.network.rwmod` 原子写入 Mod 目录，重载后发送完成包。

## 2. 代码结构

| 文件 | 职责 |
| --- | --- |
| `net/rwhps/server/net/rwpp/RwppConstants.kt` | 协议版本、包类型和 64 KiB 分块大小 |
| `net/rwhps/server/net/rwpp/RwppRoomOption.kt` | 生成 TXJS 可解析的 `RoomOption` TOML |
| `net/rwhps/server/net/rwpp/ModTransferSupport.kt` | 判断功能是否可宣告，计算可传输总大小 |
| `net/rwhps/server/net/rwpp/ModCatalog.kt` | 已加载 Mod 目录快照、归档缓存与安全校验 |
| `net/rwhps/server/net/rwpp/packet/RwppModPacket.kt` | 自定义包体的编码和解码 |
| `net/rwhps/server/net/rwpp/ModTransferScheduler.kt` | 分块、ACK 窗口、并发与超时调度 |
| `net/rwhps/server/net/rwpp/ModTransferEndpoint.kt` | app 类加载器可见的连接抽象，避免引用 Headless 的 `GameVersionServer` |
| `net/rwhps/server/net/rwpp/ModTransferHandler.kt` | 请求验证及连接生命周期入口 |
| `plugin/internal/headless/inject/lib/PlayerConnectX.kt` | 改写 161 握手包，声明能力 |
| `plugin/internal/headless/inject/net/HeadlessTypeConnect.kt` | 拦截 500、502、503 自定义包 |
| `plugin/internal/headless/inject/net/GameVersionServer.kt` | 断线时清理传输会话 |
| `game/manage/ModManage.kt` | Mod 加载后刷新或失效目录 |

## 3. 能力协商

`ModTransferSupport.isActive()` 同时要求：

1. `enableModTransfer` 为 `true`，且 `rwjsProtocolVersion` 等于协议常量默认值。
2. Headless 单位数据表明当前房间使用了非原版 Mod。
3. `ModCatalogManager.snapshot()` 有效且包含至少一个可传输条目。

Headless 向客户端发出 `PREREGISTER_INFO`（161）时，若功能有效，首个 UTF 字段被替换为：

```text
io.github.rwppcanTransferMod = true
allModsSize = <实际可传输字节数>
protocolVersion = <rwjsProtocolVersion>
```

`allModsSize` 来自当前目录快照的总大小，只用于客户端初始进度提示；每个 511 首块中的 `totalSize` 才是单个 Mod 的权威大小。

## 4. Mod 目录

`ModCatalogManager` 在 Headless 加载 Mod 后刷新：

1. 从已加载名单取得显示名。
2. 扫描 `data/mods` 顶层目录和 `.rwmod`，读取 `mod-info.txt` 的 `title`。
3. 仅保留显示名匹配、路径安全、大小合规且不歧义的条目。
4. 目录型 Mod 打包到归档缓存目录，总量受 `modTransferArchiveCacheSizeMb` 限制。

客户端 500 请求中的名称不是文件名，而是游戏 Mod 显示名。匹配时大小写不敏感；同名（忽略大小写）歧义条目会被拒绝。

## 5. 线协议

所有字符串均使用 Java `DataInput/DataOutput` 的 modified UTF-8 格式。游戏网络层负责外层包长度和包类型。

| 方向 | 类型 | 名称 | 用途 |
| --- | ---: | --- | --- |
| 客户端到服务端 | 500 | `MOD_DOWNLOAD_REQUEST` | 请求缺失 Mod |
| 客户端到服务端 | 502 | `MOD_RELOAD_FINISH` | 客户端完成校验、落盘和重载 |
| 客户端到服务端 | 503 | `MOD_CHUNK_ACK` | 确认一个已接收分块 |
| 服务端到客户端 | 511 | `DOWNLOAD_MOD_CHUNK` | 发送 Mod 分块 |
| 旧版服务端到客户端 | 510 | `DOWNLOAD_MOD_PACK` | 已废弃，RW-HPS 不发送 |

### 500

```text
UTF mods
```

`mods` 是逗号分隔的显示名。空项或无法唯一映射到当前快照的名称会导致拒绝。

### 511

```text
UTF  name
int  chunkIndex
int  totalChunks
long totalSize
UTF  sha256
int  chunkLength
byte chunkBytes[chunkLength]
```

- `chunkIndex` 从 0 开始。
- 首块携带真实 `totalSize` 与 SHA-256；后续分块元数据可为空/零，以实现为准。
- 空载荷仍生成至少一个空分块，保证状态机可结束。

### 503 / 502

503 确认识别 `name` 与 `ackChunkIndex`。无效、重复、陈旧或窗外 ACK 会被拒绝。502 表示客户端重载完成；完成时机不正确时会话会被拒绝。

## 6. 请求与调度

`ModTransferHandler` 处理流程：

1. 检查功能是否有效。
2. 解析请求并映射到当前 `ModCatalogSnapshot`。
3. 校验请求总大小不超过 `maxModTransferSizeMb`。
4. 将连接 ID、玩家名、快照与条目提交给 `ModTransferScheduler`。
5. 调度器按窗口发送分块，等待 ACK，并在超时或失败时断开连接。

关键限制：

- `modTransferWindowSize`：每会话未确认分块上限（默认 32）。
- `maxConcurrentModTransfers`：全服并发会话上限（默认 4）。
- `modTransferAckTimeoutMs` / `modTransferSessionTimeoutMs`：ACK 与会话无活动超时。

## 7. 安全边界

客户端不能提交路径，只能提交显示名，且必须落在：

```text
客户端请求名称 ∩ Headless 已加载名称 ∩ 当前目录唯一条目
```

服务端只枚举 `data/mods` 规范根目录的直接子项，并拒绝越界路径与不安全符号链接。SHA-256 用于检测传输损坏，不提供来源认证；该功能默认开启，房主需自行承担授权与内容安全责任。

## 8. 错误处理

| 场景 | 服务端行为 |
| --- | --- |
| 功能关闭或没有有效目录 | 不宣告能力；异常请求会被拒绝 |
| 请求名称不存在或存在歧义 | 断开连接并记录原因 |
| 超过大小、并发或超时限制 | 断开连接并记录原因 |
| 网络发送失败 / 客户端断开 | 立即取消对应会话 |

日志统一使用 `MODSYNC-HPS` 前缀。

## 9. 测试覆盖

测试位于 `Server-Core/src/test/java/net/rwhps/server/net/rwpp/`：

- `ModCatalogManagerTest`
- `ModTransferHandlerTest`
- `ModTransferSchedulerTest`
- `RwppRoomOptionTest`
- `packet/RwppModPacketTest`

```bash
./gradlew :Server-Core:test --tests 'net.rwhps.server.net.rwpp.*'
```

## 10. 已知限制

当前实现明确不包含：

- 断点续传和分块重传。
- 对 Mod 内容的签名、发布者认证或信任策略。
- 旧版 510 单包协议发送。
- Relay 模式的跨节点 Mod 数据代理。

如果未来升级协议，必须同步修改协议常量、配置默认版本、握手 RoomOption、TXJS 客户端包定义和本页线协议说明。
