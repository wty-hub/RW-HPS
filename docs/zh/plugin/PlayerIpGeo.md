# PlayerIpGeo 插件

PlayerIpGeo 在玩家加入房间时，根据其连接 IP 查询地理位置，并输出到服务器控制台日志。

定位数据使用内置的 IP2Location LITE DB5 数据库（国家 / 地区 / 城市）。

## 功能概述

- 监听 `PlayerJoinEvent`
- 读取玩家连接 IP，查询地理位置
- 控制台输出示例：`[PlayerIpGeo] 玩家 xxx 进入 | IP: x.x.x.x | 地理位置: China|Beijing|Beijing`
- 无控制台命令、无独立运行时配置文件
- 查询失败或不支持的 IP 状态时显示 `未知` 或 `未知 (status)`

## 依赖

| 依赖 | 说明 |
|------|------|
| RW-HPS | `>= 3.0.0` |
| IP2Location 运行库 | 构建时 `compileOnly("net.renfei:ip2location:1.2.1")`；运行侧由服务端/依赖 classpath 提供对应能力 |
| `ip2location.7z` | 必须打进 JAR 资源：内含 `IP2LOCATION-LITE-DB5.BIN` |

本插件**不**依赖 Password。

## 安装

### 方式一：使用预构建 JAR

```
data/plugins/PlayerIpGeo.jar
```

### 方式二：从源码构建

```bash
./gradlew :plugin:PlayerIpGeo:copyToPlugins
```

`processResources` 会自动执行 `downloadIp2Location`，确保资源中有数据库：

1. 若环境变量 `IP2LOCATION_TOKEN` 已设置：从 [IP2Location LITE](https://lite.ip2location.com/) 下载 DB5 并打包为 `ip2location.7z`
2. 否则若存在 `Server-Core/src/main/resources/ip2location.7z`：复制到插件资源目录
3. 两者皆无：构建失败，需先准备数据库

启动后懒加载数据库时可见：

```
[PlayerIpGeo] IP2Location 数据库已加载 (IP2LOCATION-LITE-DB5)
```

## 行为说明

1. 插件 `onEnable` 时用 `127.0.0.1` 预热一次查询（触发数据库加载）
2. 玩家加入时：
   - 取 `player.con` 的连接 IP
   - `PlayerIpGeoLookup.lookup(ip)` → `国家|地区|城市`
   - `Log.clog` 打印结果

不会踢人、不会改名、不会向游戏内玩家广播地理位置。

### 查询结果格式

| 情况 | 输出 |
|------|------|
| 查询成功 | `Country|Region|City`（英文库长国名等，视 DB 内容而定） |
| IP 为空 | `未知` |
| 库返回非 OK | `未知 (status)` |
| 异常 | `未知`，并写错误日志 |

## 构建任务补充

| 任务 | 说明 |
|------|------|
| `:plugin:PlayerIpGeo:downloadIp2Location` | 单独同步 / 下载 IP 库到 `src/main/resources/ip2location.7z` |
| `:plugin:PlayerIpGeo:jar` | 打包插件 |
| `:plugin:PlayerIpGeo:copyToPlugins` | 复制到 `data/plugins/` |

免费 Token 可在 IP2Location LITE 注册后获得，然后：

```bash
export IP2LOCATION_TOKEN=你的token
./gradlew :plugin:PlayerIpGeo:downloadIp2Location
```

## 插件生命周期

```
onEnable()       → 预热 lookup("127.0.0.1")，触发 IP 库加载
registerEvents() → 注册 PlayerIpGeoEvent
```

## 开发与测试

### 目录结构

```
plugin/PlayerIpGeo/
├── build.gradle.kts
├── README.md
└── src/main/
    ├── resources/
    │   ├── plugin.json
    │   └── ip2location.7z          # 构建时生成/同步，勿依赖手改
    └── kotlin/net/rwhps/plugin/playeripgeo/
        ├── PlayerIpGeoMain.kt
        ├── PlayerIpGeoEvent.kt
        └── PlayerIpGeoLookup.kt
```

当前仓库未附带独立单元测试；以构建成功与进服日志为准验证。

## 隐私与合规提示

本插件会在服务端日志中记录玩家 IP 与粗略地理位置。请按你所在地区与运营规范告知用户，并妥善保管日志，避免公开泄露。

IP2Location LITE 数据库有其自身许可条款；商用或再分发前请自行确认 [IP2Location](https://www.ip2location.com/) 许可要求。

## 常见问题

### Q: 启动报 `ip2location.7z not found`？

JAR 资源未打入数据库。请重新执行 `downloadIp2Location` 后再 `copyToPlugins`，确认打包进资源。

### Q: 地理位置总是「未知」？

- 确认玩家连接拿到的是公网 IP（经反向代理时可能是内网/代理 IP）
- 本地 / 局域网进服常见为私网地址，库可能无法解析
- 查看错误日志中是否有查询异常

### Q: 如何更新 IP 库？

设置 `IP2LOCATION_TOKEN` 后重新运行 `downloadIp2Location` 并重新打包部署插件。

## 相关文档

- [Plugin 构成与 plugin.json](JsonConfig.md)
- [服务器运行](../run/Run.md)
