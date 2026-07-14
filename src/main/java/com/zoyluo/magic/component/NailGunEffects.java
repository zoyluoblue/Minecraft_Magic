package com.zoyluo.magic.component;

import com.zoyluo.magic.network.NailGunActionPayload;
import com.zoyluo.magic.network.NailGunStatePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NailGunEffects {
	private static final double WALK_SPEED_SCALE = 4.30D;
	private static final double MIN_PULL_SPEED = 0.08D;
	private static final double ARRIVAL_DISTANCE = 0.75D;
	private static final double BLOCK_FACE_OFFSET = 0.72D;
	private static final Map<UUID, HookState> HOOKS = new HashMap<>();

	private NailGunEffects() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(NailGunActionPayload.ID, (payload, context) -> handleAction(context.player(), payload.action()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPING.register(NailGunEffects::clearAll);
	}

	public static void tickPlayer(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}

		HookState state = HOOKS.get(serverPlayer.getUuid());
		if (state == null) {
			return;
		}

		if (!state.worldKey().equals(world.getRegistryKey()) || !serverPlayer.isAlive() || getRange(serverPlayer) <= 0.0D || world.getBlockState(state.blockPos()).isAir()) {
			cancel(serverPlayer, true);
			return;
		}

		serverPlayer.setNoGravity(true);
		serverPlayer.fallDistance = 0.0F;
		if (state.pulling()) {
			tickPulling(serverPlayer, state);
		} else {
			tickLocked(serverPlayer, state);
		}
	}

	private static void handleAction(ServerPlayerEntity player, NailGunActionPayload.Action action) {
		switch (action) {
			case FIRE_TOGGLE -> {
				if (HOOKS.containsKey(player.getUuid())) {
					cancel(player, true);
				} else {
					fire(player);
				}
			}
			case CANCEL -> cancel(player, true);
			case PULL -> startPulling(player);
		}
	}

	private static void fire(ServerPlayerEntity player) {
		double range = getRange(player);
		if (range <= 0.0D || !(player.getWorld() instanceof ServerWorld world)) {
			sendState(player, NailGunStatePayload.inactive());
			return;
		}

		Vec3d start = player.getEyePos();
		Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
		BlockHitResult hit = world.raycast(new RaycastContext(
				start,
				end,
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player
		));
		if (hit.getType() != HitResult.Type.BLOCK) {
			sendState(player, NailGunStatePayload.inactive());
			return;
		}

		Vec3d faceOffset = Vec3d.of(hit.getSide().getVector()).multiply(BLOCK_FACE_OFFSET);
		Vec3d target = hit.getPos().add(faceOffset);
		HOOKS.put(player.getUuid(), new HookState(
				world.getRegistryKey(),
				hit.getBlockPos().toImmutable(),
				target,
				player.getPos(),
				false,
				player.hasNoGravity()
		));
		player.setNoGravity(true);
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		sendState(player, new NailGunStatePayload(true, target));
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 0.65F, 1.65F);
		world.playSound(null, hit.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.75F, 1.25F);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hit.getPos().x, hit.getPos().y, hit.getPos().z, 8, 0.08D, 0.08D, 0.08D, 0.01D);
	}

	private static void startPulling(ServerPlayerEntity player) {
		HookState state = HOOKS.get(player.getUuid());
		if (state == null || state.pulling()) {
			return;
		}
		HOOKS.put(player.getUuid(), state.withPulling());
		player.setNoGravity(true);
		player.setOnGround(false);
	}

	private static void tickLocked(ServerPlayerEntity player, HookState state) {
		player.setVelocity(Vec3d.ZERO);
		player.setPosition(state.lockPos());
		player.velocityModified = true;
		player.networkHandler.requestTeleport(state.lockPos().x, state.lockPos().y, state.lockPos().z, player.getYaw(), player.getPitch());
	}

	private static void tickPulling(ServerPlayerEntity player, HookState state) {
		Vec3d current = player.getPos().add(0.0D, player.getStandingEyeHeight() * 0.35D, 0.0D);
		Vec3d toTarget = state.target().subtract(current);
		double distance = toTarget.length();
		if (distance <= ARRIVAL_DISTANCE) {
			player.setPosition(state.target());
			player.networkHandler.requestTeleport(state.target().x, state.target().y, state.target().z, player.getYaw(), player.getPitch());
			cancel(player, true);
			return;
		}

		double speed = Math.max(MIN_PULL_SPEED, player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * WALK_SPEED_SCALE);
		Vec3d velocity = toTarget.normalize().multiply(Math.min(speed, distance));
		player.setVelocity(velocity);
		player.velocityModified = true;
		player.setOnGround(false);
	}

	private static void cancel(ServerPlayerEntity player, boolean notify) {
		HookState state = HOOKS.remove(player.getUuid());
		if (state == null) {
			if (notify) {
				sendState(player, NailGunStatePayload.inactive());
			}
			return;
		}

		player.setNoGravity(state.previousNoGravity());
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		if (notify) {
			sendState(player, NailGunStatePayload.inactive());
		}
	}

	private static void clearAll(MinecraftServer server) {
		for (UUID playerUuid : Set.copyOf(HOOKS.keySet())) {
			HookState state = HOOKS.get(playerUuid);
			ServerWorld world = state == null ? null : server.getWorld(state.worldKey());
			if (world != null && world.getEntity(playerUuid) instanceof ServerPlayerEntity player) {
				cancel(player, false);
			} else {
				HOOKS.remove(playerUuid);
			}
		}
		HOOKS.clear();
	}

	private static void sendState(ServerPlayerEntity player, NailGunStatePayload payload) {
		if (ServerPlayNetworking.canSend(player, NailGunStatePayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static double getRange(ServerPlayerEntity player) {
		ItemStack chestplate = player.getEquippedStack(EquipmentSlot.CHEST);
		return EnhancementSystem.getNailGunRange(chestplate);
	}

	private record HookState(RegistryKey<World> worldKey, BlockPos blockPos, Vec3d target, Vec3d lockPos, boolean pulling, boolean previousNoGravity) {
		private HookState withPulling() {
			return new HookState(worldKey, blockPos, target, lockPos, true, previousNoGravity);
		}
	}
}
