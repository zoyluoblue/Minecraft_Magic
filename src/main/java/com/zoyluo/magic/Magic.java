package com.zoyluo.magic;

import com.zoyluo.magic.block.StrengtheningTableBlock;
import com.zoyluo.magic.block.StrengtheningTableBlockEntity;
import com.zoyluo.magic.component.EnhancementEffects;
import com.zoyluo.magic.component.HelmetEnhancementEffects;
import com.zoyluo.magic.component.NailGunEffects;
import com.zoyluo.magic.network.MagicNetworking;
import com.zoyluo.magic.registry.ModBlockEntities;
import com.zoyluo.magic.registry.ModBlocks;
import com.zoyluo.magic.registry.ModScreenHandlers;
import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class Magic implements ModInitializer {
	public static final String MOD_ID = "magic";

	// Compatibility aliases retained for existing integrations and screen handlers.
	public static final StrengtheningTableBlock STRENGTHENING_TABLE = ModBlocks.STRENGTHENING_TABLE;
	public static final Item STRENGTHENING_TABLE_ITEM = ModBlocks.STRENGTHENING_TABLE_ITEM;
	public static final BlockEntityType<StrengtheningTableBlockEntity> STRENGTHENING_TABLE_BLOCK_ENTITY = ModBlockEntities.STRENGTHENING_TABLE;
	public static final ScreenHandlerType<StrengtheningTableScreenHandler> STRENGTHENING_TABLE_SCREEN_HANDLER = ModScreenHandlers.STRENGTHENING_TABLE;

	@Override
	public void onInitialize() {
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModScreenHandlers.initialize();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(STRENGTHENING_TABLE_ITEM));
		MagicNetworking.registerPayloadTypes();
		EnhancementEffects.register();
		HelmetEnhancementEffects.register();
		NailGunEffects.register();
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
