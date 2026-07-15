package com.zoyluo.magic.gametest;

import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Runtime regression tests for item safety and server-authoritative combat effects. */
@SuppressWarnings("removal")
public final class MagicGameplayGameTests implements FabricGameTest {
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void strengtheningConsumesOnlyTheRequiredMaterial(TestContext context) {
		PlayerEntity player = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		player.getInventory().clear();
		player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 2));
		player.getInventory().setStack(1, new ItemStack(Items.DIRT, 5));
		player.getInventory().setStack(8, new ItemStack(Items.IRON_INGOT, 7));

		SimpleInventory tableInventory = new SimpleInventory(2);
		ItemStack weapon = weaponAtLevel(EnhancementType.CRIT, new EnhancementSystem.Level(1, 3));
		tableInventory.setStack(0, weapon);
		tableInventory.setStack(1, new ItemStack(Items.IRON_INGOT));

		StrengtheningTableScreenHandler handler = new StrengtheningTableScreenHandler(
				0,
				player.getInventory(),
				tableInventory,
				BlockPos.ORIGIN
		);
		try {
			require(context, handler.onButtonClick(player, EnhancementType.CRIT.buttonId()), "valid strengthening request was rejected");
			require(context, player.getInventory().getStack(0).getCount() == 2, "unrelated diamonds were consumed");
			require(context, player.getInventory().getStack(1).getCount() == 5, "unrelated dirt was consumed");
			require(context, player.getInventory().getStack(8).isEmpty(), "required iron in player inventory was not consumed");
			require(context, tableInventory.getStack(1).isEmpty(), "required iron in material slot was not consumed");
			require(
					context,
					EnhancementSystem.getLevel(weapon, EnhancementType.CRIT).equals(new EnhancementSystem.Level(1, 4)),
					"strengthening level did not advance exactly once"
			);
		} finally {
			handler.onClosed(player);
		}
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void strengtheningRejectsInsufficientMaterialWithoutMutation(TestContext context) {
		PlayerEntity player = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		player.getInventory().clear();
		player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 2));
		player.getInventory().setStack(1, new ItemStack(Items.DIRT, 5));
		player.getInventory().setStack(8, new ItemStack(Items.IRON_INGOT, 6));

		SimpleInventory tableInventory = new SimpleInventory(2);
		ItemStack weapon = weaponAtLevel(EnhancementType.CRIT, new EnhancementSystem.Level(1, 3));
		tableInventory.setStack(0, weapon);
		tableInventory.setStack(1, new ItemStack(Items.IRON_INGOT));

		StrengtheningTableScreenHandler handler = new StrengtheningTableScreenHandler(
				0,
				player.getInventory(),
				tableInventory,
				BlockPos.ORIGIN
		);
		try {
			require(context, !handler.onButtonClick(player, EnhancementType.CRIT.buttonId()), "insufficient strengthening request was accepted");
			require(context, player.getInventory().getStack(0).getCount() == 2, "unrelated diamonds changed after rejection");
			require(context, player.getInventory().getStack(1).getCount() == 5, "unrelated dirt changed after rejection");
			require(context, player.getInventory().getStack(8).getCount() == 6, "player iron changed after rejection");
			require(context, tableInventory.getStack(1).getCount() == 1, "material-slot iron changed after rejection");
			require(
					context,
					EnhancementSystem.getLevel(weapon, EnhancementType.CRIT).equals(new EnhancementSystem.Level(1, 3)),
					"strengthening level changed after rejection"
			);
		} finally {
			handler.onClosed(player);
		}
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 10)
	public void rejectedDamageDoesNotCommitInstantKill(TestContext context) {
		PlayerEntity attacker = attackerWith(context, EnhancementType.INSTANT_KILL);
		PlayerEntity target = protectedTarget(context);
		float healthBefore = target.getHealth();

		assertCreativeDamageIsRejected(context, attacker, target);
		attackWithDeterministicTrigger(attacker, target);

		require(context, target.isAlive(), "instant kill bypassed creative damage rejection");
		require(context, target.getHealth() == healthBefore, "rejected instant-kill attack changed health");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void rejectedDamageDoesNotCommitPetrify(TestContext context) {
		PlayerEntity attacker = attackerWith(context, EnhancementType.PETRIFY);
		PlayerEntity target = protectedTarget(context);
		float healthBefore = target.getHealth();

		assertCreativeDamageIsRejected(context, attacker, target);
		attackWithDeterministicTrigger(attacker, target);
		context.waitAndRun(2, () -> {
			require(context, target.isAlive(), "petrify attack killed a creative target");
			require(context, target.getHealth() == healthBefore, "rejected petrify attack changed health");
			require(context, !target.hasStatusEffect(StatusEffects.SLOWNESS), "petrify was committed after damage rejection");
			context.complete();
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void acceptedDamageStillCommitsPetrify(TestContext context) {
		PlayerEntity attacker = attackerWith(context, EnhancementType.PETRIFY);
		PlayerEntity target = player(context, GameMode.SURVIVAL, new BlockPos(2, 1, 2));
		float healthBefore = target.getHealth();

		attackWithDeterministicTrigger(attacker, target);
		context.waitAndRun(2, () -> {
			require(context, target.isAlive(), "accepted petrify control unexpectedly killed the target");
			require(context, target.getHealth() < healthBefore, "survival target did not accept baseline attack damage");
			require(context, target.hasStatusEffect(StatusEffects.SLOWNESS), "petrify was not committed after accepted damage");
			context.complete();
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45)
	public void rejectedDamageDoesNotCommitExplosion(TestContext context) {
		PlayerEntity attacker = attackerWith(context, EnhancementType.EXPLOSION);
		PlayerEntity target = protectedTarget(context);
		float healthBefore = target.getHealth();

		assertCreativeDamageIsRejected(context, attacker, target);
		attackWithDeterministicTrigger(attacker, target);
		context.waitAndRun(35, () -> {
			require(context, target.isAlive(), "explosion bypassed creative damage rejection");
			require(context, target.getHealth() == healthBefore, "rejected explosion attack changed health");
			context.complete();
		});
	}

	private static PlayerEntity player(TestContext context, GameMode gameMode, BlockPos relativePos) {
		PlayerEntity player = context.createMockPlayer(gameMode);
		gameMode.setAbilities(player.getAbilities());
		BlockPos pos = context.getAbsolutePos(relativePos);
		player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		require(context, context.getWorld().spawnEntity(player), "failed to spawn mock player");
		return player;
	}

	private static ItemStack weaponAtLevel(EnhancementType type, EnhancementSystem.Level level) {
		ItemStack weapon = new ItemStack(Items.DIAMOND_SWORD);
		EnhancementSystem.setLevel(weapon, type, level);
		return weapon;
	}

	private static PlayerEntity attackerWith(TestContext context, EnhancementType type) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 2));
		attacker.getInventory().clear();
		attacker.getInventory().setStack(
				attacker.getInventory().selectedSlot,
				weaponAtLevel(type, new EnhancementSystem.Level(4, 10))
		);
		return attacker;
	}

	private static PlayerEntity protectedTarget(TestContext context) {
		return player(context, GameMode.CREATIVE, new BlockPos(2, 1, 2));
	}

	private static void assertCreativeDamageIsRejected(TestContext context, PlayerEntity attacker, PlayerEntity target) {
		boolean applied = target.sidedDamage(context.getWorld().getDamageSources().playerAttack(attacker), 1.0F);
		require(context, !applied, "creative target did not reject baseline player damage");
	}

	private static void attackWithDeterministicTrigger(PlayerEntity attacker, PlayerEntity target) {
		// CheckedRandom seed 4220 yields 0.0896 first, guaranteeing a 40% effect roll.
		attacker.getRandom().setSeed(4220L);
		attacker.attack(target);
	}

	private static void require(TestContext context, boolean condition, String message) {
		if (!condition) {
			context.throwGameTestException(message);
		}
	}
}
