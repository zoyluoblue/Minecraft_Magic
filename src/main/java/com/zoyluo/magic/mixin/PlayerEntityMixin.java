package com.zoyluo.magic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zoyluo.magic.component.BootEnhancementEffects;
import com.zoyluo.magic.component.HelmetEnhancementEffects;
import com.zoyluo.magic.component.LegEnhancementEffects;
import com.zoyluo.magic.component.NailGunEffects;
import com.zoyluo.magic.component.SoulChargeService;
import com.zoyluo.magic.component.WeaponEnhancementCombat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "equipStack", at = @At("HEAD"))
	private void magic$flushSoulChargeBeforeEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (slot == EquipmentSlot.FEET) {
			ItemStack equipped = player.getEquippedStack(slot);
			if (equipped != stack) {
				SoulChargeService.beforeEquipmentMutation(player, equipped);
			}
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void magic$tickBootEnhancements(CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		BootEnhancementEffects.tickPlayer(player);
		LegEnhancementEffects.tickPlayer(player);
		HelmetEnhancementEffects.tickPlayer(player);
		NailGunEffects.tickPlayer(player);
	}

	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void magic$blockFireSoulDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (BootEnhancementEffects.blocksFireDamage(player, source)) {
			player.extinguish();
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(
			method = "attack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean magic$applyEnhancedAttack(
			Entity target,
			DamageSource source,
			float damage,
			Operation<Boolean> original
	) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (!(player.getWorld() instanceof ServerWorld world)
				|| !(target instanceof LivingEntity livingTarget)
				|| !livingTarget.isAlive()) {
			return original.call(target, source, damage);
		}

		return WeaponEnhancementCombat.applyEnhancedDamage(
				world,
				player,
				livingTarget,
				player.getMainHandStack(),
				player.getRandom(),
				damage,
				modifiedDamage -> original.call(target, source, modifiedDamage)
		);
	}
}
