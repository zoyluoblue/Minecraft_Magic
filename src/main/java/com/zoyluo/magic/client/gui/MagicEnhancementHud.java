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
import java.util.List;

public final class MagicEnhancementHud {
	private static final int X = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int BOTTOM_MARGIN = 58;
	private static boolean visible = true;
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
				visible = !visible;
			}
		});
		HudRenderCallback.EVENT.register(MagicEnhancementHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (!visible || player == null || client.options.hudHidden) {
			return;
		}

		List<Text> lines = collectLines(player);
		if (lines.isEmpty()) {
			return;
		}

		TextRenderer textRenderer = client.textRenderer;
		int y = Math.max(6, client.getWindow().getScaledHeight() - BOTTOM_MARGIN - lines.size() * LINE_HEIGHT);
		for (Text line : lines) {
			int width = textRenderer.getWidth(line);
			context.fill(X - 3, y - 1, X + width + 4, y + 9, 0x88000000);
			context.drawText(textRenderer, line, X, y, 0xFFE9C46A, true);
			y += LINE_HEIGHT;
		}
	}

	private static List<Text> collectLines(PlayerEntity player) {
		List<Text> lines = new ArrayList<>();
		addStackLines(lines, player, "主手", player.getEquippedStack(EquipmentSlot.MAINHAND));
		addStackLines(lines, player, "副手", player.getEquippedStack(EquipmentSlot.OFFHAND));
		addStackLines(lines, player, "头盔", player.getEquippedStack(EquipmentSlot.HEAD));
		addStackLines(lines, player, "胸甲", player.getEquippedStack(EquipmentSlot.CHEST));
		addStackLines(lines, player, "护腿", player.getEquippedStack(EquipmentSlot.LEGS));
		addStackLines(lines, player, "鞋子", player.getEquippedStack(EquipmentSlot.FEET));
		return lines;
	}

	private static void addStackLines(List<Text> lines, PlayerEntity player, String slotLabel, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		for (EnhancementType type : EnhancementSystem.getApplicableTypes(stack)) {
			EnhancementSystem.Level level = EnhancementSystem.getLevel(stack, type);
			if (!level.isEmpty()) {
				lines.add(createLine(player, slotLabel, stack, type, level));
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
}
