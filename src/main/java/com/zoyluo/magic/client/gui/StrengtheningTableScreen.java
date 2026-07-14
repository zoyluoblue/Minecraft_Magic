package com.zoyluo.magic.client.gui;

import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class StrengtheningTableScreen extends HandledScreen<StrengtheningTableScreenHandler> {
	private final Map<EnhancementType, ButtonWidget> enhancementButtons = new EnumMap<>(EnhancementType.class);

	public StrengtheningTableScreen(StrengtheningTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 214;
		playerInventoryTitleY = 120;
	}

	@Override
	protected void init() {
		super.init();
		enhancementButtons.clear();
		for (EnhancementType type : EnhancementType.values()) {
			ButtonWidget button = ButtonWidget.builder(Text.translatable(type.translationKey()), clicked -> {
				if (client != null && client.interactionManager != null) {
					client.interactionManager.clickButton(handler.syncId, type.buttonId());
				}
			}).dimensions(x + 8, y + 48, 31, 18).build();
			enhancementButtons.put(type, button);
			addDrawableChild(button);
		}
		layoutEnhancementButtons();
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		layoutEnhancementButtons();
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = x;
		int top = y;
		context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF2C3037);
		context.fill(left + 4, top + 4, left + backgroundWidth - 4, top + 124, 0xFF39404A);
		context.fill(left + 4, top + 126, left + backgroundWidth - 4, top + backgroundHeight - 4, 0xFF242830);
		drawSlot(context, left + 43, top + 21);
		drawSlot(context, left + 115, top + 21);
		context.fill(left + 67, top + 26, left + 109, top + 28, 0xFF9AA8B5);
		context.fill(left + 107, top + 24, left + 111, top + 30, 0xFF9AA8B5);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, titleX, titleY, 0xF2F5F7, false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xB8C1CC, false);

		ItemStack tool = handler.getToolStack();
		if (!EnhancementSystem.isUpgradeableTool(tool)) {
			context.drawText(textRenderer, Text.translatable("gui.magic.place_tool").formatted(Formatting.GRAY), 8, 74, 0xF2F5F7, false);
			return;
		}

		int lineY = 70;
		for (EnhancementType type : EnhancementSystem.getApplicableTypes(tool)) {
			EnhancementSystem.Level level = EnhancementSystem.getLevel(tool, type);
			Text levelText = level.isEmpty()
					? Text.translatable("gui.magic.enhancement_empty", Text.translatable(type.translationKey())).formatted(Formatting.GRAY)
					: Text.translatable(
							"gui.magic.enhancement_status",
							Text.translatable(type.translationKey()),
							level.display(),
							EnhancementSystem.formatDisplayValueText(tool, type)
					).formatted(Formatting.GOLD);
			context.drawText(textRenderer, levelText, 8, lineY, 0xF2F5F7, false);
			lineY += 10;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		renderEnhancementTooltip(context, mouseX, mouseY);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	private void renderEnhancementTooltip(DrawContext context, int mouseX, int mouseY) {
		for (EnhancementType type : EnhancementType.values()) {
			ButtonWidget button = enhancementButtons.get(type);
			if (button == null || !button.visible || !button.isHovered()) {
				continue;
			}

			EnhancementSystem.UpgradeRequirement requirement = handler.getNextRequirement(type);
			Text tooltip;
			if (requirement == null) {
				tooltip = EnhancementSystem.isUpgradeableTool(handler.getToolStack())
						? Text.translatable("gui.magic.max_level")
						: Text.translatable("gui.magic.place_tool");
			} else {
				tooltip = Text.translatable(
						"gui.magic.next_cost",
						Text.translatable(type.translationKey()),
						requirement.nextLevel().display(),
						requirement.count(),
						getMaterialName(requirement.material())
				);
			}
			context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
			return;
		}
	}

	private void layoutEnhancementButtons() {
		List<EnhancementType> visibleTypes = EnhancementSystem.getApplicableTypes(handler.getToolStack());
		int buttonX = x + 8;
		for (EnhancementType type : EnhancementType.values()) {
			ButtonWidget button = enhancementButtons.get(type);
			if (button == null) {
				continue;
			}

			int visibleIndex = visibleTypes.indexOf(type);
			button.visible = visibleIndex >= 0;
			button.active = button.visible && handler.canUpgrade(type);
			if (button.visible) {
				button.setX(buttonX + visibleIndex * 33);
				button.setY(y + 48);
			}
		}
	}

	private void drawSlot(DrawContext context, int left, int top) {
		context.fill(left, top, left + 18, top + 18, 0xFF11141A);
		context.fill(left + 1, top + 1, left + 17, top + 17, 0xFF596270);
		context.fill(left + 2, top + 2, left + 16, top + 16, 0xFF20242B);
	}

	private Text getMaterialName(Item material) {
		return material.getName();
	}
}
