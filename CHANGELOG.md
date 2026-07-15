# Changelog

## 2.2.0 — 2026-07-15

### 简体中文

#### 新功能

- 强化物品按最高适用强化显示独立轮廓光：I 蓝、II 紫、III 粉、IV 橙，同阶 1–10 级逐步增加轮廓宽度与强度。
- 轮廓只覆盖物品剪影外侧，不修改原贴图、本体颜色、明暗或原版附魔 glint，也不会照亮周围环境。
- 背包、热栏和第一人称手持已完成实机验证；第三人称、掉落物和展示框使用同一物品渲染路径，仍在兼容验收。

#### 质量与兼容性

- 新增 Alpha-only 轮廓 Shader、发布资源门禁和四阶十级视觉规则回归，Dedicated Server GameTest 增至 35 项。
- 不涉及数值、配方、Registry ID、存档结构或网络协议变化，可直接从 `2.1.1` 升级。
- 穿戴中的盔甲暂不显示轮廓；多人游戏仍需要客户端与服务端安装相同版本的 Magic。

需要 Minecraft `1.21.3`、Java `21+`、Fabric Loader `0.18.4+` 和 Fabric API `0.114.1+1.21.3`。

### English

#### New

- Enhanced items now receive a separate silhouette halo based on the strongest applicable enhancement: Tier I blue, Tier II purple, Tier III pink, and Tier IV orange. Width and intensity increase from level 1 to 10 within each tier.
- The halo covers only the outside of the item silhouette. It does not alter the original texture, item color, lighting, or vanilla enchantment glint, and it does not illuminate the environment.
- Inventories, hotbars, and first-person hands have been verified in a real client. Third-person hands, dropped items, and item frames use the same item-rendering path and remain under compatibility validation.

#### Quality and compatibility

- Added an alpha-only outline shader, release resource gates, four-tier visual regression coverage, and a 35-test Dedicated Server suite.
- No balance, recipe, registry ID, save schema, or network protocol changes. Worlds can upgrade directly from `2.1.1`.
- Worn armor does not yet receive the outline. Multiplayer still requires matching Magic versions on client and server.

Requires Minecraft `1.21.3`, Java `21+`, Fabric Loader `0.18.4+`, and Fabric API `0.114.1+1.21.3`.

## 2.1.1 — 2026-07-15

### 简体中文

#### 修复

- 修复强化台进入客户端渲染范围时可能立即闪退的问题。星环与成功能量特效现在按正确的 RenderLayer 缓冲区生命周期顺序绘制。

#### 兼容性

- 不涉及数值、配方、Registry ID、存档结构或网络协议变化，可直接从 `2.1.0` 升级。
- 多人游戏仍需要客户端与服务端安装相同版本的 Magic。

需要 Minecraft `1.21.3`、Java `21+`、Fabric Loader `0.18.4+` 和 Fabric API `0.114.1+1.21.3`。

### English

#### Fixed

- Fixed an immediate client crash when a Strengthening Table entered render range. Astral rings and success-energy effects now render in the correct RenderLayer buffer lifecycle order.

#### Compatibility

- No balance, recipe, registry ID, save schema, or network protocol changes. Worlds can upgrade directly from `2.1.0`.
- Multiplayer still requires matching Magic versions on client and server.

Requires Minecraft `1.21.3`, Java `21+`, Fabric Loader `0.18.4+`, and Fabric API `0.114.1+1.21.3`.

## 2.1.0 — 2026-07-15

### 简体中文

- 新增原创星环强化台模型、贴图、三重动态星环、槽位物品展示与强化成功反馈。
- 完成 12 种武器、工具与护甲强化的四阶 40 级长期成长说明。
- 强化近战、远程武器快照、水魂/火魂持久化和射钉枪的多人服务端权威结算。
- 加入玩家自行勾选的 HUD 与探矿目标，以及完整简体中文/英文切换。
- 新增 34 项 Dedicated Server GameTest、双语 key 检查和原创视觉资源门禁。
- 重组 GitHub README，并加入五张玩法、合成、材料、武器与护甲宣传教学海报。

需要 Minecraft `1.21.3`、Java `21+`、Fabric Loader `0.18.4+` 和 Fabric API `0.114.1+1.21.3`。多人游戏需要客户端与服务端安装相同版本的 Magic。

### English

- Added the original Astral Strengthening Table model and textures, three animated rings, displayed slot items, and synchronized success feedback.
- Documented the full four-tier, 40-level progression for 12 weapon, tool, and armor enhancements.
- Hardened server-authoritative multiplayer combat, ranged weapon snapshots, Water/Fire Soul persistence, and the Nail Gun state machine.
- Added player-selected HUD and ore targets with complete English and Simplified Chinese language switching.
- Added 34 Dedicated Server GameTests, translation-key parity checks, and an original-visual-resource verification gate.
- Reorganized the GitHub README with five promotional teaching posters for gameplay, crafting, materials, weapons, and armor.

Requires Minecraft `1.21.3`, Java `21+`, Fabric Loader `0.18.4+`, and Fabric API `0.114.1+1.21.3`. Multiplayer requires matching Magic versions on client and server.
