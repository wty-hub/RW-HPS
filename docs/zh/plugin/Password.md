# Password 插件

Password 是 RW-HPS 的管理员密码插件。它负责在服务端安全存储管理员密码，并通过 Server-Core 提供的 `AdminPassword` API 向其他插件暴露校验能力。

## 功能概述

- 在控制台设置、更新、清除管理员密码
- 密码以 **SHA-256 + 随机 salt** 哈希存储，配置文件中不包含明文
- 插件加载完成后注册到 `net.rwhps.server.plugin.api.AdminPassword`，供依赖插件调用
- 与 `ConfigServer.json` 中的 `passwd`（进服房间密码）相互独立

## 安装

### 方式一：使用预构建 JAR

将 `Password.jar` 放入服务器目录下的 `data/plugins/`：

```
data/plugins/Password.jar
```

### 方式二：从源码构建

```bash
./gradlew :plugin:Password:copyToPlugins
```

构建产物会自动复制到 `data/plugins/Password.jar`。

### 版本要求

- RW-HPS `>= 3.0.0`
- Server-Core 需包含 `net.rwhps.server.plugin.api.AdminPassword`（与 Password 插件同期部署）

重启服务器后，控制台应出现类似日志：

```
[Password] 管理员密码服务已注册 (尚未设置密码，请使用 setadminpassword)
```

## 控制台命令

| 命令 | 说明 |
|------|------|
| `setadminpassword <密码>` | 设置或更新管理员密码（密码可含空格，会合并后续所有参数） |
| `clearadminpassword` | 清除已设置的管理员密码 |
| `adminpassword` | 查看当前是否已设置密码 |
| `adminpassword status` | 同上 |

### 示例

```
> setadminpassword mySecretPass
管理员密码已更新

> adminpassword status
管理员密码: 已设置

> clearadminpassword
管理员密码已清除
```

## 配置文件

首次启动后自动生成：

```
data/plugins/Password/PasswordConfig.json
```

示例内容：

```json
{
  "salt": "a1b2c3...",
  "passwordHash": "9f86d081..."
}
```

| 字段 | 说明 |
|------|------|
| `salt` | 随机盐值，每次设置密码时重新生成 |
| `passwordHash` | `SHA-256(salt + 明文密码)` 的十六进制字符串 |

**注意：**

- 请勿手动编辑 `passwordHash`；应通过 `setadminpassword` 命令设置密码
- 若 `salt` 与 `passwordHash` 均为空，表示尚未配置密码
- 修改配置后需重启服务器才会重新加载（密码在 `onEnable` 时读取）

## 安全说明

| 项目 | 说明 |
|------|------|
| 存储方式 | 仅保存哈希，不保存明文 |
| 哈希算法 | SHA-256，格式为 `sha256Hex(salt + password)` |
| Salt | 每次设置密码时生成 32 位随机字符串 |
| 空密码 | 不允许设置空白密码 |
| 未配置时 | `AdminPassword.verify()` 始终返回 `false` |

Password 插件提供的是**插件级管理员密码**，与以下机制无关：

- `ConfigServer.json` 的 `passwd`（玩家加入房间时的游戏密码）
- `Data.core.admin` 的 UUID 管理员列表
- HTTP API 的 `webToken`

如需在验证密码后授予游戏内管理员权限，需在依赖插件中自行桥接。

## 依赖插件接入指南

其他插件可通过 Server-Core 的 `AdminPassword` 对象校验密码，无需直接依赖 Password 插件的 JAR。

### 1. 声明加载顺序

在依赖插件的 `plugin.json` 中加入 `import`，确保 Password 先加载：

```json
{
  "name": "MyPlugin",
  "import": "Password",
  "main": "com.example.myplugin.MyPluginMain",
  "version": "1.0.0",
  "supportedVersions": ">= 3.0.0"
}
```

`import` 的值必须与 Password 插件的 `name` 字段一致（即 `"Password"`）。

### 2. 添加 Gradle 依赖

```kotlin
dependencies {
    compileOnly(project(":Server-Core"))
}
```

### 3. 调用 API

