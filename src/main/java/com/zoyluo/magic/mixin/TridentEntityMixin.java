package com.zoyluo.magic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zoyluo.magic.component.WeaponEnhancementCombat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TridentEntity.class)
public abstract class TridentEntityMixin {
	@WrapOperation(
			method = "onEntityHit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;sidedDamage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean magic$applyEnhancedTridentAttack(
			Entity target,
			DamageSource source,
			float damage,
			Operation<Boolean> original
	) {
		TridentEntity projectile = (TridentEntity) (Object) this;
		ItemStack weapon = projectile.getWeaponStack();
		if (!(projectile.getWorld() instanceof ServerWorld world)
				|| !(projectile.getOwner() instanceof PlayerEntity attacker)
				|| !(target instanceof LivingEntity livingTarget)
				|| !livingTarget.isAlive()
				|| weapon == null) {
			return original.call(target, source, damage);
		}

		return WeaponEnhancementCombat.applyEnhancedDamage(
				world,
				attacker,
				livingTarget,
				weapon,
				projectile.getRandom(),
				damage,
				modifiedDamage -> original.call(target, source, modifiedDamage)
		);
	}
}
