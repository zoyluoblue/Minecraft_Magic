package com.zoyluo.magic.component;

import org.jetbrains.annotations.Nullable;

public enum EnhancementType {
	CRIT(0, "crit", "enhancement.magic.crit"),
	PETRIFY(1, "petrify", "enhancement.magic.petrify"),
	INSTANT_KILL(2, "instant_kill", "enhancement.magic.instant_kill"),
	POWER(3, "power", "enhancement.magic.power"),
	EXPLOSION(4, "explosion", "enhancement.magic.explosion");

	private final int buttonId;
	private final String id;
	private final String translationKey;

	EnhancementType(int buttonId, String id, String translationKey) {
		this.buttonId = buttonId;
		this.id = id;
		this.translationKey = translationKey;
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

	@Nullable
	public static EnhancementType byButtonId(int buttonId) {
		for (EnhancementType type : values()) {
			if (type.buttonId == buttonId) {
				return type;
			}
		}
		return null;
	}
}
