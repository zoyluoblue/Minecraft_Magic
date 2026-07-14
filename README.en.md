# Magic 2.0.0

English | [简体中文](readme.md)

Magic is a Fabric enhancement-system mod for Minecraft 1.21.3. It adds a survival-craftable Strengthening Table and twelve independent weapon, tool, and armor enhancements.

## Strengthening Table

Obtain it from the Functional Blocks creative tab, run `/give @p magic:strengthening_table`, or craft it with five iron ingots, one emerald, two amethyst shards, and one smithing table.

The table has one enhanceable-item slot and one selected-material slot. Each enhancement progresses from `I-1` to `IV-10`. The four major tiers use iron ingots, gold ingots, diamonds, and emeralds; each minor-tier cost doubles from `1` to `512`. Survival mode consumes materials from the selected slot and player inventory, while creative mode does not.

## Enhancements

- Weapons/tools: Critical Hit, Petrify, Instant Kill, Power, Explosion.
- Leggings: Speed, Jump Height.
- Boots: Water Soul, Fire Soul.
- Helmet: Illumination, Ore Seeker.
- Chestplate: Nail Gun.

Press `F9` to choose which enhancement values and ore targets appear in the HUD. Ore Seeker scans loaded chunks only. With Nail Gun on a chestplate, press `Alt + 1` to fire/cancel, `Space` to pull, or `Esc` to cancel.

## Language

English and Simplified Chinese are included. Use Minecraft's native **Options → Language** screen. Names, screens, buttons, units, HUD labels, tooltips, and server messages all follow the active language.

## Build

~~~bash
./gradlew clean build --no-daemon --stacktrace
~~~

The build enforces `en_us`/`zh_cn` key parity. Stable Registry IDs, item-data schema, protocol order, values, and regression requirements are documented in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Demo

[Watch the demo video](https://www.youtube.com/watch?v=J3A4pPPDw4k)
