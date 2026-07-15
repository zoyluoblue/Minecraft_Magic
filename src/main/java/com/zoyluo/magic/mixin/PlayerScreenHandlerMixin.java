package com.zoyluo.magic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zoyluo.magic.component.SoulChargeService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Flushes item-owned Soul state before vanilla shift-click can split an armor stack directly. */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin {
	@WrapOperation(
			method = "quickMove",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/screen/PlayerScreenHandler;insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z"
			)
	)
	private boolean magic$flushSoulChargeBeforeQuickMoveSplit(
			PlayerScreenHandler handler,
			ItemStack stack,
			int startIndex,
			int endIndex,
			boolean fromLast,
			Operation<Boolean> original,
			PlayerEntity player,
			int slotIndex
	) {
		SoulChargeService.EquipmentMutation mutation = SoulChargeService.beforeEquipmentMutation(player, stack);
		boolean moved = original.call(handler, stack, startIndex, endIndex, fromLast);
		if (!moved) {
			SoulChargeService.restoreAfterFailedEquipmentMutation(player, stack, mutation);
		}
		return moved;
	}
}
