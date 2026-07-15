package com.zoyluo.magic.component;

import com.zoyluo.magic.network.NailGunActionPayload;
import com.zoyluo.magic.network.NailGunCommandPayload;
import com.zoyluo.magic.network.NailGunRuntimePayload;
import com.zoyluo.magic.network.NailGunStatePayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NailGunEffects {
	private static final double BLOCK_FACE_OFFSET = 0.12D;
	private static final double ARRIVAL_DISTANCE = 1.0D;
	private static final double PULL_ACCELERATION = 0.12D;
	private static final double MIN_PULL_SPEED = 0.45D;
	private static final double MAX_PULL_SPEED = 1.35D;
	private static final double STALL_PROGRESS_EPSILON = 0.025D;
	private static final int MAX_STALLED_TICKS = 5;
	private static final int MAX_HOOK_LIFETIME_TICKS = 600;
	private static final int LEGACY_WARNING_COOLDOWN_TICKS = 100;
	private static final int PROTOCOL_REJECTION_COOLDOWN_TICKS = 100;

	private static final Map<UUID, HookSession> HOOKS = new HashMap<>();
	private static final Map<UUID, NailGunCommandGate> COMMAND_GATES = new HashMap<>();
	private static final Map<UUID, Long> LEGACY_WARNING_TICKS = new HashMap<>();
	private static final Map<UUID, Long> PROTOCOL_REJECTION_TICKS = new HashMap<>();
	private static long nextStateVersion = 1L;
	private static int runtimeSendAttemptsForTest;

	private NailGunEffects() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(NailGunActionPayload.ID, (payload, context) -> handleLegacyAction(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(NailGunCommandPayload.ID, (payload, context) -> handleCommand(context.player(), payload));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendOwnerSnapshot(handler.player, 0L));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ServerThreadTasks.execute(server, () -> {
					release(handler.player, false, true, acknowledgedSequence(handler.player));
					COMMAND_GATES.remove(handler.player.getUuid());
					LEGACY_WARNING_TICKS.remove(handler.player.getUuid());
					PROTOCOL_REJECTION_TICKS.remove(handler.player.getUuid());
				})
		);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				release(player, true, true, acknowledgedSequence(player));
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				release(player, true, true, acknowledgedSequence(player))
		);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			release(oldPlayer, false, true, acknowledgedSequence(oldPlayer));
			COMMAND_GATES.remove(oldPlayer.getUuid());
			sendOwnerSnapshot(newPlayer, 0L);
		});
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previousStack, currentStack) -> {
			if (slot == EquipmentSlot.CHEST && entity instanceof ServerPlayerEntity player && getRange(player) <= 0.0D) {
				release(player, true, true, acknowledgedSequence(player));
			}
		});
		EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
			if (trackedEntity instanceof ServerPlayerEntity owner) {
				sendSnapshotTo(player, owner, currentPhase(owner.getUuid()), currentAnchor(owner.getUuid()), 0L, nextVersion());
			}
		});
		EntityTrackingEvents.STOP_TRACKING.register((trackedEntity, player) -> {
			if (trackedEntity instanceof ServerPlayerEntity owner) {
				sendRuntime(player, NailGunRuntimePayload.current(
						owner.getUuid(),
						nextVersion(),
						0L,
						NailGunRuntimePayload.Phase.RELEASED,
						Vec3d.ZERO
				));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(NailGunEffects::clearAll);
	}

	public static void tickPlayer(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(player.getWorld() instanceof ServerWorld world)) {
			return;
		}

		HookSession state = HOOKS.get(serverPlayer.getUuid());
		if (state == null) {
			return;
		}

		long serverTick = serverTick(serverPlayer);
		if (!isSessionValid(serverPlayer, world, state, serverTick)) {
			release(serverPlayer, true, true, acknowledgedSequence(serverPlayer));
			return;
		}

		if (state.phase() == NailGunRuntimePayload.Phase.PULLING) {
			tickPulling(serverPlayer, world, state);
		}
	}

	static void handleCommand(ServerPlayerEntity player, NailGunCommandPayload payload) {
		long tick = serverTick(player);
		NailGunCommandGate gate = COMMAND_GATES.computeIfAbsent(player.getUuid(), uuid -> new NailGunCommandGate());
		if (payload.protocolVersion() != NailGunCommandPayload.PROTOCOL_VERSION) {
			if (allowPeriodicReply(PROTOCOL_REJECTION_TICKS, player.getUuid(), tick, PROTOCOL_REJECTION_COOLDOWN_TICKS)) {
				player.sendMessage(Text.translatable("message.magic.nail_gun.protocol_mismatch"), true);
				sendOwnerSnapshot(player, gate.lastObservedSequence());
			}
			return;
		}

		NailGunCommandGate.Decision decision = gate.evaluate(payload.requestSequence(), payload.command(), tick);
		if (!decision.shouldProcess()) {
			if (decision.shouldReply()) {
				sendOwnerSnapshot(player, decision.acknowledgedSequence());
			}
			return;
		}

		switch (payload.command()) {
			case FIRE -> fire(player, decision.acknowledgedSequence());
			case PULL -> startPulling(player, decision.acknowledgedSequence());
			case RELEASE -> {
				if (HOOKS.containsKey(player.getUuid())) {
					release(player, true, true, decision.acknowledgedSequence());
				} else if (decision.shouldReply()) {
					sendOwnerSnapshot(player, decision.acknowledgedSequence());
				}
			}
		}
	}

	static void handleLegacyAction(ServerPlayerEntity player) {
		long tick = serverTick(player);
		if (allowPeriodicReply(LEGACY_WARNING_TICKS, player.getUuid(), tick, LEGACY_WARNING_COOLDOWN_TICKS)) {
			player.sendMessage(Text.translatableWithFallback(
					"message.magic.nail_gun.protocol_upgrade",
					"Nail Gun requires the matching Magic client version."
			), true);
			if (ServerPlayNetworking.canSend(player, NailGunStatePayload.ID)) {
				ServerPlayNetworking.send(player, NailGunStatePayload.inactive());
			}
		}
	}

	private static void fire(ServerPlayerEntity player, long acknowledgedRequestSequence) {
		HookSession existing = HOOKS.get(player.getUuid());
		if (existing != null) {
			sendOwnerSnapshot(player, acknowledgedRequestSequence);
			return;
		}

		double range = getRange(player);
		if (range <= 0.0D || !player.isAlive() || player.isSpectator() || !(player.getWorld() instanceof ServerWorld world)) {
			sendOwnerSnapshot(player, acknowledgedRequestSequence);
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
		if (hit.getType() != HitResult.Type.BLOCK || !isValidAnchor(world, hit.getBlockPos())) {
			sendOwnerSnapshot(player, acknowledgedRequestSequence);
			return;
		}

		Direction face = hit.getSide();
		Vec3d anchor = hit.getPos().add(Vec3d.of(face.getVector()).multiply(BLOCK_FACE_OFFSET));
		HookSession session = new HookSession(
				world.getRegistryKey(),
				hit.getBlockPos().toImmutable(),
				face,
				anchor,
				NailGunRuntimePayload.Phase.ATTACHED,
				serverTick(player),
				Double.POSITIVE_INFINITY,
				0
		);
		HOOKS.put(player.getUuid(), session);
		broadcast(player, session.phase(), session.anchor(), acknowledgedRequestSequence, true);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 0.65F, 1.65F);
		world.playSound(null, hit.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.75F, 1.25F);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hit.getPos().x, hit.getPos().y, hit.getPos().z, 8, 0.08D, 0.08D, 0.08D, 0.01D);
	}

	private static void startPulling(ServerPlayerEntity player, long acknowledgedRequestSequence) {
		HookSession state = HOOKS.get(player.getUuid());
		if (state == null) {
			sendOwnerSnapshot(player, acknowledgedRequestSequence);
			return;
		}
		if (state.phase() == NailGunRuntimePayload.Phase.PULLING) {
			sendOwnerSnapshot(player, acknowledgedRequestSequence);
			return;
		}

		double distance = pullOrigin(player).distanceTo(state.anchor());
		HookSession pulling = state.withPulling(distance);
		HOOKS.put(player.getUuid(), pulling);
		broadcast(player, pulling.phase(), pulling.anchor(), acknowledgedRequestSequence, true);
	}

	private static void tickPulling(ServerPlayerEntity player, ServerWorld world, HookSession state) {
		Vec3d origin = pullOrigin(player);
		Vec3d toAnchor = state.anchor().subtract(origin);
		double distance = toAnchor.length();
		if (distance <= ARRIVAL_DISTANCE) {
			release(player, true, true, acknowledgedSequence(player));
			return;
		}

		BlockHitResult obstruction = world.raycast(new RaycastContext(
				origin,
				state.anchor(),
				RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE,
				player
		));
		if (obstruction.getType() == HitResult.Type.BLOCK && !obstruction.getBlockPos().equals(state.anchorBlock())) {
			release(player, true, true, acknowledgedSequence(player));
			return;
		}

		Vec3d velocity = player.getVelocity().multiply(0.90D).add(toAnchor.normalize().multiply(PULL_ACCELERATION));
		double speedCap = Math.min(MAX_PULL_SPEED, Math.max(MIN_PULL_SPEED, player.getMovementSpeed() * 4.3D));
		if (velocity.lengthSquared() > speedCap * speedCap) {
			velocity = velocity.normalize().multiply(speedCap);
		}
		Vec3d collisionSafeVelocity = Entity.adjustMovementForCollisions(
				player,
				velocity,
				player.getBoundingBox(),
				world,
				List.of()
		);

		boolean progressed = distance < state.lastDistance() - STALL_PROGRESS_EPSILON;
		int stalledTicks = progressed && collisionSafeVelocity.lengthSquared() > 1.0E-5D ? 0 : state.stalledTicks() + 1;
		if (stalledTicks >= MAX_STALLED_TICKS) {
			release(player, true, true, acknowledgedSequence(player));
			return;
		}

		player.setVelocity(collisionSafeVelocity);
		player.velocityModified = true;
		player.setOnGround(false);
		player.fallDistance = 0.0F;
		HOOKS.put(player.getUuid(), state.withProgress(distance, stalledTicks));
	}

	private static boolean isSessionValid(ServerPlayerEntity player, ServerWorld world, HookSession state, long tick) {
		if (!state.worldKey().equals(world.getRegistryKey()) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (tick < state.createdTick() || tick - state.createdTick() > MAX_HOOK_LIFETIME_TICKS) {
			return false;
		}
		double range = getRange(player);
		if (range <= 0.0D || player.getEyePos().squaredDistanceTo(state.anchor()) > (range + 4.0D) * (range + 4.0D)) {
			return false;
		}
		return isValidAnchor(world, state.anchorBlock());
	}

	private static boolean isValidAnchor(ServerWorld world, BlockPos pos) {
		if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
			return false;
		}
		BlockState state = world.getBlockState(pos);
		return !state.isAir() && !state.getCollisionShape(world, pos).isEmpty();
	}

	private static void release(ServerPlayerEntity player, boolean notifyOwner, boolean notifyTracking, long acknowledgedRequestSequence) {
		HookSession removed = HOOKS.remove(player.getUuid());
		if (removed == null) {
			if (notifyOwner) {
				sendOwnerSnapshot(player, acknowledgedRequestSequence);
			}
			return;
		}
		broadcast(player, NailGunRuntimePayload.Phase.RELEASED, Vec3d.ZERO, acknowledgedRequestSequence, notifyOwner, notifyTracking);
	}

	private static void clearAll(MinecraftServer server) {
		HOOKS.clear();
		COMMAND_GATES.clear();
		LEGACY_WARNING_TICKS.clear();
		PROTOCOL_REJECTION_TICKS.clear();
		nextStateVersion = 1L;
	}

	private static void sendOwnerSnapshot(ServerPlayerEntity owner, long acknowledgedRequestSequence) {
		HookSession state = HOOKS.get(owner.getUuid());
		sendSnapshotTo(owner, owner, state == null ? NailGunRuntimePayload.Phase.RELEASED : state.phase(), state == null ? Vec3d.ZERO : state.anchor(), acknowledgedRequestSequence, nextVersion());
	}

	private static void sendSnapshotTo(
			ServerPlayerEntity recipient,
			ServerPlayerEntity owner,
			NailGunRuntimePayload.Phase phase,
			Vec3d anchor,
			long acknowledgedRequestSequence,
			long stateVersion
	) {
		long ack = recipient.getUuid().equals(owner.getUuid()) ? acknowledgedRequestSequence : 0L;
		sendRuntime(recipient, NailGunRuntimePayload.current(owner.getUuid(), stateVersion, ack, phase, anchor));
	}

	private static void broadcast(ServerPlayerEntity owner, NailGunRuntimePayload.Phase phase, Vec3d anchor, long acknowledgedRequestSequence, boolean notifyOwner) {
		broadcast(owner, phase, anchor, acknowledgedRequestSequence, notifyOwner, true);
	}

	private static void broadcast(
			ServerPlayerEntity owner,
			NailGunRuntimePayload.Phase phase,
			Vec3d anchor,
			long acknowledgedRequestSequence,
			boolean notifyOwner,
			boolean notifyTracking
	) {
		long version = nextVersion();
		Set<UUID> sent = new HashSet<>();
		if (notifyOwner) {
			sendSnapshotTo(owner, owner, phase, anchor, acknowledgedRequestSequence, version);
			sent.add(owner.getUuid());
		}
		if (notifyTracking) {
			for (ServerPlayerEntity recipient : PlayerLookup.tracking(owner)) {
				if (sent.add(recipient.getUuid())) {
					sendSnapshotTo(recipient, owner, phase, anchor, 0L, version);
				}
			}
		}
	}

	private static void sendRuntime(ServerPlayerEntity recipient, NailGunRuntimePayload payload) {
		runtimeSendAttemptsForTest++;
		if (ServerPlayNetworking.canSend(recipient, NailGunRuntimePayload.ID)) {
			ServerPlayNetworking.send(recipient, payload);
		}
	}

	private static NailGunRuntimePayload.Phase currentPhase(UUID playerUuid) {
		HookSession state = HOOKS.get(playerUuid);
		return state == null ? NailGunRuntimePayload.Phase.RELEASED : state.phase();
	}

	private static Vec3d currentAnchor(UUID playerUuid) {
		HookSession state = HOOKS.get(playerUuid);
		return state == null ? Vec3d.ZERO : state.anchor();
	}

	private static long acknowledgedSequence(ServerPlayerEntity player) {
		NailGunCommandGate gate = COMMAND_GATES.get(player.getUuid());
		return gate == null ? 0L : gate.lastObservedSequence();
	}

	private static long serverTick(ServerPlayerEntity player) {
		return player.getServerWorld().getServer().getTicks();
	}

	private static boolean allowPeriodicReply(Map<UUID, Long> replies, UUID playerUuid, long tick, int cooldownTicks) {
		long previous = replies.getOrDefault(playerUuid, Long.MIN_VALUE);
		if (previous != Long.MIN_VALUE && tick >= previous && tick - previous < cooldownTicks) {
			return false;
		}
		replies.put(playerUuid, tick);
		return true;
	}

	private static long nextVersion() {
		long version = nextStateVersion++;
		if (nextStateVersion <= 0L) {
			nextStateVersion = 1L;
		}
		return version;
	}

	private static Vec3d pullOrigin(ServerPlayerEntity player) {
		return player.getPos().add(0.0D, player.getStandingEyeHeight() * 0.35D, 0.0D);
	}

	private static double getRange(ServerPlayerEntity player) {
		ItemStack chestplate = player.getEquippedStack(EquipmentSlot.CHEST);
		return EnhancementSystem.getNailGunRange(chestplate);
	}

	static NailGunRuntimePayload.Phase phaseForTest(UUID playerUuid) {
		return currentPhase(playerUuid);
	}

	static int activeHookCountForTest() {
		return HOOKS.size();
	}

	static int runtimeSendAttemptsForTest() {
		return runtimeSendAttemptsForTest;
	}

	static long acknowledgedSequenceForTest(ServerPlayerEntity player) {
		return acknowledgedSequence(player);
	}

	static void clearForTest() {
		HOOKS.clear();
		COMMAND_GATES.clear();
		LEGACY_WARNING_TICKS.clear();
		PROTOCOL_REJECTION_TICKS.clear();
		nextStateVersion = 1L;
		runtimeSendAttemptsForTest = 0;
	}

	private record HookSession(
			RegistryKey<World> worldKey,
			BlockPos anchorBlock,
			Direction anchorFace,
			Vec3d anchor,
			NailGunRuntimePayload.Phase phase,
			long createdTick,
			double lastDistance,
			int stalledTicks
	) {
		private HookSession withPulling(double distance) {
			return new HookSession(worldKey, anchorBlock, anchorFace, anchor, NailGunRuntimePayload.Phase.PULLING, createdTick, distance, 0);
		}

		private HookSession withProgress(double distance, int stalledTicks) {
			return new HookSession(worldKey, anchorBlock, anchorFace, anchor, phase, createdTick, distance, stalledTicks);
		}
	}
}
