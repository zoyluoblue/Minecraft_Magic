package com.zoyluo.magic.component;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EnhancementSystem {
	private static final String ROOT_KEY = "MagicEnhancements";
	private static final String MAJOR_KEY = "Major";
	private static final String MINOR_KEY = "Minor";
	private static final String LEGACY_CRIT_MAJOR_KEY = "MagicCritMajor";
	private static final String LEGACY_CRIT_MINOR_KEY = "MagicCritMinor";
	private static final EnhancementType[] ENHANCEMENT_TYPES = EnhancementType.values();
	public static final int MAX_MAJOR = 4;
	public static final int MAX_MINOR = 10;

	private EnhancementSystem() {
	}

	public static boolean isUpgradeableTool(ItemStack stack) {
		return isUpgradeableWeaponOrTool(stack) || isUpgradeableBoots(stack) || isUpgradeableHelmet(stack) || isUpgradeableLeggings(stack) || isUpgradeableChestplate(stack);
	}

	public static boolean isUpgradeableBoots(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
			return false;
		}

		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.FEET;
	}

	public static boolean isUpgradeableHelmet(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
			return false;
		}

		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.HEAD;
	}

	public static boolean isUpgradeableLeggings(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
			return false;
		}

		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.LEGS;
	}

	public static boolean isUpgradeableChestplate(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)) {
			return false;
		}

		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
	}

	public static List<EnhancementType> getApplicableTypes(ItemStack stack) {
		List<EnhancementType> types = new ArrayList<>();
		for (EnhancementType type : ENHANCEMENT_TYPES) {
			if (isApplicable(stack, type)) {
				types.add(type);
			}
		}
		return types;
	}

	public static boolean isApplicable(ItemStack stack, EnhancementType type) {
		if (isUpgradeableBoots(stack)) {
			return type.isBootsEnhancement();
		}
		if (isUpgradeableHelmet(stack)) {
			return type.isHelmetEnhancement();
		}
		if (isUpgradeableLeggings(stack)) {
			return type.isLegsEnhancement();
		}
		if (isUpgradeableChestplate(stack)) {
			return type.isChestEnhancement();
		}
		return isUpgradeableWeaponOrTool(stack) && type.isWeaponEnhancement();
	}

	private static boolean isUpgradeableWeaponOrTool(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		Item item = stack.getItem();
		if (item instanceof ArmorItem || item == Items.ELYTRA) {
			return false;
		}

		return item instanceof SwordItem
				|| item instanceof MiningToolItem
				|| item instanceof BowItem
				|| item instanceof CrossbowItem
				|| item instanceof TridentItem
				|| stack.isDamageable();
	}

	public static Level getLevel(ItemStack stack, EnhancementType type) {
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (component == null) {
			return Level.EMPTY;
		}

		NbtCompound nbt = component.copyNbt();
		if (nbt.contains(ROOT_KEY, 10)) {
			NbtCompound enhancements = nbt.getCompound(ROOT_KEY);
			if (enhancements.contains(type.id(), 10)) {
				return readLevel(enhancements.getCompound(type.id()));
			}
		}

		if (type == EnhancementType.CRIT) {
			return readLegacyCritLevel(nbt);
		}
		return Level.EMPTY;
	}

	/**
	 * Returns the highest stored enhancement that is actually applicable to this stack.
	 * The custom data component is copied once so client renderers can query the visual
	 * tier without repeating the NBT copy for every enhancement type.
	 */
	public static Level getHighestApplicableLevel(ItemStack stack) {
		if (stack.isEmpty()) {
			return Level.EMPTY;
		}

		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (component == null) {
			return Level.EMPTY;
		}

		NbtCompound nbt = component.copyNbt();
		NbtCompound enhancements = nbt.contains(ROOT_KEY, 10) ? nbt.getCompound(ROOT_KEY) : null;
		Level highest = Level.EMPTY;
		for (EnhancementType type : ENHANCEMENT_TYPES) {
			if (!isApplicable(stack, type)) {
				continue;
			}

			Level candidate = Level.EMPTY;
			boolean hasCurrentValue = enhancements != null && enhancements.contains(type.id(), 10);
			if (hasCurrentValue) {
				candidate = readLevel(enhancements.getCompound(type.id()));
			} else if (type == EnhancementType.CRIT) {
				candidate = readLegacyCritLevel(nbt);
			}

			if (candidate.totalLevels() > highest.totalLevels()) {
				highest = candidate;
			}
		}
		return highest;
	}

	public static void setLevel(ItemStack stack, EnhancementType type, Level level) {
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
		NbtCompound enhancements = nbt.contains(ROOT_KEY, 10) ? nbt.getCompound(ROOT_KEY) : new NbtCompound();
		NbtCompound typeNbt = new NbtCompound();
		typeNbt.putInt(MAJOR_KEY, level.major());
		typeNbt.putInt(MINOR_KEY, level.minor());
		enhancements.put(type.id(), typeNbt);
		nbt.put(ROOT_KEY, enhancements);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Nullable
	public static UpgradeRequirement getNextRequirement(ItemStack stack, EnhancementType type) {
		if (!isApplicable(stack, type)) {
			return null;
		}

		Level current = getLevel(stack, type);
		if (current.isMaxed()) {
			return null;
		}

		Level next = current.next();
		return new UpgradeRequirement(next, materialForMajor(next.major()), materialCost(next.minor()));
	}

	public static double getWeightedValue(ItemStack stack, EnhancementType type) {
		return getWeightedValue(getLevel(stack, type));
	}

	public static double getWeightedValue(Level level) {
		double value = 0.0D;
		for (int major = 1; major <= MAX_MAJOR; major++) {
			int levelsInMajor;
			if (level.major() > major) {
				levelsInMajor = MAX_MINOR;
			} else if (level.major() == major) {
				levelsInMajor = level.minor();
			} else {
				levelsInMajor = 0;
			}
			value += levelsInMajor * weightedValuePerMinor(major);
		}
		return value;
	}

	public static double getFlatChance(ItemStack stack, EnhancementType type) {
		return getLevel(stack, type).totalLevels() / 100.0D;
	}

	public static double getDisplayValue(ItemStack stack, EnhancementType type) {
		return switch (type) {
			case CRIT, POWER -> getWeightedValue(stack, type);
			case PETRIFY, INSTANT_KILL, EXPLOSION -> getFlatChance(stack, type);
			case SPEED, HEIGHT -> getBootPercentBonus(stack, type);
			case WATER_SOUL, FIRE_SOUL -> getSoulSeconds(stack, type);
			case ILLUMINATION -> getIlluminationRadius(stack);
			case ORE_SEEKER -> getOreSearchRange(stack);
			case NAIL_GUN -> getNailGunRange(stack);
		};
	}

	public static double getCritChance(ItemStack stack) {
		return getWeightedValue(stack, EnhancementType.CRIT);
	}

	public static double getPowerBonus(ItemStack stack) {
		return getWeightedValue(stack, EnhancementType.POWER);
	}

	public static double getBootPercentBonus(ItemStack stack, EnhancementType type) {
		return Math.min(4.0D, getLevel(stack, type).totalLevels() * 0.1D);
	}

	public static int getSoulSeconds(ItemStack stack, EnhancementType type) {
		return Math.min(40, getLevel(stack, type).totalLevels());
	}

	public static int getIlluminationRadius(ItemStack stack) {
		int levels = getLevel(stack, EnhancementType.ILLUMINATION).totalLevels();
		if (levels <= 0) {
			return 0;
		}
		return Math.min(10, Math.ceilDiv(levels, 4));
	}

	public static double getOreSearchRange(ItemStack stack) {
		return Math.min(100.0D, getLevel(stack, EnhancementType.ORE_SEEKER).totalLevels() * 2.5D);
	}

	public static double getNailGunRange(ItemStack stack) {
		return Math.min(80.0D, getLevel(stack, EnhancementType.NAIL_GUN).totalLevels() * 2.0D);
	}

	public static String formatPercent(double value) {
		return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
	}

	public static String formatDisplayValue(ItemStack stack, EnhancementType type) {
		return formatDisplayValueText(stack, type).getString();
	}

	public static Text formatDisplayValueText(ItemStack stack, EnhancementType type) {
		return switch (type) {
			case WATER_SOUL, FIRE_SOUL -> Text.translatable("value.magic.seconds", getSoulSeconds(stack, type));
			case ILLUMINATION -> Text.translatable("value.magic.blocks", getIlluminationRadius(stack));
			case ORE_SEEKER, NAIL_GUN -> Text.translatable("value.magic.blocks", String.format(Locale.ROOT, "%.1f", getDisplayValue(stack, type)));
			default -> Text.literal(formatPercent(getDisplayValue(stack, type)));
		};
	}

	private static Level readLevel(NbtCompound nbt) {
		int major = clamp(nbt.getInt(MAJOR_KEY), 0, MAX_MAJOR);
		int minor = clamp(nbt.getInt(MINOR_KEY), 0, MAX_MINOR);
		if (major == 0 || minor == 0) {
			return Level.EMPTY;
		}
		return new Level(major, minor);
	}

	private static Level readLegacyCritLevel(NbtCompound nbt) {
		int major = clamp(nbt.getInt(LEGACY_CRIT_MAJOR_KEY), 0, MAX_MAJOR);
		int minor = clamp(nbt.getInt(LEGACY_CRIT_MINOR_KEY), 0, MAX_MINOR);
		if (major == 0 || minor == 0) {
			return Level.EMPTY;
		}
		return new Level(major, minor);
	}

	private static double weightedValuePerMinor(int major) {
		return switch (major) {
			case 1 -> 0.001D;
			case 2 -> 0.002D;
			case 3 -> 0.005D;
			case 4 -> 0.02D;
			default -> 0.0D;
		};
	}

	private static Item materialForMajor(int major) {
		return switch (major) {
			case 1 -> Items.IRON_INGOT;
			case 2 -> Items.GOLD_INGOT;
			case 3 -> Items.DIAMOND;
			case 4 -> Items.EMERALD;
			default -> Items.AIR;
		};
	}

	private static int materialCost(int minor) {
		return 1 << (minor - 1);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Level(int major, int minor) {
		public static final Level EMPTY = new Level(0, 0);

		public boolean isEmpty() {
			return major == 0 || minor == 0;
		}

		public boolean isMaxed() {
			return major >= MAX_MAJOR && minor >= MAX_MINOR;
		}

		public Level next() {
			if (isEmpty()) {
				return new Level(1, 1);
			}
			if (minor < MAX_MINOR) {
				return new Level(major, minor + 1);
			}
			return new Level(major + 1, 1);
		}

		public int totalLevels() {
			if (isEmpty()) {
				return 0;
			}
			return (major - 1) * MAX_MINOR + minor;
		}

		public String display() {
			if (isEmpty()) {
				return "-";
			}
			return toRoman(major) + "-" + minor;
		}
	}

	public record UpgradeRequirement(Level nextLevel, Item material, int count) {
	}

	private static String toRoman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "-";
		};
	}
}
