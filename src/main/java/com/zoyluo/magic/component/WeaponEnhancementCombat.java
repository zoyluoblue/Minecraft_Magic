package com.zoyluo.magic.component;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

/**
 * Stateless server-side resolution for weapon enhancements.
 *
 * <p>The caller supplies the vanilla damage operation. This class prepares all random outcomes,
 * invokes that operation exactly once, and commits side effects only when vanilla accepts the hit.
 */
public final class WeaponEnhancementCombat {
	private static final float INSTANT_KILL_DAMAGE = 1_000_000.0F;

	private WeaponEnhancementCombat() {
	}

	public static boolean applyEnhancedDamage(
			ServerWorld world,
			PlayerEntity attacker,
			LivingEntity target,
			ItemStack weapon,
			Random random,
			float vanillaDamage,
			DamageOperation damageOperation
	) {
		PreparedAttack prepared = prepare(target, weapon, random);
		boolean damageApplied = damageOperation.apply(prepared.modifiedDamage(vanillaDamage));
		if (damageApplied) {
			commit(world, attacker, target, prepared);
		}
		return damageApplied;
	}

	private static PreparedAttack prepare(LivingEntity target, ItemStack weapon, Random random) {
		float damageMultiplier = 1.0F;
		double powerBonus = EnhancementSystem.getPowerBonus(weapon);
		if (powerBonus > 0.0D) {
			damageMultiplier *= 1.0F + (float) powerBonus;
		}

		double critChance = EnhancementSystem.getCritChance(weapon);
		boolean crit = critChance > 0.0D && random.nextDouble() < critChance;
		if (crit) {
			damageMultiplier *= 2.0F;
		}

		EnhancementSystem.Level petrifyLevel = EnhancementSystem.getLevel(weapon, EnhancementType.PETRIFY);
		int petrifyLevels = petrifyLevel.totalLevels();
		double petrifyChance = petrifyLevels / 100.0D;
		boolean petrify = petrifyLevels > 0 && random.nextDouble() < petrifyChance;
		int petrifySeconds = petrify ? Math.ceilDiv(petrifyLevels, 10) : 0;

		double explosionChance = EnhancementSystem.getFlatChance(weapon, EnhancementType.EXPLOSION);
		boolean explosion = explosionChance > 0.0D && random.nextDouble() < explosionChance;

		double instantKillChance = EnhancementSystem.getFlatChance(weapon, EnhancementType.INSTANT_KILL);
		boolean instantKill = instantKillChance > 0.0D && random.nextDouble() < instantKillChance;

		return new PreparedAttack(
				damageMultiplier,
				crit,
				critChance,
				petrify,
				petrifyChance,
				petrifySeconds,
				explosion,
				explosionChance,
				target.getHealth(),
				instantKill,
				instantKillChance
		);
	}

	private static void commit(ServerWorld world, PlayerEntity attacker, LivingEntity target, PreparedAttack prepared) {
		if (prepared.crit()) {
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.15F);
			world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5D), target.getZ(), 12, 0.35D, 0.35D, 0.35D, 0.05D);
			sendActionBar(attacker, EnhancementType.CRIT, prepared.critChance());
		}

		if (prepared.petrify()) {
			EnhancementEffects.schedulePetrify(world, target, prepared.petrifySeconds());
			world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getBodyY(0.5D), target.getZ(), 16, 0.35D, 0.35D, 0.35D, 0.02D);
			sendActionBar(attacker, EnhancementType.PETRIFY, prepared.petrifyChance());
		}

		if (prepared.explosion()) {
			EnhancementEffects.scheduleExplosion(world, attacker, target);
			sendActionBar(attacker, EnhancementType.EXPLOSION, prepared.explosionChance());
		}

		if (prepared.instantKill()) {
			world.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.5D), target.getZ(), 20, 0.35D, 0.35D, 0.35D, 0.08D);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.7F);
			sendActionBar(attacker, EnhancementType.INSTANT_KILL, prepared.instantKillChance());
		}
	}

	private static void sendActionBar(PlayerEntity player, EnhancementType type, double chance) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(
					Text.translatable(
							"message.magic.triggered",
							Text.translatable(type.translationKey()),
							EnhancementSystem.formatPercent(chance)
					),
					true
			);
		}
	}

	@FunctionalInterface
	public interface DamageOperation {
		boolean apply(float damage);
	}

	private record PreparedAttack(
			float damageMultiplier,
			boolean crit,
			double critChance,
			boolean petrify,
			double petrifyChance,
			int petrifySeconds,
			boolean explosion,
			double explosionChance,
			float explosionTargetHealth,
			boolean instantKill,
			double instantKillChance
	) {
		private float modifiedDamage(float vanillaDamage) {
			float modifiedDamage = vanillaDamage * damageMultiplier;
			if (instantKill) {
				return Math.max(modifiedDamage, INSTANT_KILL_DAMAGE);
			}
			if (explosion) {
				float maximumNonLethalDamage = Math.max(0.0F, explosionTargetHealth - 1.0F);
				return Math.min(modifiedDamage, maximumNonLethalDamage);
			}
			return modifiedDamage;
		}
	}
}
