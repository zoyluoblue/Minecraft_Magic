package com.zoyluo.magic.registry;

import com.zoyluo.magic.Magic;
import com.zoyluo.magic.block.StrengtheningTableBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Identifier STRENGTHENING_TABLE_ID = Magic.id("strengthening_table");
	private static final RegistryKey<Block> BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, STRENGTHENING_TABLE_ID);
	private static final RegistryKey<Item> ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, STRENGTHENING_TABLE_ID);

	public static final StrengtheningTableBlock STRENGTHENING_TABLE = Registry.register(
			Registries.BLOCK,
			STRENGTHENING_TABLE_ID,
			new StrengtheningTableBlock(AbstractBlock.Settings.copy(Blocks.SMITHING_TABLE)
					.registryKey(BLOCK_KEY)
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.sounds(BlockSoundGroup.METAL)
					.strength(4.0F, 8.0F))
	);

	public static final Item STRENGTHENING_TABLE_ITEM = Registry.register(
			Registries.ITEM,
			STRENGTHENING_TABLE_ID,
			new BlockItem(STRENGTHENING_TABLE, new Item.Settings().registryKey(ITEM_KEY))
	);

	private ModBlocks() {
	}

	public static void initialize() {
		// Loading this class performs the static block and item registrations.
	}
}
