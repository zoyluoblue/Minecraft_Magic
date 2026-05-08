package com.zoyluo.magic.component;

import org.jetbrains.annotations.Nullable;

public enum EnhancementType {
	CRIT(0, "crit", "enhancement.magic.crit", Target.WEAPON),
	PETRIFY(1, "petrify", "enhancement.magic.petrify", Target.WEAPON),
	INSTANT_KILL(2, "instant_kill", "enhancement.magic.instant_kill", Target.WEAPON),
	POWER(3, "power", "enhancement.magic.power", Target.WEAPON),
	EXPLOSION(4, "explosion", "enhancement.magic.explosion", Target.WEAPON),
	SPEED(5, "speed", "enhancement.magic.speed", Target.LEGS),
	HEIGHT(6, "height", "enhancement.magic.height", Target.LEGS),
	WATER_SOUL(7, "water_soul", "enhancement.magic.water_soul", Target.BOOTS),
	FIRE_SOUL(8, "fire_soul", "enhancement.magic.fire_soul", Target.BOOTS),
	ILLUMINATION(9, "illumination", "enhancement.magic.illumination", Target.HELMET),
	ORE_SEEKER(10, "ore_seeker", "enhancement.magic.ore_seeker", Target.HELMET),
	NAIL_GUN(11, "nail_gun", "enhancement.magic.nail_gun", Target.CHEST);

	private final int buttonId;
	private final String id;
	private final String translationKey;
	private final Target target;

	EnhancementType(int buttonId, String id, String translationKey, Target target) {
		this.buttonId = buttonId;
		this.id = id;
		this.translationKey = translationKey;
		this.target = target;
	}

	public int buttonId() {
		return buttonId;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return translationKey;
	}

	public boolean isWeaponEnhancement() {
		return target == Target.WEAPON;
	}

	public boolean isBootsEnhancement() {
		return target == Target.BOOTS;
	}

	public boolean isHelmetEnhancement() {
		return target == Target.HELMET;
	}

	public boolean isLegsEnhancement() {
		return target == Target.LEGS;
	}

	public boolean isChestEnhancement() {
		return target == Target.CHEST;
	}

	@Nullable
	public static EnhancementType byButtonId(int buttonId) {
		for (EnhancementType type : values()) {
			if (type.buttonId == buttonId) {
				return type;
			}
		}
		return null;
	}

	private enum Target {
		WEAPON,
		BOOTS,
		HELMET,
		LEGS,
		CHEST
	}
}
