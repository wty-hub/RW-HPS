# RoomReset 插件

RoomReset 提供受管理员密码保护的控制台指令，将当前对战房间设置一次性恢复为铁锈战争原版默认值（起始金钱、迷雾、地图等）。

本插件依赖 [Password](Password.md)，并通过 Server-Core 的 `AbstractLinkGameServerData.resetRoomToDefaults()` 执行重置与大厅刷新。

## 功能概述

- 控制台命令：`resetroom <密码>`
- 校验 Password 插件提供的管理员密码
- 游戏已开始时拒绝重置，避免中局改图 / 改起始参数
- 恢复原版默认房间选项，切换回默认地图，并向房间广播成功消息
- **不会**重启服务器端口，也**不会**踢出全部玩家

## 依赖

| 依赖 | 说明 |
|------|------|
| Password | `plugin.json` 声明 `"import": "Password"` |
| Server-Core | 需包含 `resetRoomToDefaults()`（与本插件同期部署） |
| RW-HPS | `>= 3.0.0` |

请确保：

```
data/plugins/Password.jar
data/plugins/RoomReset.jar
```

并已用 `setadminpassword` 设置管理员密码。

## 安装

### 方式一：使用预构建 JAR

```
data/plugins/RoomReset.jar
```

### 方式二：从源码构建

```bash
./gradlew :plugin:RoomReset:copyToPlugins
```

重启服务器后即可在控制台使用 `resetroom`。

## 控制台命令

| 命令 | 说明 |
|------|------|
| `resetroom <密码>` | 校验密码后将房间设置恢复为原版默认值 |

### 示例

```
> resetroom mySecretPass
房间设置已恢复为原版默认值
```

房间内玩家会收到同样的系统消息。密码错误或前置条件不满足时，仅向控制台返回错误；**不会记录或广播密码**。

### 前置检查（按顺序）

| 条件 | 失败提示 |
|------|----------|
| 参数不是恰好 1 个 | `用法: resetroom <密码>` |
| Headless 游戏模块未就绪 | `游戏服务器尚未启动` |
| 游戏已开局 | `游戏已开始，无法重置房间设置` |
| Password 未注册 | `Password 插件未加载，无法校验密码` |
| 未设置管理员密码 | `尚未设置管理员密码，请先用 setadminpassword 设置` |
| 密码错误 | `管理员密码错误` |

## 恢复的默认值

与原版网络房间选项对象初始字段一致（参考逆向源码中的房间设置类）：

| 项目 | 默认值 | 说明 |
|------|--------|------|
| 地图 | Crossing Large (10p) | 路径 `maps/skirmish/[z;p10]Crossing Large (10p).tmx` |
| 地图人数标记 | `[z;p10]` | 同步写入 `room.maps.mapPlayer` |
| 起始金钱 | 索引 `0` | 对应金额 4000 |
| 迷雾 | `2` | LOS Fog |
| 核武相关字段 | `false` | 底层为 `noNukes` 语义，默认**允许**核武 |
| 共享控制 | `false` | 关闭 |
| AI 难度 | `1` | |
| 收入倍率 | `1.0` | 原版房间默认，非服务端 `defIncome` 配置 |
| 起始单位 | `1` | Normal（1 builder） |

同时会清理自定义 `mapData`，将 `mapType` 设为官方地图，并调用引擎刷新方法通知已连接客户端。

**说明：** 恢复目标是「原版新建房间默认」，不是 `ConfigServer.json` 中的管理员偏好（例如自定义默认收入）。若需要按服务器配置恢复，需另行扩展。

## 与其他机制的关系

| 机制 | 关系 |
|------|------|
| `/map`、`/fog`、`/credits` 等游戏内指令 | 互不影响；`resetroom` 一次性覆盖为默认 |
| `reBootServer()` | 本插件**不**调用；不会断开所有客户端 |
| 房间进服密码 `ConfigServer.passwd` | 独立，不会被重置 |
| 管理员 UUID 列表 | 独立 |

## 插件生命周期

```
registerCoreCommands() → 注册 resetroom
执行成功时：
  gameLinkServerData.resetRoomToDefaults()
  room.call.sendSystemMessage(...)
```

本插件无独立配置文件。

## 开发与测试

### 目录结构

```
plugin/RoomReset/
├── build.gradle.kts
├── README.md
├── src/main/
│   ├── resources/plugin.json
│   └── kotlin/net/rwhps/plugin/roomreset/
│       └── RoomResetMain.kt
└── src/test/kotlin/net/rwhps/plugin/roomreset/
    └── RoomResetDefaultsTest.kt
```

相关 Server-Core 实现：

- `AbstractLinkGameServerData.resetRoomToDefaults()`
- `LinkGameServerData.resetRoomToDefaults()`（写入引擎字段并刷新）

### 运行单元测试

```bash
./gradlew :plugin:RoomReset:test
```

## 常见问题

### Q: 提示游戏服务器尚未启动？

需先完成 Headless / 对战服务启动，使 `HeadlessModuleManage.hps` 就绪后再执行。

### Q: 开局后能否强制重置？

当前版本明确拒绝。开局后改地图与起始参数可能导致客户端不同步。

### Q: 客户端大厅选项没有立刻刷新？

确认部署的 Server-Core 已包含 `resetRoomToDefaults()` 完整实现（含 `GameEngine.netEngine.L()` 刷新）。仅旧版 Core + 新插件会导致接口缺失或行为不完整。

### Q: 是否支持游戏内 `/resetroom`？

当前仅控制台指令。游戏内聊天传密码有泄密风险；如需可自行扩展 `registerServerClientCommands`。

## 相关文档

- [Password 插件](Password.md)
- [Plugin 构成与 plugin.json](JsonConfig.md)
- [依赖其他插件的加载顺序](JsonConfig.md#依赖加载的例子)
