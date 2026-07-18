# Magic — Minecraft 1.21.3 Fabric 装备强化与长期成长 Mod

简体中文 | [English](README.en.md)

[![Release](https://img.shields.io/github/v/release/zoyluoblue/Minecraft_Magic?display_name=tag&sort=semver)](https://github.com/zoyluoblue/Minecraft_Magic/releases/latest)
[![Build](https://github.com/zoyluoblue/Minecraft_Magic/actions/workflows/build.yml/badge.svg)](https://github.com/zoyluoblue/Minecraft_Magic/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.3-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric_Loader-0.18.4%2B-DBD0B4)](https://fabricmc.net/)
[![License](https://img.shields.io/github/license/zoyluoblue/Minecraft_Magic)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-zoyluo--magic-1BD96A?logo=modrinth&logoColor=white)](https://modrinth.com/mod/zoyluo-magic)

[在 Modrinth 下载](https://modrinth.com/mod/zoyluo-magic) · [GitHub Releases](https://github.com/zoyluoblue/Minecraft_Magic/releases/latest) · [三分钟快速开始](#三分钟快速开始) · [查看 12 种强化](#12-种可叠加强化) · [常见问题](#常见问题-faq)

**Modrinth 下载说明：** [zoyluo-magic](https://modrinth.com/mod/zoyluo-magic) 是推荐的发布页，可查看 Minecraft 与 Fabric 兼容标签、版本更新说明、宣传图库并下载正式 JAR；[GitHub Releases](https://github.com/zoyluoblue/Minecraft_Magic/releases/latest) 同步提供发布构件与源码仓库关联。

![Minecraft Magic 装备强化 Mod 宣传图：12 种可叠加强化、四阶 40 级成长与多人服务端权威](./demo/readme/2.1.0/01-minecraft-magic-equipment-enhancement.png)

## 30 秒了解 Magic

| 项目 | 内容 |
| --- | --- |
| 核心玩法 | 探索与收集材料 → 合成强化台 → 逐级强化装备 → 挑战更高难度内容 |
| 成长规模 | 4 阶、40 级；每一种强化独立升级 |
| 强化数量 | 武器/工具 5 种，护甲 7 种，共 12 种 |
| 叠加规则 | 同一件装备可以同时拥有该装备部位支持的多种强化 |
| 生存材料 | I 铁锭、II 金锭、III 钻石、IV 绿宝石 |
| 多人模式 | 强化、材料、战斗、充能和射钉枪状态由服务端权威结算 |
| 语言 | 简体中文与 English，跟随 Minecraft 原生语言设置 |

## 为什么选择 Magic

- **长期刷装成长：** 每种强化从 `I-1` 成长至 `IV-10`，适合持续探索、收集与养成。
- **12 种可叠加强化：** 战斗、移动、探索、流体行走与钢索移动各有明确用途。
- **原创星环强化台：** 三重星环围绕槽位装备旋转，并随最高适用强化显示蓝、紫、粉、橙四阶能量。
- **多人优先：** 关键结果在服务端结算；远程武器保存发射时快照，其他玩家也能看到权威钢索和强化台事件。
- **玩家自行选择 HUD：** 按 `Left Option + B`（macOS）或 `Left Alt + B`（Windows）勾选要显示的装备数值和探矿目标，减少不需要的信息。
- **不替代原版附魔：** Magic 增加一套独立的装备强化成长，原版附魔系统仍可正常使用。

## 兼容性与安装

| 依赖 | 要求 |
| --- | --- |
| Minecraft | `1.21.3` |
| Java | `21+` |
| Fabric Loader | `0.18.4+` |
| Fabric API | `0.114.1+1.21.3` 或更高的 1.21.3 兼容版本 |
| Magic | 客户端与服务端使用相同版本 |

安装步骤：

1. 安装 Minecraft 1.21.3 对应的 Fabric Loader 与 Fabric API。
2. 从 [Modrinth](https://modrinth.com/mod/zoyluo-magic) 或 [GitHub Releases](https://github.com/zoyluoblue/Minecraft_Magic/releases/latest) 下载非 `sources` 的 Magic JAR。
3. 把 Magic JAR 和 Fabric API 放入游戏或服务器的 `mods` 文件夹。
4. 多人服务器需要客户端与服务端都安装 Magic 和 Fabric API，并保持 Magic 版本一致。

## 三分钟快速开始

### 1. 合成强化台

使用 5 个铁锭、1 个绿宝石、2 个紫水晶碎片和 1 个锻造台合成强化台。

```text
I E I
A S A
I I I

I = 铁锭    E = 绿宝石
A = 紫水晶碎片    S = 锻造台
```

也可以在创造模式的“功能性方块”分类中获取，或使用：

```mcfunction
/give @p magic:strengthening_table
```

![Minecraft Magic 强化台合成配方与从放入装备到升至 IV-10 的五步教学](./demo/readme/2.1.0/02-strengthening-table-crafting-guide.png)

### 2. 强化一件装备

1. 放置强化台并右键打开。
2. 左槽放入可强化的武器、工具或护甲。
3. 选择要提升的强化类型。
4. 右槽放入当前阶级材料；需求超过 64 个时，强化台会合并计算材料槽与玩家背包。
5. 材料足够后点击强化；升级必定成功，不会随机失败或损坏装备。
6. 查看物品 Tooltip，或按 `Left Option + B`（macOS）/ `Left Alt + B`（Windows）选择需要显示在 HUD 中的强化信息。

## 四阶 40 级成长

每一种强化独立从 `I-1` 升到 `IV-10`。每阶的第 1–10 个小等级分别消耗 `1、2、4、8、16、32、64、128、256、512` 个材料，因此每阶升满需要 `1,023` 个对应材料。

![Minecraft Magic 四阶强化材料、等级范围与 1 到 512 的成长成本](./demo/readme/2.1.0/03-four-tier-material-progression.png)

| 阶级 | 总等级 | 材料 | 星环颜色 | 每阶总消耗 |
| --- | ---: | --- | --- | ---: |
| I | 1–10 | 铁锭 | 蓝 | 1,023 |
| II | 11–20 | 金锭 | 紫 | 1,023 |
| III | 21–30 | 钻石 | 粉 | 1,023 |
| IV | 31–40 | 绿宝石 | 橙 | 1,023 |

单种强化从零升满共需 `1,023` 铁锭、`1,023` 金锭、`1,023` 钻石和 `1,023` 绿宝石。材料不足时不会修改装备或背包；创造模式仍需选择正确材料并准备足量材料，但成功后不会实际扣除。

## 12 种可叠加强化

### 武器与工具：5 种战斗强化

剑、采掘工具、弓、弩、三叉戟和多数具有耐久度的非护甲物品可以获得以下 5 种强化。同一件兼容物品可以同时拥有多种效果。

![Minecraft Magic 五种可叠加武器强化及其 IV-10 满级战斗效果](./demo/readme/2.1.0/04-stackable-weapon-enhancements.png)

| 强化 | IV-10 满级效果 | 关键规则 |
| --- | --- | --- |
| 暴击 | 28% 概率使本次伤害 ×2 | 与力量加成共同结算 |
| 石化 | 40% 概率停止目标移动 4 秒 | 再次触发只延长，不叠加失控状态 |
| 一击 | 40% 概率把原版伤害至少提高到 1,000,000 | 不是无条件 `kill`；保护规则和不死图腾仍可介入 |
| 力量 | 每次攻击伤害 +28% | 常驻生效，不需要随机触发 |
| 爆炸 | 40% 概率在 30 个服务器 tick 后产生强度 3.0 的原版爆炸 | 攻击者失效时安全取消 |

五种效果支持近战、弓箭、弩的物理箭和投掷三叉戟；弩发射的烟花火箭保持原版行为。

### 护甲：7 种探索与移动强化

![Minecraft Magic 头盔、胸甲、护腿和鞋子的七种护甲强化与 HUD 操作](./demo/readme/2.1.0/05-armor-enhancements-and-hud.png)

| 装备部位 | 强化 | IV-10 满级效果 | 关键限制 |
| --- | --- | --- | --- |
| 头盔 | 照明 | 显示值 10；移动光源映射为原版光照等级 12–15 | 显示值不是 10 格照明半径 |
| 头盔 | 探矿 | XYZ 各轴最高 100 格 | 只扫描客户端已加载区块；箭头与距离显示在右上角 |
| 胸甲 | 射钉枪 | 最远 80 格 | 需要客户端与服务端版本一致 |
| 护腿 | 速度 | 基础移动速度 +400%，包含空中加速 | 高速移动需要注意碰撞与地形 |
| 护腿 | 高度 | Jump Strength 基础属性 +400%，摔落伤害 -80% | 实际跳跃高度不是线性 +400% |
| 鞋子 | 水魂 | 最多 40 秒水面行走充能 | 身体接触水时持续消耗；完全离水后按 1 秒/秒恢复 |
| 鞋子 | 火魂 | 最多 40 秒岩浆行走与防灼烧充能 | 充能耗尽后相关保护关闭 |

水魂和火魂充能属于靴子本身，会随复制、交易、掉落、重连和服务器重启保存。穿水魂鞋在水面行走时，按住当前潜行键（默认 `Shift`）可主动下潜；进入水下后恢复原版游泳与上浮。即使踩在水下的实体方块上，只要身体仍接触水，水魂就不会开始恢复。

## 星环强化台

放置后的强化台使用原创基座、原创贴图和三重动态星环：

- 左槽装备会悬浮在环心，右槽材料以缩小投影沿基座轨道运行。
- 星环根据装备最高适用强化显示 I 蓝、II 紫、III 粉、IV 橙；同一阶内等级越高，视觉强度越高。
- 强化成功时，能量先向中心收束，再产生对应阶级颜色的爆发。
- 方块占地仍为 `1×1`；只有约 `12/16` 格高的基座参与碰撞，星环不会形成空气墙。
- 动画由客户端世界时间渲染；服务端只同步槽位变化和成功事件，不发送逐帧网络包。

强化物品会显示独立的轮廓光圈：I 蓝、II 紫、III 粉、IV 橙，同一阶内从 1 到 10 级逐步增加光圈宽度与强度。背包、热栏和第一人称手持已完成实机验证；第三人称、掉落和展示框使用同一物品渲染路径，仍在兼容验收。光圈只覆盖物品轮廓外侧，不修改原贴图、本体颜色、明暗或原版附魔 glint，也不会照亮周围环境。穿在身上的盔甲暂不显示这层轮廓；头盔“照明”仍是独立强化效果。

## HUD 与射钉枪操作

| 操作 | 默认按键 | 说明 |
| --- | --- | --- |
| 打开 HUD 选择页 | macOS：`Left Option + B`；Windows：`Left Alt + B` | `B` 可在 Minecraft 按键设置中重绑，左侧修饰键固定 |
| 从水面主动下潜 | 当前潜行键，默认 `Shift` | 穿水魂鞋水面行走时按住即可下水 |
| 发射/释放射钉枪 | `Alt + 1` | 仅在未打开界面且装备射钉枪胸甲时读取 |
| 开始牵引 | 当前跳跃键，默认 `Space` | 锚定后按一次，沿钢索方向直线拉向锚点 |
| 释放射钉枪 | 再按 `Alt + 1` 或 `Esc` | 到达、受阻、超时、死亡或换维度也会自动释放 |

HUD 选择页按主手、副手、头盔、胸甲、护腿和鞋子分组。玩家自行勾选后，左下角显示装备位置、物品名、强化类型、等级和数值；探矿箭头与距离位于屏幕右上角。HUD 与探矿选择只保留在当前客户端会话，重启后需要重新选择。

## 多人游戏与可靠性

- 强化升级、材料扣除、战斗效果、水火魂充能、移动光源和射钉枪状态均由服务端权威结算。
- 远程攻击保存发射时的武器快照；箭或三叉戟飞行期间换手、丢弃或修改原武器，不会改变本次命中。
- 只有原版伤害成功后，暴击反馈、石化、待爆和一击副作用才会提交，因此 PvP、队伍规则、创造模式和不死图腾仍可介入。
- 射钉枪输入具备序列、幂等和限流；附近正在追踪玩家的客户端会看到相同的钢索状态。
- 强化台使用原版 BlockEntity 更新与方块事件同步槽位和成功反馈，不依赖逐帧网络同步。

## 演示视频

[在 YouTube 观看 Minecraft Magic 玩法演示](https://www.youtube.com/watch?v=J3A4pPPDw4k)

## 常见问题 FAQ

### Magic 是什么？

Magic 是一个面向 Minecraft 1.21.3 Fabric 的装备强化与长期成长 Mod，提供原创强化台、4 阶 40 级成长、12 种可叠加强化和多人服务端权威结算。

### Magic 会替代原版附魔台或原版附魔吗？

不会。Magic 增加独立的强化台与装备成长层，原版附魔系统仍可正常使用。

### 支持哪些 Minecraft、Fabric 和 Java 版本？

当前版本支持 Minecraft `1.21.3`、Java `21+`、Fabric Loader `0.18.4+`，并需要 Fabric API `0.114.1+1.21.3` 或更高的 1.21.3 兼容版本。

### 客户端和服务端都需要安装吗？

需要。多人游戏时客户端与服务端都要安装 Magic 和 Fabric API，并使用相同的 Magic 版本。

### 支持多人服务器吗？

支持。强化、材料、战斗、充能、移动光源和射钉枪状态都由服务端权威处理。

### 同一件装备可以叠加多种强化吗？

可以，但只能叠加该装备部位支持的类型。例如一把剑可以同时拥有 5 种战斗强化，鞋子可以同时拥有水魂和火魂。

### 强化会失败、降级或损坏装备吗？

不会。满足物品、等级和材料条件后升级必定成功；当前没有随机失败、降级或装备损坏机制。

### 一次升级需要超过 64 个材料时怎么办？

先在材料槽中选择正确材料。强化台会合并计算材料槽与玩家背包中的同类材料，并以完整事务扣除；材料不足时不会修改装备或背包。

### 可以加入已有世界吗？

可以加载现有的 Minecraft 1.21.3 Fabric 世界和旧版 Magic 物品；本版本不修改世界生成。更新前仍建议备份，且不要在未备份的情况下回退到旧版。

### 强化装备会按阶级发光或照亮环境吗？

会按最高适用强化显示四阶轮廓光圈：I 蓝、II 紫、III 粉、IV 橙，同阶等级越高，光圈越宽、越强。背包、热栏和第一人称手持已实机验证；第三人称、掉落和展示框仍在兼容验收。原物品颜色与原版附魔 glint 保持不变。穿在身上的盔甲暂不显示这层轮廓，它也不是动态光源；头盔“照明”是独立功能。

### 在哪里下载最新版或反馈问题？

从 [GitHub Releases](https://github.com/zoyluoblue/Minecraft_Magic/releases/latest) 下载最新版；在 [GitHub Issues](https://github.com/zoyluoblue/Minecraft_Magic/issues) 提交可复现的问题、日志和建议。

## 开发与验证

<details>
<summary>构建、测试与技术验证</summary>

```bash
./gradlew clean build --no-daemon --stacktrace
./gradlew runGameTest --no-daemon --stacktrace
```

正式构建会执行 Java 编译、资源处理、JAR 打包、中英文 translation key 一致性检查，以及原创星环强化台和轮廓 Shader 资源门禁。当前 Dedicated Server 套件包含 37 项 GameTest，覆盖材料事务、持久化、水魂水下移动、战斗、远程武器快照、临时效果、射钉枪状态机与直线牵引，以及强化台/轮廓视觉规则。

</details>

## 下载、反馈与许可证

- [下载最新 Release](https://github.com/zoyluoblue/Minecraft_Magic/releases/latest)
- [查看构建状态](https://github.com/zoyluoblue/Minecraft_Magic/actions)
- [提交 Issue](https://github.com/zoyluoblue/Minecraft_Magic/issues)
- 开源许可证：[MIT](LICENSE)

Magic 是非官方 Minecraft Mod，与 Mojang Studios 或 Microsoft 无隶属、认可或合作关系。
