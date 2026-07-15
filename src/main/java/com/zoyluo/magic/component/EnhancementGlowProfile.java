package com.zoyluo.magic.component;

/**
 * Server-safe mapping from an enhancement level to its client outline profile.
 * The profile is derived data only and never changes the base item color or world light.
 */
public record EnhancementGlowProfile(int rgb, float outlineOpacity, float outlineWidth) {
	public static final EnhancementGlowProfile NONE = new EnhancementGlowProfile(0xFFFFFF, 0.0F, 0.0F);

	private static final float MIN_OUTLINE_OPACITY = 0.62F;
	private static final float MAX_OUTLINE_OPACITY = 0.94F;
	private static final float MIN_OUTLINE_WIDTH = 0.35F;
	private static final float MAX_OUTLINE_WIDTH = 1.0F;
	private static final EnhancementGlowProfile[][] PROFILES = createProfiles();

	public static EnhancementGlowProfile forLevel(EnhancementSystem.Level level) {
		if (level == null || level.isEmpty()) {
			return NONE;
		}

		int major = clamp(level.major(), 1, EnhancementSystem.MAX_MAJOR);
		int minor = clamp(level.minor(), 1, EnhancementSystem.MAX_MINOR);
		return PROFILES[major - 1][minor - 1];
	}

	public boolean isEmpty() {
		return outlineOpacity <= 0.0F || outlineWidth <= 0.0F;
	}

	public int red() {
		return rgb >> 16 & 0xFF;
	}

	public int green() {
		return rgb >> 8 & 0xFF;
	}

	public int blue() {
		return rgb & 0xFF;
	}

	private static EnhancementGlowProfile[][] createProfiles() {
		int[] colors = {0x4EA8FF, 0xA970FF, 0xFF70C8, 0xFF9A45};
		EnhancementGlowProfile[][] profiles =
				new EnhancementGlowProfile[EnhancementSystem.MAX_MAJOR][EnhancementSystem.MAX_MINOR];
		for (int major = 0; major < profiles.length; major++) {
			for (int minor = 0; minor < profiles[major].length; minor++) {
				float progress = (float) minor / (EnhancementSystem.MAX_MINOR - 1.0F);
				profiles[major][minor] = new EnhancementGlowProfile(
						colors[major],
						lerp(progress, MIN_OUTLINE_OPACITY, MAX_OUTLINE_OPACITY),
						lerp(progress, MIN_OUTLINE_WIDTH, MAX_OUTLINE_WIDTH)
				);
			}
		}
		return profiles;
	}

	private static float lerp(float amount, float start, float end) {
		return start + (end - start) * amount;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
