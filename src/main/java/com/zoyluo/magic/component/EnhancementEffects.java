package com.zoyluo.magic.component;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EnhancementEffects {
	private static final int TICKS_PER_SECOND = 20;
	private static final int EXPLOSION_FUSE_TICKS = 30;
	private static final float EXPLOSION_POWER = 3.0F;
	private static final Map<EntityIdentity, PendingExplosion> PENDING_EXPLOSIONS = new HashMap<>();
	private static final Map<EntityIdentity, PetrifiedEntity> PETRIFIED_ENTITIES = new HashMap<>();
	private static boolean registered;

	private EnhancementEffects() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		ServerTickEvents.END_SERVER_TICK.register(EnhancementEffects::tickServer);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> clearIdentity(identityOf(entity), entity instanceof PlayerEntity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> clearIdentity(identityOf(world, entity), entity instanceof PlayerEntity));
		ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register(
				(originalEntity, newEntity, origin, destination) -> clearIdentity(identityOf(origin, originalEntity), originalEntity instanceof PlayerEntity)
		);
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				(player, origin, destination) -> clearIdentity(identityOf(origin, player), true)
		);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clearUuid(oldPlayer.getUuid()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ServerThreadTasks.execute(server, () -> clearUuid(handler.player.getUuid()))
		);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clearAll());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
	}

	public static void scheduleExplosion(ServerWorld world, PlayerEntity attacker, LivingEntity target) {
		if (!attacker.isAlive()
				|| !target.isAlive()
				|| attacker.getWorld() != world
				|| target.getWorld() != world) {
			return;
		}

		EntityIdentity targetIdentity = identityOf(world, target);
		EntityIdentity attackerIdentity = identityOf(world, attacker);
		long deadlineTick = (long) world.getServer().getTicks() + EXPLOSION_FUSE_TICKS;
		PendingExplosion previous = PENDING_EXPLOSIONS.putIfAbsent(
				targetIdentity,
				new PendingExplosion(attackerIdentity, deadlineTick)
		);
		if (previous == null) {
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0F, 0.9F);
		}
	}

	public static void schedulePetrify(ServerWorld world, LivingEntity target, int seconds) {
		if (!target.isAlive() || target.getWorld() != world) {
			return;
		}

		long durationTicks = Math.max(1L, seconds) * TICKS_PER_SECOND;
		long deadlineTick = saturatingAdd(world.getServer().getTicks(), durationTicks);
		EntityIdentity identity = identityOf(world, target);
		PETRIFIED_ENTITIES.compute(identity, (ignored, existing) -> {
			if (existing == null || deadlineTick > existing.deadlineTick()) {
				return new PetrifiedEntity(deadlineTick);
			}
			return existing;
		});
	}

	private static void tickServer(MinecraftServer server) {
		long currentTick = server.getTicks();
		tickPetrifiedEntities(server, currentTick);
		tickExplosions(server, currentTick);
	}

	private static void tickPetrifiedEntities(MinecraftServer server, long currentTick) {
		Iterator<Map.Entry<EntityIdentity, PetrifiedEntity>> iterator = PETRIFIED_ENTITIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<EntityIdentity, PetrifiedEntity> entry = iterator.next();
			if (currentTick >= entry.getValue().deadlineTick()) {
				iterator.remove();
				continue;
			}

			LivingEntity living = resolveLiving(server, entry.getKey());
			if (living == null) {
				iterator.remove();
				continue;
			}

			// Keep the vanilla effect alive across the next entity tick. The authoritative deadline
			// remains the map entry above; the short duration only bridges Minecraft's tick order.
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 2, 255, false, false, true));
			living.setVelocity(0.0D, 0.0D, 0.0D);
			living.velocityModified = true;
		}
	}

	private static void tickExplosions(MinecraftServer server, long currentTick) {
		List<ExplosionRequest> dueExplosions = new ArrayList<>();
		Iterator<Map.Entry<EntityIdentity, PendingExplosion>> iterator = PENDING_EXPLOSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<EntityIdentity, PendingExplosion> entry = iterator.next();
			EntityIdentity targetIdentity = entry.getKey();
			PendingExplosion pending = entry.getValue();
			if (resolveLiving(server, targetIdentity) == null || resolvePlayer(server, pending.attacker()) == null) {
				iterator.remove();
				continue;
			}

			if (currentTick >= pending.deadlineTick()) {
				iterator.remove();
				dueExplosions.add(new ExplosionRequest(targetIdentity, pending.attacker()));
			}
		}

		for (ExplosionRequest request : dueExplosions) {
			LivingEntity target = resolveLiving(server, request.target());
			PlayerEntity attacker = resolvePlayer(server, request.attacker());
			if (target == null || attacker == null || target.getWorld() != attacker.getWorld()) {
				continue;
			}

			ServerWorld world = (ServerWorld) target.getWorld();
			world.createExplosion(
					attacker,
					target.getX(),
					target.getY(),
					target.getZ(),
					EXPLOSION_POWER,
					World.ExplosionSourceType.MOB
			);
		}
	}

	private static LivingEntity resolveLiving(MinecraftServer server, EntityIdentity identity) {
		Entity entity = resolveEntity(server, identity);
		return entity instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	private static PlayerEntity resolvePlayer(MinecraftServer server, EntityIdentity identity) {
		Entity entity = resolveEntity(server, identity);
		return entity instanceof PlayerEntity player && player.isAlive() ? player : null;
	}

	private static Entity resolveEntity(MinecraftServer server, EntityIdentity identity) {
		ServerWorld world = server.getWorld(identity.worldKey());
		if (world == null) {
			return null;
		}

		Entity entity = world.getEntity(identity.uuid());
		if (entity == null
				|| entity.isRemoved()
				|| entity.getId() != identity.entityId()
				|| world.getEntityById(identity.entityId()) != entity) {
			return null;
		}
		return entity;
	}

	private static EntityIdentity identityOf(Entity entity) {
		if (!(entity.getWorld() instanceof ServerWorld world)) {
			throw new IllegalArgumentException("Temporary enhancement effects require a server entity");
		}
		return identityOf(world, entity);
	}

	private static EntityIdentity identityOf(ServerWorld world, Entity entity) {
		return new EntityIdentity(world.getRegistryKey(), entity.getUuid(), entity.getId());
	}

	private static void clearIdentity(EntityIdentity identity, boolean canBeAttacker) {
		PETRIFIED_ENTITIES.remove(identity);
		PENDING_EXPLOSIONS.remove(identity);
		if (canBeAttacker) {
			PENDING_EXPLOSIONS.entrySet().removeIf(entry -> entry.getValue().attacker().equals(identity));
		}
	}

	private static void clearUuid(UUID uuid) {
		PETRIFIED_ENTITIES.keySet().removeIf(identity -> identity.uuid().equals(uuid));
		PENDING_EXPLOSIONS.entrySet().removeIf(entry ->
				entry.getKey().uuid().equals(uuid) || entry.getValue().attacker().uuid().equals(uuid)
		);
	}

	private static void clearAll() {
		PETRIFIED_ENTITIES.clear();
		PENDING_EXPLOSIONS.clear();
	}

	private static long saturatingAdd(long left, long right) {
		if (right > 0L && left > Long.MAX_VALUE - right) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	private record EntityIdentity(RegistryKey<World> worldKey, UUID uuid, int entityId) {
	}

	private record PendingExplosion(EntityIdentity attacker, long deadlineTick) {
	}

	private record PetrifiedEntity(long deadlineTick) {
	}

	private record ExplosionRequest(EntityIdentity target, EntityIdentity attacker) {
	}
}
