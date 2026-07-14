package com.zoyluo.magic.registry;

import com.zoyluo.magic.block.StrengtheningTableBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
	public static final BlockEntityType<StrengtheningTableBlockEntity> STRENGTHENING_TABLE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			ModBlocks.STRENGTHENING_TABLE_ID,
			FabricBlockEntityTypeBuilder.create(StrengtheningTableBlockEntity::new, ModBlocks.STRENGTHENING_TABLE).build()
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
		// Loading this class performs the static block-entity registration.
	}
}
