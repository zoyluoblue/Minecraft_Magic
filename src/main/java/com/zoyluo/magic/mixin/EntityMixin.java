package com.zoyluo.magic.mixin;

import com.zoyluo.magic.component.BootEnhancementEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "updateMovementInFluid", at = @At("HEAD"), cancellable = true)
	private void magic$ignoreLavaFlow(TagKey<Fluid> fluidTag, double speed, CallbackInfoReturnable<Boolean> cir) {
		if (fluidTag == FluidTags.LAVA && (Object) this instanceof PlayerEntity player && BootEnhancementEffects.canUseFireSoulWalk(player)) {
			cir.setReturnValue(false);
		}
	}
}
