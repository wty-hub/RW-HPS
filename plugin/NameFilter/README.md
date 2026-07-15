# NameFilter 插件

按正则过滤玩家昵称；不匹配则踢出。修改正则需管理员密码。

完整文档见：[docs/zh/plugin/NameFilter.md](../../docs/zh/plugin/NameFilter.md)

## 快速开始

```bash
# 需先部署 Password 插件并设置管理员密码
./gradlew :plugin:Password:copyToPlugins
./gradlew :plugin:NameFilter:copyToPlugins

# 运行测试
./gradlew :plugin:NameFilter:test
```

## 控制台

```
namefilter status
namefilter setpattern <密码> <正则>
```

## 配置

`data/plugins/NameFilter/NameFilterConfig.json`

- `enabled` / `namePattern` / `kickMessage` / `kickDurationSeconds`

`plugin.json` 中声明 `"import": "Password"`。
