package com.zoyluo.magic.mixin;

import com.zoyluo.magic.component.SoulChargeService;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guarantees item-owned Soul state is flushed before vanilla saves a disconnecting player. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void magic$flushSoulChargeBeforePlayerSave(ServerPlayerEntity player, CallbackInfo ci) {
		SoulChargeService.flushBeforePlayerRemoval(player);
	}
}
