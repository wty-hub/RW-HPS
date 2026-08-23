# TeamChange 插件

强制修改玩家队伍（支持开局后），核心内置的 `team` 指令仅限大厅，本插件通过全量存档同步实现开局后改队伍。

## 指令

| 指令 | 适用 | 说明 |
|------|------|------|
| `.forceteam <席位> <队伍>` | 游戏内（管理员） | 修改指定席位玩家队伍 |
| `forceteam <席位> <队伍>` | 控制台 | 同上 |

- `<席位>`：1-based 玩家席位
- `<队伍>`：1-based 队伍编号（内部存储为 0-based，与内置 `team` 一致）

## 原理

- 大厅：修改玩家同盟字段后由 TEAM_LIST(115) 正常同步（与内置 `team` 一致）
- 开局后：修改 `player.team` 后调用 `allPlayerSync()`，广播 SYNC(35) 全量 gameSave，全员重载网络存档，强制应用新队伍

## 注意事项

- 开局后改队伍会导致所有客户端短时卡顿（重载网络存档），属实验性功能
- 修改的是**玩家同盟**（谁是友军），不改变单位所有权
- 官方协议不支持开局后改队，此功能由服务端强制同步实现，需实机验证雾战/索敌/胜负边界

## 部署

```bash
./gradlew :plugin:TeamChange:copyToPlugins
```

## 测试

```bash
./gradlew :plugin:TeamChange:test
```

测试覆盖：

- `TeamChangeServiceTest`：服务层纯逻辑单测（大厅不同步 / 开局同步 / 同队伍同步）
- `TeamChangeCommandTest`：通过真实 `CommandHandler` 验证指令注册、参数校验、管理员权限、
  席位越界、开局前后对 `allPlayerSync` 的触发（注入假游戏模块，不依赖真实游戏 boot）

注意：`GameStartInit.start` 依赖 core jar 流（`FileUtils.getMyCoreJarStream`），在 JUnit
环境中无法满足，因此**未**做真实游戏 boot 的端到端测试；真实环境（开局后改队伍 →
SYNC(35) 全员强制同步）需实机验证。
