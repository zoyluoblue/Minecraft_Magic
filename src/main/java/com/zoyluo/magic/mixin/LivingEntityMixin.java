package com.zoyluo.magic.mixin;

import com.zoyluo.magic.component.BootEnhancementEffects;
import com.zoyluo.magic.component.LegEnhancementEffects;
import com.zoyluo.magic.component.SoulChargeData;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
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

	@Shadow
	@Final
	private DefaultedList<ItemStack> syncedArmorStacks;

	@Inject(method = "canWalkOnFluid", at = @At("HEAD"), cancellable = true)
	private void magic$allowSoulWalk(FluidState state, CallbackInfoReturnable<Boolean> cir) {
		if (BootEnhancementEffects.canWalkOnFluid((LivingEntity) (Object) this, state)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "areItemsDifferent", at = @At("RETURN"), cancellable = true)
	private void magic$ignoreSoulRuntimeEquipmentDiff(
			ItemStack previous,
			ItemStack current,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValueZ()
				&& (Object) this instanceof ServerPlayerEntity player
				&& SoulChargeData.differsOnlyBySoulRuntime(previous, current)) {
			int feetIndex = EquipmentSlot.FEET.getEntitySlotId();
			if (player.getEquippedStack(EquipmentSlot.FEET) == current
					&& syncedArmorStacks.get(feetIndex) == previous) {
				// Vanilla only advances this snapshot for reported equipment changes. Advance just
				// the feet copy here so a suppressed runtime checkpoint is not re-compared every tick.
				syncedArmorStacks.set(feetIndex, current.copy());
			}
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "travelMidAir", at = @At("TAIL"))
	private void magic$applyLegAirAcceleration(Vec3d movementInput, CallbackInfo ci) {
		if ((Object) this instanceof PlayerEntity player) {
			double speedBonus = LegEnhancementEffects.getSpeedBonus(player);
			if (speedBonus > 0.0D && movementInput.lengthSquared() > 0.0D) {
				((LivingEntity) (Object) this).updateVelocity(getOffGroundSpeed() * (float) speedBonus, movementInput);
			}
		}
	}

	@Inject(method = "computeFallDamage", at = @At("RETURN"), cancellable = true)
	private void magic$reduceHeightFallDamage(float fallDistance, float damageMultiplier, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof PlayerEntity player) {
			double reduction = LegEnhancementEffects.getFallDamageReduction(player);
			if (reduction > 0.0D) {
				cir.setReturnValue(Math.max(0, (int) Math.ceil(cir.getReturnValueI() * (1.0D - reduction))));
			}
		}
	}
}