```kotlin
import net.rwhps.server.plugin.api.AdminPassword

// Password 插件是否已加载并完成注册
if (!AdminPassword.isAvailable()) {
    // Password 插件未安装或未启用
}

// 是否已通过 setadminpassword 设置了密码
if (!AdminPassword.isConfigured()) {
    // 尚未配置密码，verify 将始终返回 false
}

// 校验用户输入的明文密码
if (AdminPassword.verify(userInput)) {
    // 密码正确，允许执行受保护操作
} else {
    // 密码错误、未配置或 Password 插件未加载
}
```

### 4. API 参考

类路径：`net.rwhps.server.plugin.api.AdminPassword`

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `isAvailable()` | `Boolean` | Password 插件是否已注册校验器 |
| `isConfigured()` | `Boolean` | 是否已设置管理员密码 |
| `verify(password: String)` | `Boolean` | 校验密码；未加载、未配置或密码错误时返回 `false` |

接口 `AdminPasswordVerifier` 由 Password 插件内部实现，依赖方通常不需要直接实现。

### 5. 完整示例：受密码保护的命令

```kotlin
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.plugin.Plugin
import net.rwhps.server.plugin.api.AdminPassword
import net.rwhps.server.util.game.command.CommandHandler

class MyPluginMain : Plugin() {
    override fun registerServerClientCommands(handler: CommandHandler) {
        handler.register("protectedcmd", "<password> <action...>", "myplugin.protected") { args, player ->
            val password = args[0]
            if (!AdminPassword.verify(password)) {
                player.sendSystemMessage("管理员密码错误或未配置")
                return@register
            }
            // 密码正确，继续执行
            player.sendSystemMessage("操作已授权")
        }
    }
}
```

## 插件生命周期

```
onEnable()     → 读取 PasswordConfig.json，创建 PasswordVerifierImpl
registerCoreCommands() → 注册 setadminpassword / clearadminpassword / adminpassword
init()         → AdminPassword.register(verifier)
onDisable()    → AdminPassword.unregister(verifier)
```

依赖插件应在 `init()` 之后（或运行时）调用 `AdminPassword.verify()`，此时 Password 插件已完成注册。

## 开发与测试

### 目录结构

```
plugin/Password/
├── build.gradle.kts
├── src/main/
│   ├── resources/plugin.json
│   └── kotlin/net/rwhps/plugin/password/
│       ├── PasswordMain.kt
│       ├── PasswordConfig.kt
│       └── PasswordVerifierImpl.kt
└── src/test/kotlin/net/rwhps/plugin/password/
    ├── PasswordVerifierTest.kt
    └── AdminPasswordIntegrationTest.kt
```

### 运行单元测试

```bash
./gradlew :plugin:Password:test
```

测试报告：`plugin/Password/build/reports/tests/test/index.html`

## 常见问题

### Q: 设置了密码，但依赖插件校验始终失败？

1. 确认 Password 插件已成功加载（查看启动日志）
2. 确认依赖插件的 `plugin.json` 中有 `"import": "Password"`
3. 确认 Server-Core 版本包含 `AdminPassword` API
4. 使用 `adminpassword status` 确认密码已设置

### Q: 能否与房间进服密码（ConfigServer.passwd）同步？

当前版本不支持。两者独立维护。如有需要，可在依赖插件中自行读取 `Data.configServer.passwd` 或扩展 Password 插件。

### Q: 玩家如何在游戏内输入密码？

Password 插件本身不提供游戏内 UI。依赖插件可结合 RW-HPS 的输入框 API（如 `sendRelayServerType`）收集玩家输入，再调用 `AdminPassword.verify()`。

### Q: 忘记密码怎么办？

在控制台执行 `setadminpassword <新密码>` 重新设置，或删除 `data/plugins/Password/PasswordConfig.json` 后重启再设置。

## 相关文档

- [Plugin 构成与 plugin.json](JsonConfig.md)
- [依赖其他插件的加载顺序](JsonConfig.md#依赖加载的例子)
- [服务器配置（房间密码 passwd）](../run/Config.md)
