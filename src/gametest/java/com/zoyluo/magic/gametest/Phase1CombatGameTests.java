package com.zoyluo.magic.gametest;

import com.zoyluo.magic.component.EnhancementEffects;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import com.zoyluo.magic.component.WeaponEnhancementCombat;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** Deterministic Phase 1 combat and temporary-effect regression coverage. */
@SuppressWarnings("removal")
public final class Phase1CombatGameTests implements FabricGameTest {
	private static final EnhancementSystem.Level MAX_LEVEL =
			new EnhancementSystem.Level(EnhancementSystem.MAX_MAJOR, EnhancementSystem.MAX_MINOR);
	private static final long DOUBLE_TRIGGER_SEED = 4220L;

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 10)
	public void sharedResolverCallsDamageOnceAndDoesNotCommitRejectedHit(TestContext context) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		PlayerEntity target = player(context, GameMode.SURVIVAL, new BlockPos(3, 1, 1));
		ItemStack weapon = enhancedWeapon(Items.DIAMOND_SWORD, EnhancementType.PETRIFY, EnhancementType.INSTANT_KILL);
		int[] calls = {0};

		boolean damageApplied = WeaponEnhancementCombat.applyEnhancedDamage(
				context.getWorld(),
				attacker,
				target,
				weapon,
				Random.create(DOUBLE_TRIGGER_SEED),
				3.0F,
				damage -> {
					calls[0]++;
					return false;
				}
		);

		require(context, !damageApplied, "resolver changed a rejected damage result");
		require(context, calls[0] == 1, "resolver did not call the original damage operation exactly once");
		context.waitAndRun(2, () -> {
			require(context, target.isAlive(), "rejected resolver hit killed the target");
			require(context, !target.hasStatusEffect(StatusEffects.SLOWNESS), "rejected resolver hit committed petrify");
			context.complete();
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 10)
	public void bowAndCrossbowArrowsUseFiringWeaponSnapshots(TestContext context) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		PlayerEntity bowTarget = player(context, GameMode.SURVIVAL, new BlockPos(3, 1, 1));
		PlayerEntity crossbowTarget = player(context, GameMode.SURVIVAL, new BlockPos(3, 1, 3));

		assertArrowSnapshot(context, attacker, bowTarget, Items.BOW, "bow");
		assertArrowSnapshot(context, attacker, crossbowTarget, Items.CROSSBOW, "crossbow");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 10)
	public void tridentSnapshotSurvivesWeaponMutationAndNbtRoundTrip(TestContext context) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		PlayerEntity target = player(context, GameMode.SURVIVAL, new BlockPos(3, 1, 1));
		ItemStack originalTrident = enhancedWeapon(Items.TRIDENT, EnhancementType.POWER);
		setMainHand(attacker, originalTrident);

		TestTrident fired = new TestTrident(context.getWorld(), attacker, originalTrident);
		NbtCompound projectileNbt = new NbtCompound();
		fired.writeCustomDataToNbt(projectileNbt);

		EnhancementSystem.setLevel(originalTrident, EnhancementType.POWER, EnhancementSystem.Level.EMPTY);
		setMainHand(attacker, new ItemStack(Items.STICK));

		TestTrident restored = new TestTrident(context.getWorld());
		restored.readCustomDataFromNbt(projectileNbt);
		restored.setOwner(attacker);
		require(
				context,
				EnhancementSystem.getLevel(restored.getWeaponStack(), EnhancementType.POWER).equals(MAX_LEVEL),
				"trident NBT round-trip lost the firing weapon snapshot"
		);

		float healthBefore = target.getHealth();
		restored.hit(target);
		require(context, healthBefore - target.getHealth() > 8.1F, "restored trident did not use snapshot power");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 10)
	public void rejectedArrowHitDoesNotCommitPetrifyOrInstantKill(TestContext context) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		PlayerEntity target = player(context, GameMode.CREATIVE, new BlockPos(3, 1, 1));
		ItemStack bow = enhancedWeapon(Items.BOW, EnhancementType.PETRIFY, EnhancementType.INSTANT_KILL);
		setMainHand(attacker, bow);
		TestArrow arrow = new TestArrow(context.getWorld(), attacker, new ItemStack(Items.ARROW), bow);
		arrow.setVelocity(1.0D, 0.0D, 0.0D);
		arrow.getRandom().setSeed(DOUBLE_TRIGGER_SEED);
		float healthBefore = target.getHealth();

		arrow.hit(target);
		context.waitAndRun(2, () -> {
			require(context, target.isAlive(), "rejected projectile instant-kill killed a creative target");
			require(context, target.getHealth() == healthBefore, "rejected projectile hit changed creative health");
			require(context, !target.hasStatusEffect(StatusEffects.SLOWNESS), "rejected projectile hit committed petrify");
			context.complete();
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45)
	public void petrifyUsesTickDeadlineAndReapplicationExtendsIt(TestContext context) {
		PlayerEntity target = player(context, GameMode.SURVIVAL, new BlockPos(3, 1, 1));
		EnhancementEffects.schedulePetrify(context.getWorld(), target, 1);

		context.runAtTick(2, () ->
				require(context, target.hasStatusEffect(StatusEffects.SLOWNESS), "petrify was not active after scheduling")
		);
		context.runAtTick(15, () -> EnhancementEffects.schedulePetrify(context.getWorld(), target, 1));
		context.runAtTick(23, () ->
				require(context, target.hasStatusEffect(StatusEffects.SLOWNESS), "reapplied petrify did not extend its deadline")
		);
		context.runAtTick(40, () -> {
			require(context, !target.hasStatusEffect(StatusEffects.SLOWNESS), "petrify remained after its extended tick deadline");
			context.complete();
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45)
	public void pendingExplosionCancelsWhenAttackerIsRemoved(TestContext context) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		PlayerEntity target = player(context, GameMode.SURVIVAL, new BlockPos(4, 1, 1));
		float healthBefore = target.getHealth();

		EnhancementEffects.scheduleExplosion(context.getWorld(), attacker, target);
		attacker.discard();
		context.waitAndRun(35, () -> {
			require(context, target.isAlive(), "unresolved explosion source killed the target");
			require(context, target.getHealth() == healthBefore, "unresolved explosion source still damaged the target");
			context.complete();
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 45)
	public void pendingExplosionDoesNotForceKillAProtectedTarget(TestContext context) {
		PlayerEntity attacker = player(context, GameMode.SURVIVAL, new BlockPos(1, 1, 1));
		PlayerEntity target = player(context, GameMode.SURVIVAL, new BlockPos(4, 1, 1));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false));

		EnhancementEffects.scheduleExplosion(context.getWorld(), attacker, target);
		context.waitAndRun(35, () -> {
			require(context, target.isAlive(), "pending explosion force-killed a target that survived vanilla damage");
			context.complete();
		});
	}

	private static void assertArrowSnapshot(
			TestContext context,
			PlayerEntity attacker,
			PlayerEntity target,
			Item weaponItem,
			String weaponName
	) {
		ItemStack originalWeapon = enhancedWeapon(weaponItem, EnhancementType.POWER);
		setMainHand(attacker, originalWeapon);
		TestArrow arrow = new TestArrow(context.getWorld(), attacker, new ItemStack(Items.ARROW), originalWeapon);
		arrow.setVelocity(1.0D, 0.0D, 0.0D);

		EnhancementSystem.setLevel(originalWeapon, EnhancementType.POWER, EnhancementSystem.Level.EMPTY);
		setMainHand(attacker, new ItemStack(Items.STICK));
		require(
				context,
				EnhancementSystem.getLevel(arrow.getWeaponStack(), EnhancementType.POWER).equals(MAX_LEVEL),
				weaponName + " arrow did not retain an independent firing snapshot"
		);

		float healthBefore = target.getHealth();
		arrow.hit(target);
		require(context, healthBefore - target.getHealth() > 2.1F, weaponName + " arrow did not use snapshot power");
	}

	private static ItemStack enhancedWeapon(Item item, EnhancementType... types) {
		ItemStack weapon = new ItemStack(item);
		for (EnhancementType type : types) {
			EnhancementSystem.setLevel(weapon, type, MAX_LEVEL);
		}
		return weapon;
	}

	private static void setMainHand(PlayerEntity player, ItemStack stack) {
		player.getInventory().setStack(player.getInventory().selectedSlot, stack);
	}

	private static PlayerEntity player(TestContext context, GameMode gameMode, BlockPos relativePos) {
		PlayerEntity player = context.createMockPlayer(gameMode);
		gameMode.setAbilities(player.getAbilities());
		BlockPos pos = context.getAbsolutePos(relativePos);
		player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		require(context, context.getWorld().spawnEntity(player), "failed to spawn mock player");
		return player;
	}

	private static void require(TestContext context, boolean condition, String message) {
		if (!condition) {
			context.throwGameTestException(message);
		}
	}

	private static final class TestArrow extends ArrowEntity {
		private TestArrow(World world, LivingEntity owner, ItemStack projectileStack, ItemStack weaponStack) {
			super(world, owner, projectileStack, weaponStack);
		}

		private void hit(Entity target) {
			super.onEntityHit(new EntityHitResult(target));
		}
	}

	private static final class TestTrident extends TridentEntity {
		private TestTrident(ServerWorld world) {
			super(EntityType.TRIDENT, world);
		}

		private TestTrident(World world, LivingEntity owner, ItemStack stack) {
			super(world, owner, stack);
		}

		private void hit(Entity target) {
			super.onEntityHit(new EntityHitResult(target));
		}
	}
}
