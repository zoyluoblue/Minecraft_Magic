package com.zoyluo.magic.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MagicEnhancementSelectionScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int TOP = 38;
	private static final int BOTTOM_PADDING = 54;
	private static final int LEFT_PADDING = 24;
	private static final int TOGGLE_WIDTH = 54;
	private static final int TOGGLE_HEIGHT = 16;

	private final Screen parent;
	private final List<EntryState> entries = new ArrayList<>();
	private int scrollOffset;

	public MagicEnhancementSelectionScreen(Screen parent) {
		super(Text.literal("强化数值显示"));
		this.parent = parent;
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			Map<String, SlotGroup> groups = new LinkedHashMap<>();
			for (MagicEnhancementHud.DisplayEntry entry : MagicEnhancementHud.collectDisplayEntries(player)) {
				String slotKey = slotKeyOf(entry.key());
				groups.computeIfAbsent(slotKey, ignored -> new SlotGroup(slotKey, slotLabel(slotKey))).entries.add(entry);
			}
			List<HelmetOreLocator.OreSelectionEntry> oreEntries = HelmetOreLocator.collectSelectionEntries(player);
			for (SlotGroup group : groups.values()) {
				entries.add(EntryState.group(group.key, Text.literal(group.label), group.hasEnabledEntry()));
				for (MagicEnhancementHud.DisplayEntry entry : group.entries) {
					entries.add(EntryState.display(entry, group.key));
					if (entry.key().endsWith(":ore_seeker")) {
						for (HelmetOreLocator.OreSelectionEntry oreEntry : oreEntries) {
							entries.add(EntryState.ore(oreEntry, group.key, entry.key()));
						}
					}
				}
			}
		}
	}

	@Override
	protected void init() {
		int buttonY = height - 32;
		addDrawableChild(ButtonWidget.builder(Text.literal("确定"), button -> confirm())
				.dimensions(width / 2 - 108, buttonY, 96, 20)
				.build());
		addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> cancel())
				.dimensions(width / 2 + 12, buttonY, 96, 20)
				.build());
		clampScrollOffset();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xCC000000);
		drawPanel(context);
		context.drawText(textRenderer, title, (width - textRenderer.getWidth(title)) / 2, 14, 0xFFFFFFFF, true);

		if (entries.isEmpty()) {
			Text empty = Text.literal("当前没有可显示的强化数值");
			context.drawText(textRenderer, empty, (width - textRenderer.getWidth(empty)) / 2, height / 2 - 10, 0xFFB8C1CC, true);
		} else {
			renderRows(context);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int index = rowIndexAt(mouseX, mouseY);
			if (index >= 0) {
				toggleEntry(index);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (entries.size() <= visibleRows()) {
			return false;
		}
		scrollOffset -= (int) Math.signum(verticalAmount);
		clampScrollOffset();
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private void renderRows(DrawContext context) {
		int left = LEFT_PADDING;
		int right = width - LEFT_PADDING;
		int visibleRows = visibleRows();
		int end = Math.min(entries.size(), scrollOffset + visibleRows);
		for (int i = scrollOffset; i < end; i++) {
			EntryState state = entries.get(i);
			int y = TOP + (i - scrollOffset) * ROW_HEIGHT;
			context.fill(left, y, right, y + ROW_HEIGHT - 2, 0xAA11141A);
			context.drawText(textRenderer, state.text, left + 8 + state.indent * 18, y + 7, rowTextColor(state), true);
			drawToggle(context, right - TOGGLE_WIDTH - 6, y + 3, state.enabled);
		}

		if (entries.size() > visibleRows) {
			Text scrollText = Text.literal((scrollOffset + 1) + "-" + end + " / " + entries.size());
			context.drawText(textRenderer, scrollText, right - textRenderer.getWidth(scrollText), TOP - 13, 0xFFB8C1CC, true);
		}
	}

	private void drawPanel(DrawContext context) {
		int left = Math.max(12, LEFT_PADDING - 10);
		int top = 30;
		int right = width - left;
		int bottom = height - 42;
		context.fill(left, top, right, bottom, 0xFF20242B);
		context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF2C3037);
		context.fill(left + 4, top + 24, right - 4, bottom - 4, 0xFF171A20);
	}

	private void drawToggle(DrawContext context, int x, int y, boolean enabled) {
		int color = enabled ? 0xFF2E7D32 : 0xFF4B5563;
		context.fill(x, y, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT, color);
		context.fill(x + 1, y + 1, x + TOGGLE_WIDTH - 1, y + TOGGLE_HEIGHT - 1, enabled ? 0xFF43A047 : 0xFF6B7280);
		Text text = Text.literal(enabled ? "开启" : "关闭");
		context.drawText(textRenderer, text, x + (TOGGLE_WIDTH - textRenderer.getWidth(text)) / 2, y + 4, 0xFFFFFFFF, true);
	}

	private int rowIndexAt(double mouseX, double mouseY) {
		int left = width - LEFT_PADDING - TOGGLE_WIDTH - 6;
		int right = width - LEFT_PADDING - 6;
		if (mouseX < left || mouseX > right || mouseY < TOP) {
			return -1;
		}
		int row = ((int) mouseY - TOP) / ROW_HEIGHT;
		if (row < 0 || row >= visibleRows()) {
			return -1;
		}
		int index = scrollOffset + row;
		return index < entries.size() ? index : -1;
	}

	private int visibleRows() {
		return Math.max(1, (height - TOP - BOTTOM_PADDING) / ROW_HEIGHT);
	}

	private void clampScrollOffset() {
		int max = Math.max(0, entries.size() - visibleRows());
		scrollOffset = Math.max(0, Math.min(scrollOffset, max));
	}

	private void toggleEntry(int index) {
		EntryState state = entries.get(index);
		state.enabled = !state.enabled;
		switch (state.kind) {
			case GROUP -> setGroupChildrenEnabled(state.key, state.enabled);
			case DISPLAY -> {
				if (state.enabled) {
					setGroupEnabled(state.groupKey, true);
				} else {
					setChildOreTargetsEnabled(state.key, false);
					syncGroupEnabled(state.groupKey);
				}
			}
			case ORE -> {
				if (state.enabled) {
					setGroupEnabled(state.groupKey, true);
					setDisplayEntryEnabled(state.parentKey, true);
				} else {
					syncGroupEnabled(state.groupKey);
				}
			}
		}
	}

	private void setGroupChildrenEnabled(String groupKey, boolean enabled) {
		for (EntryState entry : entries) {
			if (entry.kind != EntryKind.GROUP && entry.groupKey.equals(groupKey)) {
				entry.enabled = enabled;
			}
		}
	}

	private void setGroupEnabled(String groupKey, boolean enabled) {
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.GROUP && entry.key.equals(groupKey)) {
				entry.enabled = enabled;
				return;
			}
		}
	}

	private void setDisplayEntryEnabled(String key, boolean enabled) {
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.DISPLAY && entry.key.equals(key)) {
				entry.enabled = enabled;
				return;
			}
		}
	}

	private void setChildOreTargetsEnabled(String parentKey, boolean enabled) {
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.ORE && entry.parentKey.equals(parentKey)) {
				entry.enabled = enabled;
			}
		}
	}

	private void syncGroupEnabled(String groupKey) {
		boolean anyEnabled = false;
		for (EntryState entry : entries) {
			if (entry.kind != EntryKind.GROUP && entry.groupKey.equals(groupKey) && entry.enabled) {
				anyEnabled = true;
				break;
			}
		}
		setGroupEnabled(groupKey, anyEnabled);
	}

	private void confirm() {
		Set<String> selectedKeys = new LinkedHashSet<>();
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.DISPLAY && entry.enabled && isGroupEnabled(entry.groupKey)) {
				selectedKeys.add(entry.key);
			}
		}
		MagicEnhancementHud.setEnabledKeys(selectedKeys);
		Set<String> selectedOreTargets = new LinkedHashSet<>();
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.ORE && entry.enabled && isGroupEnabled(entry.groupKey) && isDisplayEntryEnabled(entry.parentKey)) {
				selectedOreTargets.add(entry.key);
			}
		}
		HelmetOreLocator.setSelectedOreTargets(selectedOreTargets);
		close();
	}

	private void cancel() {
		MagicEnhancementHud.clearEnabledKeys();
		HelmetOreLocator.clearSelectedOreTargets();
		close();
	}

	private enum EntryKind {
		GROUP,
		DISPLAY,
		ORE
	}

	private static final class EntryState {
		private final EntryKind kind;
		private final String key;
		private final Text text;
		private final String groupKey;
		private final String parentKey;
		private final int indent;
		private boolean enabled;

		private EntryState(EntryKind kind, String key, Text text, String groupKey, String parentKey, int indent, boolean enabled) {
			this.kind = kind;
			this.key = key;
			this.text = text;
			this.groupKey = groupKey;
			this.parentKey = parentKey;
			this.indent = indent;
			this.enabled = enabled;
		}

		private static EntryState group(String key, Text text, boolean enabled) {
			return new EntryState(EntryKind.GROUP, key, text, key, "", 0, enabled);
		}

		private static EntryState display(MagicEnhancementHud.DisplayEntry entry, String groupKey) {
			return new EntryState(EntryKind.DISPLAY, entry.key(), entry.selectionText(), groupKey, "", 1, MagicEnhancementHud.isEntryEnabled(entry));
		}

		private static EntryState ore(HelmetOreLocator.OreSelectionEntry entry, String groupKey, String parentKey) {
			return new EntryState(EntryKind.ORE, entry.key(), entry.text(), groupKey, parentKey, 2, entry.enabled());
		}
	}

	private int rowTextColor(EntryState state) {
		return switch (state.kind) {
			case GROUP -> 0xFFFFFFFF;
			case DISPLAY -> 0xFFE9C46A;
			case ORE -> 0xFF5EEAD4;
		};
	}

	private boolean isGroupEnabled(String groupKey) {
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.GROUP && entry.key.equals(groupKey)) {
				return entry.enabled;
			}
		}
		return true;
	}

	private boolean isDisplayEntryEnabled(String key) {
		for (EntryState entry : entries) {
			if (entry.kind == EntryKind.DISPLAY && entry.key.equals(key)) {
				return entry.enabled;
			}
		}
		return true;
	}

	private static String slotKeyOf(String key) {
		int separator = key.indexOf(':');
		return separator < 0 ? key : key.substring(0, separator);
	}

	private static String slotLabel(String key) {
		return switch (key) {
			case "mainhand" -> "主手武器";
			case "offhand" -> "副手";
			case "head" -> "头盔";
			case "chest" -> "胸甲";
			case "legs" -> "护腿";
			case "feet" -> "鞋子";
			default -> key;
		};
	}

	private static final class SlotGroup {
		private final String key;
		private final String label;
		private final List<MagicEnhancementHud.DisplayEntry> entries = new ArrayList<>();

		private SlotGroup(String key, String label) {
			this.key = key;
			this.label = label;
		}

		private boolean hasEnabledEntry() {
			for (MagicEnhancementHud.DisplayEntry entry : entries) {
				if (MagicEnhancementHud.isEntryEnabled(entry)) {
					return true;
				}
			}
			return false;
		}
	}
}
