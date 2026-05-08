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
import net.minecraft.text.MutableText;
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
	private static boolean configured;
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
		addStackLines(entries, player, "mainhand", "主手", player.getEquippedStack(EquipmentSlot.MAINHAND));
		addStackLines(entries, player, "offhand", "副手", player.getEquippedStack(EquipmentSlot.OFFHAND));
		addStackLines(entries, player, "head", "头盔", player.getEquippedStack(EquipmentSlot.HEAD));
		addStackLines(entries, player, "chest", "胸甲", player.getEquippedStack(EquipmentSlot.CHEST));
		addStackLines(entries, player, "legs", "护腿", player.getEquippedStack(EquipmentSlot.LEGS));
		addStackLines(entries, player, "feet", "鞋子", player.getEquippedStack(EquipmentSlot.FEET));
		return entries;
	}

	public static boolean isEntryEnabled(DisplayEntry entry) {
		return !configured || enabledKeys.contains(entry.key());
	}

	public static void setEnabledKeys(Set<String> keys) {
		enabledKeys.clear();
		enabledKeys.addAll(keys);
		configured = true;
	}

	public static void clearEnabledKeys() {
		enabledKeys.clear();
		configured = true;
	}

	private static void addStackLines(List<DisplayEntry> entries, PlayerEntity player, String slotKey, String slotLabel, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		for (EnhancementType type : EnhancementSystem.getApplicableTypes(stack)) {
			EnhancementSystem.Level level = EnhancementSystem.getLevel(stack, type);
			if (!level.isEmpty()) {
				entries.add(new DisplayEntry(slotKey + ":" + type.id(), slotKey, slotLabel, Text.translatable(type.translationKey()), createLine(player, slotLabel, stack, type, level)));
			}
		}
	}

	private static Text createLine(PlayerEntity player, String slotLabel, ItemStack stack, EnhancementType type, EnhancementSystem.Level level) {
		MutableText line = Text.literal(slotLabel + " ").append(stack.getName()).append(Text.literal("："));
		line.append(Text.translatable(type.translationKey()));
		line.append(Text.literal(" " + level.display() + "（" + getValueText(player, stack, type) + "）"));
		return line;
	}

	private static String getValueText(PlayerEntity player, ItemStack stack, EnhancementType type) {
		return switch (type) {
			case WATER_SOUL, FIRE_SOUL -> "剩余 " + BootEnhancementEffects.getSoulRemainingSeconds(player, type) + "秒";
			default -> "数值 " + EnhancementSystem.formatDisplayValue(stack, type);
		};
	}

	public record DisplayEntry(String key, String slotKey, String slotLabel, Text selectionText, Text text) {
	}
}
