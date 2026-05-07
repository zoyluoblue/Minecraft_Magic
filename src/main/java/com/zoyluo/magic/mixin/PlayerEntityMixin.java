package com.zoyluo.magic.mixin;

import com.zoyluo.magic.component.BootEnhancementEffects;
import com.zoyluo.magic.component.EnhancementEffects;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Unique
	private float magic$pendingDamageMultiplier = 1.0F;

	@Unique
	private boolean magic$pendingInstantKill;

	@Unique
	private boolean magic$pendingExplosion;

	@Unique
	private float magic$pendingExplosionTargetHealth;

	@Inject(method = "attack", at = @At("HEAD"))
	private void magic$resetEnhancedAttackState(Entity target, CallbackInfo ci) {
		magic$resetPendingAttackState();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void magic$tickBootEnhancements(CallbackInfo ci) {
		BootEnhancementEffects.tickPlayer((PlayerEntity) (Object) this);
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void magic$blockFireSoulDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (BootEnhancementEffects.blocksFireDamage(player, source)) {
			player.extinguish();
			cir.setReturnValue(false);
		}
	}

	@Inject(
			method = "attack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private void magic$prepareEnhancedAttack(Entity target, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		magic$resetPendingAttackState();

		if (!(player.getWorld() instanceof ServerWorld serverWorld) || !(target instanceof LivingEntity livingTarget) || !livingTarget.isAlive()) {
			return;
		}

		ItemStack weapon = player.getMainHandStack();
		magic$applyPower(weapon);
		magic$applyCrit(player, livingTarget, serverWorld, weapon);
		magic$applyPetrify(player, livingTarget, serverWorld, weapon);
		magic$pendingExplosion = magic$applyExplosion(player, livingTarget, serverWorld, weapon);
		magic$pendingExplosionTargetHealth = livingTarget.getHealth();
		magic$pendingInstantKill = magic$applyInstantKill(player, livingTarget, serverWorld, weapon);
	}

	@ModifyArg(
			method = "attack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			),
			index = 1
	)
	private float magic$modifyEnhancedAttackDamage(float damage) {
		float modifiedDamage = damage * magic$pendingDamageMultiplier;
		if (magic$pendingInstantKill) {
			return Math.max(modifiedDamage, 1_000_000.0F);
		}
		if (magic$pendingExplosion) {
			float maximumNonLethalDamage = Math.max(0.0F, magic$pendingExplosionTargetHealth - 1.0F);
			return Math.min(modifiedDamage, maximumNonLethalDamage);
		}
		return modifiedDamage;
	}

	@Inject(
			method = "attack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
					shift = At.Shift.AFTER
			)
	)
	private void magic$finishEnhancedAttack(Entity target, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (magic$pendingInstantKill && player.getWorld() instanceof ServerWorld serverWorld && target instanceof LivingEntity livingTarget && livingTarget.isAlive()) {
			livingTarget.kill(serverWorld);
		}
		magic$resetPendingAttackState();
	}

	@Unique
	private void magic$applyPower(ItemStack weapon) {
		double powerBonus = EnhancementSystem.getPowerBonus(weapon);
		if (powerBonus > 0.0D) {
			magic$pendingDamageMultiplier *= 1.0F + (float) powerBonus;
		}
	}

	@Unique
	private void magic$applyCrit(PlayerEntity player, LivingEntity target, ServerWorld world, ItemStack weapon) {
		double critChance = EnhancementSystem.getCritChance(weapon);
		if (critChance <= 0.0D || player.getRandom().nextDouble() >= critChance) {
			return;
		}

		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.15F);
		world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5D), target.getZ(), 12, 0.35D, 0.35D, 0.35D, 0.05D);
		sendActionBar(player, Text.translatable("message.magic.triggered", Text.translatable(EnhancementType.CRIT.translationKey()), EnhancementSystem.formatPercent(critChance)));
		magic$pendingDamageMultiplier *= 2.0F;
	}

	@Unique
	private boolean magic$applyInstantKill(PlayerEntity player, LivingEntity target, ServerWorld world, ItemStack weapon) {
		double chance = EnhancementSystem.getFlatChance(weapon, EnhancementType.INSTANT_KILL);
		if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
			return false;
		}

		world.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.5D), target.getZ(), 20, 0.35D, 0.35D, 0.35D, 0.08D);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.7F);
		sendActionBar(player, Text.translatable("message.magic.triggered", Text.translatable(EnhancementType.INSTANT_KILL.translationKey()), EnhancementSystem.formatPercent(chance)));
		return true;
	}

	@Unique
	private void magic$applyPetrify(PlayerEntity player, LivingEntity target, ServerWorld world, ItemStack weapon) {
		EnhancementSystem.Level level = EnhancementSystem.getLevel(weapon, EnhancementType.PETRIFY);
		int totalLevels = level.totalLevels();
		if (totalLevels <= 0) {
			return;
		}

		double chance = totalLevels / 100.0D;
		if (player.getRandom().nextDouble() >= chance) {
			return;
		}

		int seconds = Math.ceilDiv(totalLevels, 10);
		EnhancementEffects.schedulePetrify(world, target, seconds);
		world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getBodyY(0.5D), target.getZ(), 16, 0.35D, 0.35D, 0.35D, 0.02D);
		sendActionBar(player, Text.translatable("message.magic.triggered", Text.translatable(EnhancementType.PETRIFY.translationKey()), EnhancementSystem.formatPercent(chance)));
	}

	@Unique
	private boolean magic$applyExplosion(PlayerEntity player, LivingEntity target, ServerWorld world, ItemStack weapon) {
		double chance = EnhancementSystem.getFlatChance(weapon, EnhancementType.EXPLOSION);
		if (chance <= 0.0D || player.getRandom().nextDouble() >= chance) {
			return false;
		}

		EnhancementEffects.scheduleExplosion(world, target);
		sendActionBar(player, Text.translatable("message.magic.triggered", Text.translatable(EnhancementType.EXPLOSION.translationKey()), EnhancementSystem.formatPercent(chance)));
		return true;
	}

	@Unique
	private void sendActionBar(PlayerEntity player, Text message) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(message, true);
		}
	}

	@Unique
	private void magic$resetPendingAttackState() {
		magic$pendingDamageMultiplier = 1.0F;
		magic$pendingInstantKill = false;
		magic$pendingExplosion = false;
		magic$pendingExplosionTargetHealth = 0.0F;
	}
}
