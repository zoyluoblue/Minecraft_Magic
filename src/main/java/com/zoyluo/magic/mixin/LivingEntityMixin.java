package com.zoyluo.magic.mixin;

import com.zoyluo.magic.component.BootEnhancementEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	protected abstract float getOffGroundSpeed();

	@Inject(method = "canWalkOnFluid", at = @At("HEAD"), cancellable = true)
	private void magic$allowSoulWalk(FluidState state, CallbackInfoReturnable<Boolean> cir) {
		if (BootEnhancementEffects.canWalkOnFluid((LivingEntity) (Object) this, state)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "travelMidAir", at = @At("TAIL"))
	private void magic$applyBootAirAcceleration(Vec3d movementInput, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity player) {
			double speedBonus = BootEnhancementEffects.getSpeedBonus(player);
			if (speedBonus > 0.0D && movementInput.lengthSquared() > 0.0D) {
				((LivingEntity) (Object) this).updateVelocity(getOffGroundSpeed() * (float) speedBonus, movementInput);
			}
		}
	}

	@Inject(method = "computeFallDamage", at = @At("RETURN"), cancellable = true)
	private void magic$reduceHeightFallDamage(float fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof PlayerEntity player) {
			double reduction = BootEnhancementEffects.getFallDamageReduction(player);
			if (reduction > 0.0D) {
				cir.setReturnValue(Math.max(0, (int) Math.ceil(cir.getReturnValueI() * (1.0D - reduction))));
			}
		}
	}
}
