# Magic Architecture

## 1. 项目定位

Magic 是 Level 2 强化系统 Mod：包含可持久化物品强化、服务端战斗/护甲效果、强化台 BlockEntity 与 ScreenHandler、客户端 HUD/探矿显示，以及双向射钉枪协议。保持单 Gradle 模块，按 Registry、领域组件、网络、服务端接入、客户端和 UI 分层。

## 2. 模块边界

| 区域 | 职责 |
| --- | --- |
| `Magic` | 公共入口、兼容字段、初始化顺序 |
| `registry` | 强化台 Block/Item、BlockEntityType、ScreenHandlerType |
| `component/EnhancementSystem` | 适用规则、等级、材料、数值、物品数据读写 |
| `component/*Effects` | 服务端战斗、鞋子、护腿、头盔、射钉枪效果与短期状态 |
| `network` | Payload 定义和 codec 注册 |
| `screen` / `block` | 服务端容器、材料扣除、BlockEntity 持久化 |
| `client` / `client.gui` | 输入、渲染、HUD 选择、已加载区块探矿 |
| `mixin` | 原版实体伤害、移动、流体行走和玩家 Tick 接入 |

Common 与服务端路径不得引用 `net.minecraft.client.*`。客户端不发送强化等级、伤害、材料结果或射钉枪目标坐标。

## 3. 稳定契约

- Mod：`magic`
- Block/Item/BlockEntity/ScreenHandler：`magic:strengthening_table`
- C2S：`magic:nail_gun_action`
- S2C：`magic:nail_gun_state`
- Attribute modifier：`magic:legs_speed`、`magic:legs_height`
- NailGun action wire order：`FIRE_TOGGLE`、`CANCEL`、`PULL`

强化 type 与 button ID：

| ID | button | 目标 |
| --- | ---: | --- |
| `crit` | 0 | weapon/tool |
| `petrify` | 1 | weapon/tool |
| `instant_kill` | 2 | weapon/tool |
| `power` | 3 | weapon/tool |
| `explosion` | 4 | weapon/tool |
| `speed` | 5 | leggings |
| `height` | 6 | leggings |
| `water_soul` | 7 | boots |
| `fire_soul` | 8 | boots |
| `illumination` | 9 | helmet |
| `ore_seeker` | 10 | helmet |
| `nail_gun` | 11 | chestplate |

## 4. 数据与迁移

强化存放在 `minecraft:custom_data`：

~~~text
MagicEnhancements.<type>.Major
MagicEnhancements.<type>.Minor
~~~

`Major` 范围 `1–4`，`Minor` 范围 `1–10`；空等级为 `0/0`。读取仍兼容旧键 `MagicCritMajor`、`MagicCritMinor`，但新写入统一使用嵌套结构。Type ID、大小写和 key 名属于物品/存档契约，不得重命名而无迁移。

强化台 BlockEntity 使用原版 `Inventories.readNbt/writeNbt` 保存两个槽位。UI 选择和探矿目标是当前客户端会话状态，不写入存档。

## 5. 等级、材料与数值基线

- 每类强化最高 `IV-10`，共 `40` 个小等级。
- 每个大等级材料依次为铁锭、金锭、钻石、绿宝石。
- 目标 Minor 的单次成本为 `2^(minor-1)`，即 `1–512`。
- 创造模式不扣材料；生存模式先验证材料槽类型，再从材料槽和背包合计扣除。
- weighted 每小级权重按 Major 为 `0.001/0.002/0.005/0.02`。
- flat chance 为总等级 `/100`；速度/高度每级 `0.1`，上限 `4.0`。
- 水魂/火魂最长 `40s`；照明半径最高 `10`；探矿最高 `100` 格；射钉枪最高 `80` 格。
- 延迟爆炸 fuse `30` ticks、强度 `3`。任何调整都属于显式平衡性变更。

## 6. 网络与临时状态

射钉枪 C2S 只传 action；服务端按胸甲强化和服务端 raycast 计算有效射程与命中点。S2C 只同步 active 与服务端目标用于渲染。断线或服务器停止时必须恢复重力并清理 Hook。

其他短期状态包括水/火魂计时、照明 owner、石化、待爆炸和 HUD 选择。新增状态必须定义所有权、清理触发器和维度切换行为。

## 7. 性能边界

探矿器每 `20` client ticks 仅扫描已加载完整区块，并先用 ChunkSection palette 过滤。不得隐式加载区块。照明每 `5` server ticks 更新。若扩大探矿范围或目标数，必须先 profiling，再考虑增量索引或缓存。

## 8. 本地化

所有 Block、Item、Screen、按钮、HUD、单位、矿物名、Tooltip 和服务端消息都通过翻译键组合。`en_us` 与 `zh_cn` 由 Minecraft 原生语言设置选择；稳定 NBT/网络数据不包含本地化字符串。`verifyTranslations` 是 `check`/`build` 的强制门禁。

## 9. 验证矩阵

~~~bash
./gradlew clean build --no-daemon --stacktrace
~~~

手工至少覆盖：生存合成与材料跨背包扣除、12 种强化各一级和满级、旧暴击 NBT、BlockEntity 重载、战斗概率效果、四类护甲效果、F9 选择及取消语义、五种矿物与高度提示、射钉枪三 action/断线/维度切换，以及中英文实时切换。Dedicated Server 启动时不得加载客户端类。
