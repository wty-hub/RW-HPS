# TXJS Mod 传输

RW-HPS Server 模式可以向 TXJS（RWPP 协议 v4）客户端自动发送其缺失的 Mod。该功能默认开启，仅适用于 Headless 已成功加载 Mod 的房间。

维护者需要了解协议、调度器和安全设计时，请阅读 [TXJS Mod 传输实现设计](../dev/ModTransferImplementation.md)。

## 前置条件

- 客户端必须使用与服务器 `rwjsProtocolVersion` 相同的 TXJS/RWPP 协议版本，当前默认值为 `4`。
- Mod 位于服务器运行目录的 `data/mods` 下，并且已经被 Headless 正常加载。
- 服务端只扫描 `data/mods` 的顶层条目。支持顶层目录和 `.rwmod` 文件。
- 每个 Mod 必须包含 `mod-info.txt`，其中 `title` 必须与游戏握手中的 Mod 显示名一致。
- 房主必须确认自己拥有这些 Mod 的网络再分发权。

示例：

```text
data/mods/
├── example_mod/
│   ├── mod-info.txt       # title = Example Mod
│   └── units/
└── another.rwmod          # 包内含 mod-info.txt
```

目录型 Mod 会在刷新目录时打包为缓存的 ZIP；`.rwmod` 文件会原样发送。两个顶层条目若声明相同 `title`，该名称会被视为歧义并拒绝传输。

## 配置

关闭服务器，编辑 `data/ConfigServer.json`：

```json
{
  "enableModTransfer": true,
  "rwjsProtocolVersion": 4,
  "maxModTransferSizeMb": 128,
  "modTransferWindowSize": 1,
  "modTransferAckTimeoutMs": 10000,
  "modTransferSessionTimeoutMs": 300000,
  "maxConcurrentModTransfers": 4,
  "modTransferArchiveCacheSizeMb": 512
}
```

修改后重启服务器。启动并加载 Mod 后，`ModCatalogManager` 会刷新可传输目录；没有可安全映射的已加载 Mod 时不会宣告能力。更多字段说明见 [服务器配置](Config.md)。

## 协议流程

1. 服务器在 161 包首字段发送 `io.github.rwpp` 加 `RoomOption` TOML。
2. 客户端检测本地缺失项，以 500 包发送逗号分隔的 Mod 显示名。
3. 服务器按 `ModCatalog` 快照校验请求，仅允许传输目录中已加载且来源唯一的 Mod。
4. 服务器按 64 KiB 发送 511 分块；首块携带总大小与 SHA-256。
5. 客户端每接收一个有效分块，以 503 包确认。默认每客户端最多 1 个未确认分块（适配 TXJS 严格按序接收），受 `modTransferWindowSize` 控制。
6. 客户端重组、校验、原子落盘并重载后发送 502 包，服务器结束该连接的传输会话。

TXJS 旧版 510 单包格式不会由 RW-HPS 发送。

## 限制与安全

- 请求中所有 Mod 的总大小不得超过 `maxModTransferSizeMb`。
- 所有请求都按服务器实际加载的 Mod 名单过滤，客户端不能借此读取 `data/mods` 外的文件。
- 符号链接或规范化路径越出 Mod 根目录会被拒绝。
- 目录型 Mod 使用磁盘归档缓存，缓存总量受 `modTransferArchiveCacheSizeMb` 限制。
- 当前协议没有断点续传。连接断开、分块乱序、ACK 超时或哈希失败后，客户端需要重新加入。

## 排障

日志关键字为 `MODSYNC-HPS`。

- 客户端未显示下载：检查 `enableModTransfer`、协议版本、Headless 是否成功加载 Mod，以及 `mod-info.txt` 的 `title`。
- `invalid, ambiguous, or unknown mod request`：请求名称未映射到当前目录快照中唯一且已加载的 Mod。
- `requested mods exceed transfer limit`：提高 `maxModTransferSizeMb`，或减少一次请求的 Mod；提高上限也会增加资源占用。
- 客户端停在下载进度：检查 503 ACK 是否到达服务器、`modTransferAckTimeoutMs` 是否过短，并确认中间代理没有过滤自定义包类型 500、502、503、511。
