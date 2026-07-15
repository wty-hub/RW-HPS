# PlayerIpGeo 插件

玩家加入时根据 IP 查询地理位置，并输出到服务器控制台日志。

完整文档见：[docs/zh/plugin/PlayerIpGeo.md](../../docs/zh/plugin/PlayerIpGeo.md)

## 快速开始

```bash
# 构建会同步/下载 IP2Location 数据库到插件资源
./gradlew :plugin:PlayerIpGeo:copyToPlugins
```

可选：使用官方 Token 更新数据库

```bash
export IP2LOCATION_TOKEN=你的token
./gradlew :plugin:PlayerIpGeo:downloadIp2Location
```

## 日志示例

```
[PlayerIpGeo] 玩家 Alice 进入 | IP: 1.2.3.4 | 地理位置: China|Beijing|Beijing
```

无控制台命令；无独立配置文件。
