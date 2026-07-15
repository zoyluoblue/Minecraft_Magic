package com.zoyluo.magic.gametest;

import com.zoyluo.magic.component.EnhancementGlowProfile;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/** Server-safe regression coverage for the item-stack visual tier resolver. */
@SuppressWarnings("removal")
public final class Phase3AVisualRuleGameTests implements FabricGameTest {
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void highestApplicableLevelIsEmptyWithoutEnhancements(TestContext context) {
		require(
				context,
				EnhancementSystem.getHighestApplicableLevel(ItemStack.EMPTY).isEmpty(),
				"empty stack unexpectedly produced a visual enhancement level"
		);
		require(
				context,
				EnhancementSystem.getHighestApplicableLevel(new ItemStack(Items.DIAMOND_SWORD)).isEmpty(),
				"unenhanced sword unexpectedly produced a visual enhancement level"
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void highestApplicableLevelPreservesAllTierBoundaries(TestContext context) {
		EnhancementSystem.Level[] boundaries = {
				new EnhancementSystem.Level(1, 1),
				new EnhancementSystem.Level(1, 10),
				new EnhancementSystem.Level(2, 1),
				new EnhancementSystem.Level(2, 10),
				new EnhancementSystem.Level(3, 1),
				new EnhancementSystem.Level(3, 10),
				new EnhancementSystem.Level(4, 1),
				new EnhancementSystem.Level(4, 10)
		};

		for (EnhancementSystem.Level boundary : boundaries) {
			ItemStack sword = enhancedStack(Items.DIAMOND_SWORD.getDefaultStack(), EnhancementType.CRIT, boundary);
			require(
					context,
					EnhancementSystem.getHighestApplicableLevel(sword).equals(boundary),
					"visual resolver changed tier boundary " + boundary.display()
			);
		}
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void levelTotalsIncreaseMonotonicallyWithinEveryTier(TestContext context) {
		int previousTotal = 0;
		for (int major = 1; major <= EnhancementSystem.MAX_MAJOR; major++) {
			for (int minor = 1; minor <= EnhancementSystem.MAX_MINOR; minor++) {
				EnhancementSystem.Level expected = new EnhancementSystem.Level(major, minor);
				ItemStack sword = enhancedStack(Items.DIAMOND_SWORD.getDefaultStack(), EnhancementType.CRIT, expected);
				EnhancementSystem.Level resolved = EnhancementSystem.getHighestApplicableLevel(sword);

				require(context, resolved.equals(expected), "visual resolver changed " + expected.display());
				require(
						context,
						resolved.totalLevels() == (major - 1) * EnhancementSystem.MAX_MINOR + minor,
						"incorrect total level for " + expected.display()
				);
				require(
						context,
						resolved.totalLevels() > previousTotal,
						"visual total level did not increase monotonically at " + expected.display()
				);
				previousTotal = resolved.totalLevels();
			}
		}
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void glowProfilesPreserveTierColorsAndIncreaseWithinEveryTier(TestContext context) {
		int[] expectedColors = {0x4EA8FF, 0xA970FF, 0xFF70C8, 0xFF9A45};
		for (int major = 1; major <= EnhancementSystem.MAX_MAJOR; major++) {
			EnhancementGlowProfile previous = EnhancementGlowProfile.NONE;
			for (int minor = 1; minor <= EnhancementSystem.MAX_MINOR; minor++) {
				EnhancementGlowProfile profile = EnhancementGlowProfile.forLevel(
						new EnhancementSystem.Level(major, minor)
				);
				require(context, profile.rgb() == expectedColors[major - 1], "wrong tier glow color");
				require(context, profile.outlineOpacity() > previous.outlineOpacity(), "outline opacity did not increase");
				require(context, profile.outlineWidth() > previous.outlineWidth(), "outline width did not increase");
				require(context, profile.outlineOpacity() >= 0.62F && profile.outlineOpacity() <= 0.94F, "outline opacity out of bounds");
				require(context, profile.outlineWidth() >= 0.35F && profile.outlineWidth() <= 1.0F, "outline width out of bounds");
				previous = profile;
			}
		}

		require(
				context,
				EnhancementGlowProfile.forLevel(EnhancementSystem.Level.EMPTY).equals(EnhancementGlowProfile.NONE),
				"empty enhancement unexpectedly produced a glow profile"
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void highestApplicableLevelSelectsTheStrongestApplicableEnhancement(TestContext context) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		EnhancementSystem.setLevel(sword, EnhancementType.CRIT, new EnhancementSystem.Level(2, 10));
		EnhancementSystem.setLevel(sword, EnhancementType.POWER, new EnhancementSystem.Level(3, 2));
		EnhancementSystem.setLevel(sword, EnhancementType.EXPLOSION, new EnhancementSystem.Level(1, 9));

		require(
				context,
				EnhancementSystem.getHighestApplicableLevel(sword).equals(new EnhancementSystem.Level(3, 2)),
				"visual resolver did not select the strongest applicable enhancement"
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void highestApplicableLevelIgnoresForgedInapplicableEnhancements(TestContext context) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		EnhancementSystem.setLevel(sword, EnhancementType.CRIT, new EnhancementSystem.Level(1, 3));
		EnhancementSystem.setLevel(sword, EnhancementType.WATER_SOUL, new EnhancementSystem.Level(4, 10));
		require(
				context,
				EnhancementSystem.getHighestApplicableLevel(sword).equals(new EnhancementSystem.Level(1, 3)),
				"forged boots enhancement controlled a weapon's visual tier"
		);

		ItemStack dirt = new ItemStack(Items.DIRT);
		EnhancementSystem.setLevel(dirt, EnhancementType.CRIT, new EnhancementSystem.Level(4, 10));
		require(
				context,
				EnhancementSystem.getHighestApplicableLevel(dirt).isEmpty(),
				"non-upgradeable item used forged enhancement data for visuals"
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void highestApplicableLevelSupportsLegacyCriticalData(TestContext context) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		NbtCompound legacyData = new NbtCompound();
		legacyData.putInt("MagicCritMajor", 3);
		legacyData.putInt("MagicCritMinor", 7);
		sword.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(legacyData));

		require(
				context,
				EnhancementSystem.getHighestApplicableLevel(sword).equals(new EnhancementSystem.Level(3, 7)),
				"legacy critical enhancement did not control the visual tier"
		);
		context.complete();
	}

	private static ItemStack enhancedStack(
			ItemStack stack,
			EnhancementType type,
			EnhancementSystem.Level level
	) {
		EnhancementSystem.setLevel(stack, type, level);
		return stack;
	}

	private static void require(TestContext context, boolean condition, String message) {
		if (!condition) {
			context.throwGameTestException(message);
		}
	}
}
