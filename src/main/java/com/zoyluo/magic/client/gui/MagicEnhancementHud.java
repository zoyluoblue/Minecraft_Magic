package com.zoyluo.magic.client.gui;

import com.zoyluo.magic.component.BootEnhancementEffects;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MagicEnhancementHud {
	private static final int X = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int BOTTOM_MARGIN = 58;
	private static final Set<String> enabledKeys = new LinkedHashSet<>();
	private static KeyBinding toggleKeyBinding;

	private MagicEnhancementHud() {
	}

	public static void register() {
		toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.magic.toggle_hud",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F9,
				"category.magic"
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKeyBinding.wasPressed()) {
				if (client.player != null) {
					client.setScreen(new MagicEnhancementSelectionScreen(client.currentScreen));
				}
			}
		});
		HudRenderCallback.EVENT.register(MagicEnhancementHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || client.options.hudHidden) {
			return;
		}

		List<DisplayEntry> entries = collectDisplayEntries(player).stream()
				.filter(MagicEnhancementHud::isEntryEnabled)
				.toList();
		if (entries.isEmpty()) {
			return;
		}

		TextRenderer textRenderer = client.textRenderer;
		int y = Math.max(6, client.getWindow().getScaledHeight() - BOTTOM_MARGIN - entries.size() * LINE_HEIGHT);
		for (DisplayEntry entry : entries) {
			int width = textRenderer.getWidth(entry.text());
			context.fill(X - 3, y - 1, X + width + 4, y + 9, 0x88000000);
			context.drawText(textRenderer, entry.text(), X, y, 0xFFE9C46A, true);
			y += LINE_HEIGHT;
		}
	}

	public static List<DisplayEntry> collectDisplayEntries(PlayerEntity player) {
		List<DisplayEntry> entries = new ArrayList<>();
		addStackLines(entries, player, "mainhand", "slot.magic.mainhand", player.getEquippedStack(EquipmentSlot.MAINHAND));
		addStackLines(entries, player, "offhand", "slot.magic.offhand", player.getEquippedStack(EquipmentSlot.OFFHAND));
		addStackLines(entries, player, "head", "slot.magic.head", player.getEquippedStack(EquipmentSlot.HEAD));
		addStackLines(entries, player, "chest", "slot.magic.chest", player.getEquippedStack(EquipmentSlot.CHEST));
		addStackLines(entries, player, "legs", "slot.magic.legs", player.getEquippedStack(EquipmentSlot.LEGS));
		addStackLines(entries, player, "feet", "slot.magic.feet", player.getEquippedStack(EquipmentSlot.FEET));
		return entries;
	}

	public static boolean isEntryEnabled(DisplayEntry entry) {
		return enabledKeys.contains(entry.key());
	}

	public static void setEnabledKeys(Set<String> keys) {
		enabledKeys.clear();
		enabledKeys.addAll(keys);
	}

	public static void clearEnabledKeys() {
		enabledKeys.clear();
	}

	private static void addStackLines(List<DisplayEntry> entries, PlayerEntity player, String slotKey, String slotTranslationKey, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		for (EnhancementType type : EnhancementSystem.getApplicableTypes(stack)) {
			EnhancementSystem.Level level = EnhancementSystem.getLevel(stack, type);
			if (!level.isEmpty()) {
				Text slotLabel = Text.translatable(slotTranslationKey);
				entries.add(new DisplayEntry(slotKey + ":" + type.id(), slotKey, slotLabel, Text.translatable(type.translationKey()), createLine(player, slotLabel, stack, type, level)));
			}
		}
	}

	private static Text createLine(PlayerEntity player, Text slotLabel, ItemStack stack, EnhancementType type, EnhancementSystem.Level level) {
		return Text.translatable(
				"hud.magic.enhancement_line",
				slotLabel,
				stack.getName(),
				Text.translatable(type.translationKey()),
				level.display(),
				getValueText(player, stack, type)
		);
	}

	private static Text getValueText(PlayerEntity player, ItemStack stack, EnhancementType type) {
		return switch (type) {
			case WATER_SOUL, FIRE_SOUL -> Text.translatable("value.magic.remaining_seconds", BootEnhancementEffects.getSoulRemainingSeconds(player, type));
			default -> Text.translatable("value.magic.numeric", EnhancementSystem.formatDisplayValueText(stack, type));
		};
	}

	public record DisplayEntry(String key, String slotKey, Text slotLabel, Text selectionText, Text text) {
	}
}
