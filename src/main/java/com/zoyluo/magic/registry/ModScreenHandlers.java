package com.zoyluo.magic.registry;

import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<StrengtheningTableScreenHandler> STRENGTHENING_TABLE = Registry.register(
			Registries.SCREEN_HANDLER,
			ModBlocks.STRENGTHENING_TABLE_ID,
			new ScreenHandlerType<>(StrengtheningTableScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
	);

	private ModScreenHandlers() {
	}

	public static void initialize() {
		// Loading this class performs the static screen-handler registration.
	}
}
