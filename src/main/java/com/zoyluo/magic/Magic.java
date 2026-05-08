package com.zoyluo.magic;

import com.zoyluo.magic.block.StrengtheningTableBlock;
import com.zoyluo.magic.block.StrengtheningTableBlockEntity;
import com.zoyluo.magic.component.EnhancementEffects;
import com.zoyluo.magic.component.HelmetEnhancementEffects;
import com.zoyluo.magic.component.NailGunEffects;
import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class Magic implements ModInitializer {
	public static final String MOD_ID = "magic";
	private static final Identifier STRENGTHENING_TABLE_ID = id("strengthening_table");
	private static final RegistryKey<Block> STRENGTHENING_TABLE_BLOCK_KEY = RegistryKey.of(RegistryKeys.BLOCK, STRENGTHENING_TABLE_ID);
	private static final RegistryKey<Item> STRENGTHENING_TABLE_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, STRENGTHENING_TABLE_ID);

	public static final StrengtheningTableBlock STRENGTHENING_TABLE = Registry.register(
			Registries.BLOCK,
			STRENGTHENING_TABLE_ID,
			new StrengtheningTableBlock(AbstractBlock.Settings.copy(Blocks.SMITHING_TABLE)
					.registryKey(STRENGTHENING_TABLE_BLOCK_KEY)
					.mapColor(MapColor.DEEPSLATE_GRAY)
					.sounds(BlockSoundGroup.METAL)
					.strength(4.0F, 8.0F))
	);

	public static final Item STRENGTHENING_TABLE_ITEM = Registry.register(
			Registries.ITEM,
			STRENGTHENING_TABLE_ID,
			new BlockItem(STRENGTHENING_TABLE, new Item.Settings().registryKey(STRENGTHENING_TABLE_ITEM_KEY))
	);

	public static final BlockEntityType<StrengtheningTableBlockEntity> STRENGTHENING_TABLE_BLOCK_ENTITY =
			Registry.register(
					Registries.BLOCK_ENTITY_TYPE,
					STRENGTHENING_TABLE_ID,
					FabricBlockEntityTypeBuilder.create(StrengtheningTableBlockEntity::new, STRENGTHENING_TABLE).build()
			);

	public static final ScreenHandlerType<StrengtheningTableScreenHandler> STRENGTHENING_TABLE_SCREEN_HANDLER =
			Registry.register(
					Registries.SCREEN_HANDLER,
					STRENGTHENING_TABLE_ID,
					new ScreenHandlerType<>(StrengtheningTableScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
			);

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(STRENGTHENING_TABLE_ITEM));
		EnhancementEffects.register();
		HelmetEnhancementEffects.register();
		NailGunEffects.register();
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
