# RoomReset 插件

密码保护的控制台指令，将房间设置恢复为原版默认值（金钱、迷雾、地图等）。

完整文档见：[docs/zh/plugin/RoomReset.md](../../docs/zh/plugin/RoomReset.md)

## 快速开始

```bash
# 需先部署 Password 插件并设置管理员密码
./gradlew :plugin:Password:copyToPlugins
./gradlew :plugin:RoomReset:copyToPlugins

# 运行测试
./gradlew :plugin:RoomReset:test
```

## 控制台

```
resetroom <密码>
```

游戏已开始时拒绝执行。成功后向房间广播「房间设置已恢复为原版默认值」。

`plugin.json` 中声明 `"import": "Password"`。
