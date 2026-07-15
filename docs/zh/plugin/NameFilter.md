# NameFilter 插件

NameFilter 是 RW-HPS 的玩家昵称过滤插件。玩家进入房间时，若昵称不匹配配置的正则表达式，将被踢出。

修改正则需要管理员密码，因此本插件依赖 [Password](Password.md)。

## 功能概述

- 在玩家加入（`PlayerJoinEvent`）时校验昵称
- 通过配置文件自定义正则、踢出消息与封禁时长
- 控制台可查看状态；修改正则需通过 Password 插件校验管理员密码
- 运行时可热更新正则（无需重启），`enabled` 等开关仍需改配置后重启生效

## 依赖

| 依赖 | 说明 |
|------|------|
| Password | `plugin.json` 中声明 `"import": "Password"`，须先加载 |
| RW-HPS | `>= 3.0.0`，并已包含 `AdminPassword` API |

请确保 `data/plugins/` 中同时存在：

```
data/plugins/Password.jar
data/plugins/NameFilter.jar
```

并先用 `setadminpassword` 设置管理员密码，否则无法通过控制台修改正则。

## 安装

### 方式一：使用预构建 JAR

将 `NameFilter.jar` 放入：

```
data/plugins/NameFilter.jar
```

### 方式二：从源码构建

```bash
./gradlew :plugin:NameFilter:copyToPlugins
```

构建产物会复制到 `data/plugins/NameFilter.jar`。

重启服务器后，控制台应出现类似日志：

```
[NameFilter] 已启用，昵称正则: ^[\w\u4e00-\u9fa5]{2,20}$
```

若配置中 `enabled=false`：

```
[NameFilter] 插件已禁用 (enabled=false)
```

## 控制台命令

| 命令 | 说明 |
|------|------|
| `namefilter` | 查看当前状态（同 `status`） |
| `namefilter status` | 查看启用状态、正则、踢出消息与时长 |
| `namefilter setpattern <密码> <正则>` | 校验管理员密码后更新昵称正则并立即生效 |

正则可包含空格：密码之后的所有参数会合并为一条正则字符串。

### 示例

```
> namefilter status
NameFilter 状态:
  启用: true
  昵称正则: ^[\w\u4e00-\u9fa5]{2,20}$
  踢出消息: 您的昵称不符合服务器要求，请修改后重试
  踢出时长(秒): 0
修改正则: namefilter setpattern <密码> <正则>

> namefilter setpattern mySecretPass ^\[.+\][\w\u4e00-\u9fa5]{2,16}$
昵称正则已更新: ^\[.+\][\w\u4e00-\u9fa5]{2,16}$
```

密码错误、Password 未加载、或尚未设置管理员密码时，会仅向控制台返回错误，不会广播密码。

## 配置文件

首次启动后自动生成：

```
data/plugins/NameFilter/NameFilterConfig.json
```

默认内容对应字段如下：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | Boolean | `true` | 是否启用过滤；`false` 时不注册事件监听 |
| `namePattern` | String | `^[\w\u4e00-\u9fa5]{2,20}$` | 昵称须**完全匹配**的正则（Kotlin / Java 正则） |
| `kickMessage` | String | `您的昵称不符合服务器要求，请修改后重试` | 踢出时发给玩家的提示 |
| `kickDurationSeconds` | Int | `0` | 踢出相关时长（秒），传给 `player.kickPlayer` |

### 默认正则含义

`^[\w\u4e00-\u9fa5]{2,20}$` 大致要求：

- 长度 2～20
- 仅允许字母、数字、下划线（`\w`）与常用汉字
- 不允许空格、多数符号

可用玩家示例：`Player_01`、`测试玩家`  
会被拒绝示例：`a`（过短）、`bad name`（空格）、`name@mail`（符号）

**注意：**

- 修改 `enabled`、`kickMessage`、`kickDurationSeconds`：编辑 JSON 后需**重启服务器**
- 修改 `namePattern`：推荐使用 `namefilter setpattern`（立即生效并写回配置）；也可改 JSON 后重启
- 正则无效时插件会记录错误并**不启用过滤**（不会误踢所有人）

## 行为说明

1. 玩家触发 `PlayerJoinEvent`
2. 用当前正则对 `player.name` 做 `matches`（完整匹配）
3. 不匹配则 `kickPlayer(kickMessage, kickDurationSeconds)`，并在控制台记录：

```
[NameFilter] 已踢出玩家 xxx，昵称不符合正则: ...
```

本插件**不会**修改玩家昵称，也不会代替进服密码或 UUID 管理员体系。

## 插件生命周期

```
onEnable()              → 读取 NameFilterConfig.json
registerCoreCommands()  → 注册 namefilter
registerEvents()        → enabled 且正则有效时注册 NameFilterEvent
```

`setpattern` 成功后会更新内存中的 `NameFilterState.pattern` 并 `config.save()`。

## 开发与测试

### 目录结构

```
plugin/NameFilter/
├── build.gradle.kts
├── README.md
├── src/main/
│   ├── resources/plugin.json
│   └── kotlin/net/rwhps/plugin/namefilter/
│       ├── NameFilterMain.kt
│       ├── NameFilterConfig.kt
│       ├── NameFilterState.kt
│       └── NameFilterEvent.kt
└── src/test/kotlin/net/rwhps/plugin/namefilter/
    └── NameFilterPatternTest.kt
```

### 运行单元测试

```bash
./gradlew :plugin:NameFilter:test
```

## 常见问题

### Q: setpattern 提示 Password 插件未加载？

确认已安装并启用 [Password](Password.md)，且 NameFilter 的 `plugin.json` 含 `"import": "Password"`。

### Q: 提示尚未设置管理员密码？

在控制台执行 `setadminpassword <密码>`。

### Q: 改了 enabled=false 仍在过滤？

配置在 `onEnable` / `registerEvents` 时读取；请重启服务器。

### Q: 中文昵称被误踢？

默认正则已包含 `\u4e00-\u9fa5`。若需允许更多字符（如空格、方括号标签），请用 `setpattern` 更换正则。

## 相关文档

- [Password 插件](Password.md)
- [Plugin 构成与 plugin.json](JsonConfig.md)
- [依赖其他插件的加载顺序](JsonConfig.md#依赖加载的例子)
