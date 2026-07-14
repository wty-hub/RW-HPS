# Password 插件

管理员密码存储与跨插件校验 API。

完整文档见：[docs/zh/plugin/Password.md](../../docs/zh/plugin/Password.md)

## 快速开始

```bash
# 构建并部署
./gradlew :plugin:Password:copyToPlugins

# 运行测试
./gradlew :plugin:Password:test
```

## 控制台

```
setadminpassword <密码>
clearadminpassword
adminpassword status
```

## 依赖方调用

```kotlin
import net.rwhps.server.plugin.api.AdminPassword

AdminPassword.verify("用户输入的密码")
```

`plugin.json` 中声明 `"import": "Password"` 以保证加载顺序。
