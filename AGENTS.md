# Magic AI 开发规则

## 开始前

依次阅读 `readme.md`、`README.en.md`、`fabric.mod.json`、`magic.mixins.json` 和目标源码。位于 `mc_mods` 工作区时，同时遵循上级 `../AGENTS.md`。

## 不变量

- 基线：Minecraft `1.21.3`、Java `21`、Fabric Loader `0.18.4`、Fabric API `0.114.1+1.21.3`。
- 不得无迁移地修改 Mod/Registry ID、`MagicEnhancements` NBT 结构、强化 type ID、button ID、Payload ID 或 Payload enum wire order。
- 强化升级、材料扣除、战斗效果、照明方块和射钉枪均由服务端权威处理；客户端仅渲染、选择和发送意图。
- 无玩法需求时不得调整数值、概率、材料曲线、等级上限、按键、扫描间隔或临时状态生命周期。
- 保留 `Magic` 公共静态字段、`Magic.id` 与旧暴击 NBT 读取兼容。
- Mixin 修改必须审查注入目标、服务端加载边界和失败行为。

## 双语

- 玩家可见文本必须使用 `Text.translatable`；动态单位也必须由语言键提供。
- `en_us.json` 与 `zh_cn.json` 必须同步，键集合和占位符语义一致。
- 语言使用 Minecraft 原生“设置 → 语言”切换；不得把翻译后的字符串写入物品 NBT、BlockEntity 或网络包。
- 命令、NBT key、Registry ID 和内部状态 key 保持英文稳定标识。

## 完成门禁

~~~bash
./gradlew clean build --no-daemon --stacktrace
~~~

同时完成强化台、战斗、护甲、HUD、探矿、射钉枪、存档兼容和 Dedicated Server 回归。
