package com.zoyluo.magic.mixin;

import com.zoyluo.magic.component.SoulChargeService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
	@Shadow
	@Final
	public PlayerEntity player;

	@Shadow
	public abstract ItemStack getStack(int slot);

	@Inject(
			method = "removeStack(II)Lnet/minecraft/item/ItemStack;",
			at = @At("HEAD")
	)
	private void magic$flushSoulChargeBeforeSplit(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		if (amount > 0) {
			SoulChargeService.beforeEquipmentMutation(player, getStack(slot));
		}
	}

	@Inject(
			method = "removeStack(I)Lnet/minecraft/item/ItemStack;",
			at = @At("HEAD")
	)
	private void magic$flushSoulChargeBeforeRemove(int slot, CallbackInfoReturnable<ItemStack> cir) {
		SoulChargeService.beforeEquipmentMutation(player, getStack(slot));
	}

	@Inject(
			method = "setStack(ILnet/minecraft/item/ItemStack;)V",
			at = @At("HEAD")
	)
	private void magic$flushSoulChargeBeforeReplace(int slot, ItemStack stack, CallbackInfo ci) {
		ItemStack previous = getStack(slot);
		if (previous != stack) {
			SoulChargeService.beforeEquipmentMutation(player, previous);
		}
	}
}
