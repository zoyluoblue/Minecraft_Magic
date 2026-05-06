package com.zoyluo.magic;

import com.zoyluo.magic.client.gui.StrengtheningTableScreen;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MagicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(Magic.STRENGTHENING_TABLE_SCREEN_HANDLER, StrengtheningTableScreen::new);

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			for (EnhancementType enhancementType : EnhancementType.values()) {
				EnhancementSystem.Level level = EnhancementSystem.getLevel(stack, enhancementType);
				if (!level.isEmpty()) {
					lines.add(Text.translatable(
							"tooltip.magic.enhancement",
							Text.translatable(enhancementType.translationKey()),
							level.display(),
							EnhancementSystem.formatPercent(EnhancementSystem.getDisplayValue(stack, enhancementType))
					).formatted(Formatting.GOLD));
				}
			}
		});
	}
}
