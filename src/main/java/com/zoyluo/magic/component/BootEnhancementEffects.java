package com.zoyluo.magic.component;

import com.zoyluo.magic.Magic;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BootEnhancementEffects {
	private static final Identifier SPEED_MODIFIER_ID = Magic.id("boots_speed");
	private static final Identifier HEIGHT_MODIFIER_ID = Magic.id("boots_height");
	private static final Map<UUID, SoulTimer> WATER_SOUL_TIMERS = new HashMap<>();
	private static final Map<UUID, SoulTimer> FIRE_SOUL_TIMERS = new HashMap<>();

	private BootEnhancementEffects() {
	}

	public static void tickPlayer(PlayerEntity player) {
		ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
		boolean nearWater = isNearFluid(player, FluidTags.WATER);
		boolean nearLava = isNearFluid(player, FluidTags.LAVA);
		updateSoulTimer(player, EnhancementType.WATER_SOUL, WATER_SOUL_TIMERS, nearWater);
		updateSoulTimer(player, EnhancementType.FIRE_SOUL, FIRE_SOUL_TIMERS, nearLava);
		updateAttribute(player, EntityAttributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, getSpeedBonus(player));
		updateAttribute(player, EntityAttributes.JUMP_STRENGTH, HEIGHT_MODIFIER_ID, EnhancementSystem.getBootPercentBonus(boots, EnhancementType.HEIGHT));

		if (EnhancementSystem.getSoulSeconds(boots, EnhancementType.FIRE_SOUL) > 0) {
			player.extinguish();
		}

		if (nearLava && canUseFireSoulWalk(player)) {
			stabilizeOnLava(player);
		}
	}

	public static boolean canWalkOnFluid(LivingEntity entity, FluidState fluidState) {
		if (!(entity instanceof PlayerEntity player)) {
			return false;
		}

		ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
		if (fluidState.isIn(FluidTags.WATER)) {
			return canUseSoulWalk(player, boots, EnhancementType.WATER_SOUL, WATER_SOUL_TIMERS);
		}
		if (fluidState.isIn(FluidTags.LAVA)) {
			return canUseSoulWalk(player, boots, EnhancementType.FIRE_SOUL, FIRE_SOUL_TIMERS);
		}
		return false;
	}

	public static boolean canUseFireSoulWalk(PlayerEntity player) {
		return canUseSoulWalk(player, player.getEquippedStack(EquipmentSlot.FEET), EnhancementType.FIRE_SOUL, FIRE_SOUL_TIMERS);
	}

	public static double getSpeedBonus(PlayerEntity player) {
		return EnhancementSystem.getBootPercentBonus(player.getEquippedStack(EquipmentSlot.FEET), EnhancementType.SPEED);
	}

	public static double getFallDamageReduction(PlayerEntity player) {
		ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
		int levels = EnhancementSystem.getLevel(boots, EnhancementType.HEIGHT).totalLevels();
		return Math.min(0.8D, levels * 0.02D);
	}

	public static int getSoulRemainingSeconds(PlayerEntity player, EnhancementType type) {
		ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
		int seconds = EnhancementSystem.getSoulSeconds(boots, type);
		if (seconds <= 0) {
			return 0;
		}

		Map<UUID, SoulTimer> timers = switch (type) {
			case WATER_SOUL -> WATER_SOUL_TIMERS;
			case FIRE_SOUL -> FIRE_SOUL_TIMERS;
			default -> null;
		};
		if (timers == null) {
			return 0;
		}

		SoulTimer timer = timers.get(player.getUuid());
		if (timer == null) {
			return seconds;
		}
		return (int) Math.ceil(timer.remainingMillis / 1000.0D);
	}

	public static boolean blocksFireDamage(PlayerEntity player, DamageSource source) {
		ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
		if (EnhancementSystem.getSoulSeconds(boots, EnhancementType.FIRE_SOUL) <= 0) {
			return false;
		}
		return source.isOf(DamageTypes.IN_FIRE)
				|| source.isOf(DamageTypes.ON_FIRE)
				|| source.isOf(DamageTypes.LAVA)
				|| source.isOf(DamageTypes.HOT_FLOOR)
				|| source.isOf(DamageTypes.CAMPFIRE);
	}

	private static boolean canUseSoulWalk(PlayerEntity player, ItemStack boots, EnhancementType type, Map<UUID, SoulTimer> timers) {
		int seconds = EnhancementSystem.getSoulSeconds(boots, type);
		if (seconds <= 0) {
			return false;
		}

		long nowMillis = System.currentTimeMillis();
		SoulTimer timer = getSoulTimer(player.getUuid(), timers, seconds * 1000L, nowMillis);
		return timer.remainingMillis > 0L;
	}

	private static void updateSoulTimer(PlayerEntity player, EnhancementType type, Map<UUID, SoulTimer> timers, boolean nearFluid) {
		int seconds = EnhancementSystem.getSoulSeconds(player.getEquippedStack(EquipmentSlot.FEET), type);
		long maxMillis = seconds * 1000L;
		SoulTimer timer = getSoulTimer(player.getUuid(), timers, maxMillis, System.currentTimeMillis());
		if (timer == null) {
			return;
		}

		long nowMillis = System.currentTimeMillis();
		long elapsedMillis = Math.max(0L, nowMillis - timer.lastUpdateMillis);
		timer.lastUpdateMillis = nowMillis;
		if (elapsedMillis <= 0L) {
			return;
		}

		if (nearFluid && maxMillis > 0L) {
			timer.remainingMillis = Math.max(0L, timer.remainingMillis - elapsedMillis);
		} else if (!nearFluid && timer.maxMillis > 0L) {
			timer.remainingMillis = Math.min(timer.maxMillis, timer.remainingMillis + elapsedMillis);
		}
	}

	private static SoulTimer getSoulTimer(UUID uuid, Map<UUID, SoulTimer> timers, long currentMaxMillis, long nowMillis) {
		SoulTimer timer = timers.get(uuid);
		if (timer == null) {
			if (currentMaxMillis <= 0L) {
				return null;
			}
			timer = new SoulTimer(currentMaxMillis, currentMaxMillis, nowMillis);
			timers.put(uuid, timer);
			return timer;
		}

		if (currentMaxMillis > 0L) {
			timer.maxMillis = currentMaxMillis;
			timer.remainingMillis = Math.min(timer.remainingMillis, currentMaxMillis);
		}
		return timer;
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

	private static void updateAttribute(PlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}

		EntityAttributeModifier current = instance.getModifier(id);
		if (value <= 0.0D) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}

		if (current != null && Double.compare(current.value(), value) == 0) {
			return;
		}

		instance.removeModifier(id);
		instance.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	private static boolean isNearFluid(PlayerEntity player, TagKey<Fluid> fluidTag) {
		World world = player.getWorld();
		BlockPos feetPos = BlockPos.ofFloored(player.getX(), player.getY() - 0.05D, player.getZ());
		return world.getFluidState(feetPos).isIn(fluidTag) || world.getFluidState(feetPos.down()).isIn(fluidTag);
	}

	private static final class SoulTimer {
		private long remainingMillis;
		private long maxMillis;
		private long lastUpdateMillis;

		private SoulTimer(long remainingMillis, long maxMillis, long lastUpdateMillis) {
			this.remainingMillis = remainingMillis;
			this.maxMillis = maxMillis;
			this.lastUpdateMillis = lastUpdateMillis;
		}
	}
}
