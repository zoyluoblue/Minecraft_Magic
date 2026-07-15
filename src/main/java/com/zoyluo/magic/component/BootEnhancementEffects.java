package com.zoyluo.magic.component;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BootEnhancementEffects {
	private BootEnhancementEffects() {
	}

	/**
	 * Kept as a compatibility hook for the existing player mixin. Soul charge is ticked once from
	 * {@link SoulChargeService}'s server-wide end-tick callback.
	 */
	public static void tickPlayer(PlayerEntity player) {
	}

	public static boolean canWalkOnFluid(LivingEntity entity, FluidState fluidState) {
		if (!(entity instanceof PlayerEntity player)) {
			return false;
		}
		if (fluidState.isIn(FluidTags.WATER)) {
			return SoulChargeService.hasCharge(player, EnhancementType.WATER_SOUL);
		}
		if (fluidState.isIn(FluidTags.LAVA)) {
			return SoulChargeService.hasCharge(player, EnhancementType.FIRE_SOUL);
		}
		return false;
	}

	public static boolean canUseFireSoulWalk(PlayerEntity player) {
		return SoulChargeService.hasCharge(player, EnhancementType.FIRE_SOUL);
	}

	public static int getSoulRemainingSeconds(PlayerEntity player, EnhancementType type) {
		long remainingMillis = SoulChargeService.getRemainingMillis(player, type);
		return (int) ((remainingMillis + 999L) / 1_000L);
	}

	public static boolean blocksFireDamage(PlayerEntity player, DamageSource source) {
		return SoulChargeService.hasCharge(player, EnhancementType.FIRE_SOUL)
				&& (source.isOf(DamageTypes.IN_FIRE)
				|| source.isOf(DamageTypes.ON_FIRE)
				|| source.isOf(DamageTypes.LAVA)
				|| source.isOf(DamageTypes.HOT_FLOOR)
				|| source.isOf(DamageTypes.CAMPFIRE));
	}

	static boolean isNearWater(PlayerEntity player) {
		return isNearFluid(player, FluidTags.WATER);
	}

	static boolean isNearLava(PlayerEntity player) {
		return isNearFluid(player, FluidTags.LAVA);
	}

	static void applyFireSoulEffects(ServerPlayerEntity player, boolean nearLava) {
		player.extinguish();
		if (nearLava) {
			stabilizeOnLava(player);
		}
	}

	private static void stabilizeOnLava(PlayerEntity player) {
		double lavaSurfaceY = findHighestFluidSurface(player, FluidTags.LAVA);
		if (Double.isNaN(lavaSurfaceY) || player.getY() >= lavaSurfaceY) {
			return;
		}

		Vec3d velocity = player.getVelocity();
		player.setPosition(player.getX(), lavaSurfaceY, player.getZ());
		player.setVelocity(velocity.x, Math.max(0.0D, velocity.y), velocity.z);
		player.velocityModified = true;
	}

	private static double findHighestFluidSurface(PlayerEntity player, TagKey<Fluid> fluidTag) {
		World world = player.getWorld();
		Box box = player.getBoundingBox().expand(0.1D, 0.25D, 0.1D);
		int minX = MathHelper.floor(box.minX);
		int maxX = MathHelper.floor(box.maxX);
		int minY = MathHelper.floor(player.getY() - 1.0D);
		int maxY = MathHelper.floor(player.getY() + 0.35D);
		int minZ = MathHelper.floor(box.minZ);
		int maxZ = MathHelper.floor(box.maxZ);
		double highest = Double.NaN;

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					FluidState state = world.getFluidState(pos);
					if (!state.isIn(fluidTag)) {
						continue;
					}

					double surfaceY = y + state.getHeight(world, pos);
					if (surfaceY <= player.getY() + 0.35D && (Double.isNaN(highest) || surfaceY > highest)) {
						highest = surfaceY;
					}
				}
			}
		}
		return highest;
	}

	private static boolean isNearFluid(PlayerEntity player, TagKey<Fluid> fluidTag) {
		World world = player.getWorld();
		BlockPos feetPos = BlockPos.ofFloored(player.getX(), player.getY() - 0.05D, player.getZ());
		return world.getFluidState(feetPos).isIn(fluidTag) || world.getFluidState(feetPos.down()).isIn(fluidTag);
	}
}
