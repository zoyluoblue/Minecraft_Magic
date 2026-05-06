package com.zoyluo.magic.component;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class EnhancementEffects {
	private static final int EXPLOSION_FUSE_TICKS = 30;
	private static final float EXPLOSION_POWER = 3.0F;
	private static final Map<UUID, PendingExplosion> PENDING_EXPLOSIONS = new HashMap<>();
	private static final Map<UUID, PetrifiedEntity> PETRIFIED_ENTITIES = new HashMap<>();

	private EnhancementEffects() {
	}

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(EnhancementEffects::tickWorld);
	}

	public static void scheduleExplosion(ServerWorld world, LivingEntity target) {
		PENDING_EXPLOSIONS.putIfAbsent(target.getUuid(), new PendingExplosion(world.getRegistryKey(), EXPLOSION_FUSE_TICKS));
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0F, 0.9F);
	}

	public static void schedulePetrify(ServerWorld world, LivingEntity target, int seconds) {
		long deadlineMillis = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
		PETRIFIED_ENTITIES.put(target.getUuid(), new PetrifiedEntity(world.getRegistryKey(), deadlineMillis));
	}

	private static void tickWorld(ServerWorld world) {
		tickPetrifiedEntities(world);
		tickExplosions(world);
	}

	private static void tickPetrifiedEntities(ServerWorld world) {
		long nowMillis = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, PetrifiedEntity>> iterator = PETRIFIED_ENTITIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PetrifiedEntity> entry = iterator.next();
			PetrifiedEntity petrified = entry.getValue();
			if (!petrified.worldKey().equals(world.getRegistryKey())) {
				continue;
			}

			Entity entity = world.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || nowMillis >= petrified.deadlineMillis()) {
				iterator.remove();
				continue;
			}

			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 255, false, false, true));
			living.setVelocity(0.0D, 0.0D, 0.0D);
			living.velocityModified = true;
		}
	}

	private static void tickExplosions(ServerWorld world) {
		Iterator<Map.Entry<UUID, PendingExplosion>> iterator = PENDING_EXPLOSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PendingExplosion> entry = iterator.next();
			PendingExplosion pending = entry.getValue();
			if (!pending.worldKey().equals(world.getRegistryKey())) {
				continue;
			}

			Entity entity = world.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				iterator.remove();
				continue;
			}

			int ticksLeft = pending.ticksLeft() - 1;
			if (ticksLeft > 0) {
				entry.setValue(new PendingExplosion(pending.worldKey(), ticksLeft));
				continue;
			}

			world.createExplosion(living, living.getX(), living.getY(), living.getZ(), EXPLOSION_POWER, World.ExplosionSourceType.MOB);
			if (living.isAlive()) {
				living.kill(world);
			}
			iterator.remove();
		}
	}

	private record PendingExplosion(RegistryKey<World> worldKey, int ticksLeft) {
	}

	private record PetrifiedEntity(RegistryKey<World> worldKey, long deadlineMillis) {
	}
}
