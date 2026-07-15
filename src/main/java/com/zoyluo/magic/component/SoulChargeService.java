package com.zoyluo.magic.component;

import com.zoyluo.magic.network.SoulChargeSyncPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SoulChargeService {
	private static final long MILLIS_PER_SECOND = 1_000L;
	private static final long NANOS_PER_MILLI = 1_000_000L;
	private static final long CHECKPOINT_INTERVAL_NANOS = MILLIS_PER_SECOND * NANOS_PER_MILLI;
	private static final long FULL_SYNC_INTERVAL_NANOS = 10L * CHECKPOINT_INTERVAL_NANOS;
	private static final Map<UUID, RuntimeState> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> SEQUENCES = new HashMap<>();
	private static volatile ClientPrediction clientPrediction = ClientPrediction.empty();
	private static boolean registered;

	private SoulChargeService() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(SoulChargeService::tickServer);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> startSession(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ServerThreadTasks.execute(server, () -> endSession(handler.player))
		);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				flushCurrent(player, System.nanoTime(), System.currentTimeMillis());
				sendEmptySnapshot(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			flushCurrent(oldPlayer, System.nanoTime(), System.currentTimeMillis());
			activate(newPlayer, System.nanoTime(), System.currentTimeMillis(), true);
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> restart(player));
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> checkpointAll(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(SoulChargeService::stopServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			SEQUENCES.clear();
		});
	}

	public static boolean hasCharge(PlayerEntity player, EnhancementType type) {
		return getRemainingMillis(player, type) > 0L;
	}

	/** Called from PlayerManager.remove before vanilla persists the disconnecting player. */
	public static void flushBeforePlayerRemoval(ServerPlayerEntity player) {
		endSession(player);
	}

	/**
	 * Flushes an equipped soul item before vanilla can split or replace its ItemStack object.
	 * This keeps the moved stack authoritative instead of leaving sub-checkpoint progress behind
	 * on the emptied source object.
	 */
	public static EquipmentMutation beforeEquipmentMutation(PlayerEntity player, ItemStack stack) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || stack.isEmpty()) {
			return null;
		}
		RuntimeState state = ACTIVE.get(serverPlayer.getUuid());
		if (state == null || state.stack != stack) {
			return null;
		}
		deactivate(serverPlayer, System.nanoTime(), System.currentTimeMillis());
		return new EquipmentMutation(serverPlayer.getUuid(), state);
	}

	/** Restores the exact pre-flush runtime state when a vanilla mutation ultimately made no change. */
	public static void restoreAfterFailedEquipmentMutation(
			PlayerEntity player,
			ItemStack stack,
			EquipmentMutation mutation
	) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)
				|| mutation == null
				|| stack.isEmpty()
				|| !mutation.ownerUuid.equals(serverPlayer.getUuid())
				|| mutation.state.stack != stack
				|| ACTIVE.containsKey(serverPlayer.getUuid())
				|| serverPlayer.getEquippedStack(EquipmentSlot.FEET) != stack) {
			return;
		}
		long nowNanos = System.nanoTime();
		ACTIVE.put(serverPlayer.getUuid(), mutation.state);
		checkpoint(mutation.state, SoulChargeData.MODE_ACTIVE, nowNanos, System.currentTimeMillis());
	}

	public static void updateClientPrediction(
			UUID ownerUuid,
			int mask,
			long waterRemainingMillis,
			long waterMaxMillis,
			long fireRemainingMillis,
			long fireMaxMillis
	) {
		clientPrediction = new ClientPrediction(
				ownerUuid,
				mask,
				waterRemainingMillis,
				waterMaxMillis,
				fireRemainingMillis,
				fireMaxMillis
		);
	}

	public static void clearClientPrediction() {
		clientPrediction = ClientPrediction.empty();
	}

	public static long getRemainingMillis(PlayerEntity player, EnhancementType type) {
		if (type != EnhancementType.WATER_SOUL && type != EnhancementType.FIRE_SOUL) {
			return 0L;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			RuntimeState state = ensureCurrent(serverPlayer, System.nanoTime(), System.currentTimeMillis());
			if (state.unavailable) {
				return 0L;
			}
			return type == EnhancementType.WATER_SOUL ? state.waterRemainingMillis : state.fireRemainingMillis;
		}

		ClientPrediction prediction = clientPrediction;
		if (!player.getUuid().equals(prediction.ownerUuid)) {
			return 0L;
		}
		ItemStack boots = player.getEquippedStack(EquipmentSlot.FEET);
		long expectedMaxMillis = maxMillis(boots, type);
		if (type == EnhancementType.WATER_SOUL) {
			return (prediction.mask & SoulChargeSyncPayload.WATER_SOUL_MASK) != 0
					&& prediction.waterMaxMillis == expectedMaxMillis
					? prediction.waterRemainingMillis : 0L;
		}
		return (prediction.mask & SoulChargeSyncPayload.FIRE_SOUL_MASK) != 0
				&& prediction.fireMaxMillis == expectedMaxMillis
				? prediction.fireRemainingMillis : 0L;
	}

	private static void tickServer(MinecraftServer server) {
		long nowNanos = System.nanoTime();
		long nowEpochMillis = System.currentTimeMillis();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isAlive()) {
				tickPlayer(player, nowNanos, nowEpochMillis);
			}
		}
	}

	private static void tickPlayer(ServerPlayerEntity player, long nowNanos, long nowEpochMillis) {
		RuntimeState state = ensureCurrent(player, nowNanos, nowEpochMillis);
		boolean nearWater = BootEnhancementEffects.isNearWater(player);
		boolean nearLava = BootEnhancementEffects.isNearLava(player);
		boolean boundaryReached = settle(state, nowNanos, nearWater, nearLava);

		if (!state.unavailable && state.fireRemainingMillis > 0L) {
			BootEnhancementEffects.applyFireSoulEffects(player, nearLava);
		}

		if (boundaryReached) {
			checkpoint(state, SoulChargeData.MODE_ACTIVE, nowNanos, nowEpochMillis);
			sendDelta(player, state);
			return;
		}

		if (elapsedAtLeast(nowNanos, state.lastCheckpointNanos, CHECKPOINT_INTERVAL_NANOS)) {
			if (state.dirty) {
				checkpoint(state, SoulChargeData.MODE_ACTIVE, nowNanos, nowEpochMillis);
				sendDelta(player, state);
			} else {
				state.lastCheckpointNanos = nowNanos;
			}
		}
		if (elapsedAtLeast(nowNanos, state.lastFullSyncNanos, FULL_SYNC_INTERVAL_NANOS)) {
			sendSnapshot(player, state, nowNanos);
		}
	}

	private static RuntimeState ensureCurrent(ServerPlayerEntity player, long nowNanos, long nowEpochMillis) {
		return ensureCurrent(player, nowNanos, nowEpochMillis, true);
	}

	private static RuntimeState ensureCurrent(ServerPlayerEntity player, long nowNanos, long nowEpochMillis, boolean synchronize) {
		RuntimeState state = ACTIVE.get(player.getUuid());
		ItemStack equippedBoots = player.getEquippedStack(EquipmentSlot.FEET);
		if (state == null) {
			return activate(player, nowNanos, nowEpochMillis, synchronize);
		}
		if (state.stack != equippedBoots) {
			deactivate(player, nowNanos, nowEpochMillis);
			return activate(player, nowNanos, nowEpochMillis, synchronize);
		}
		if (refreshCapacities(state)) {
			checkpoint(state, SoulChargeData.MODE_ACTIVE, nowNanos, nowEpochMillis);
			if (synchronize) {
				sendSnapshot(player, state, nowNanos);
			}
		}
		return state;
	}

	private static RuntimeState activate(ServerPlayerEntity player, long nowNanos, long nowEpochMillis, boolean synchronize) {
		ItemStack stack = player.getEquippedStack(EquipmentSlot.FEET);
		long waterMaxMillis = maxMillis(stack, EnhancementType.WATER_SOUL);
		long fireMaxMillis = maxMillis(stack, EnhancementType.FIRE_SOUL);
		SoulChargeData.Snapshot snapshot = SoulChargeData.read(stack, waterMaxMillis, fireMaxMillis, nowEpochMillis);
		boolean unavailable = !snapshot.writable();
		RuntimeState state = new RuntimeState(
				stack,
				unavailable ? 0L : snapshot.waterRemainingMillis(),
				waterMaxMillis,
				unavailable ? 0L : snapshot.fireRemainingMillis(),
				fireMaxMillis,
				nowNanos,
				snapshot.writable(),
				waterMaxMillis > 0L || fireMaxMillis > 0L || snapshot.status() != SoulChargeData.Status.MISSING,
				unavailable
		);
		ACTIVE.put(player.getUuid(), state);
		checkpoint(state, SoulChargeData.MODE_ACTIVE, nowNanos, nowEpochMillis);
		if (synchronize) {
			sendSnapshot(player, state, nowNanos);
		}
		return state;
	}

	private static void deactivate(ServerPlayerEntity player, long nowNanos, long nowEpochMillis) {
		RuntimeState state = ACTIVE.remove(player.getUuid());
		if (state == null) {
			return;
		}
		refreshCapacities(state);
		settle(
				state,
				nowNanos,
				BootEnhancementEffects.isNearWater(player),
				BootEnhancementEffects.isNearLava(player)
		);
		checkpoint(state, SoulChargeData.MODE_RECOVERING, nowNanos, nowEpochMillis);
	}

	private static boolean refreshCapacities(RuntimeState state) {
		long waterMaxMillis = maxMillis(state.stack, EnhancementType.WATER_SOUL);
		long fireMaxMillis = maxMillis(state.stack, EnhancementType.FIRE_SOUL);
		if (waterMaxMillis == state.waterMaxMillis && fireMaxMillis == state.fireMaxMillis) {
			return false;
		}

		state.waterRemainingMillis = resize(state.waterRemainingMillis, state.waterMaxMillis, waterMaxMillis, state.unavailable);
		state.fireRemainingMillis = resize(state.fireRemainingMillis, state.fireMaxMillis, fireMaxMillis, state.unavailable);
		state.waterMaxMillis = waterMaxMillis;
		state.fireMaxMillis = fireMaxMillis;
		state.persist |= waterMaxMillis > 0L || fireMaxMillis > 0L;
		state.dirty = true;
		return true;
	}

	private static boolean settle(RuntimeState state, long nowNanos, boolean drainWater, boolean drainFire) {
		if (nowNanos <= state.lastUpdateNanos) {
			return false;
		}
		long elapsedMillis = (nowNanos - state.lastUpdateNanos) / NANOS_PER_MILLI;
		if (elapsedMillis <= 0L) {
			return false;
		}
		state.lastUpdateNanos += elapsedMillis * NANOS_PER_MILLI;
		if (state.unavailable) {
			return false;
		}

		long previousWater = state.waterRemainingMillis;
		long previousFire = state.fireRemainingMillis;
		state.waterRemainingMillis = update(previousWater, state.waterMaxMillis, elapsedMillis, drainWater);
		state.fireRemainingMillis = update(previousFire, state.fireMaxMillis, elapsedMillis, drainFire);
		state.dirty |= previousWater != state.waterRemainingMillis || previousFire != state.fireRemainingMillis;
		return reachedBoundary(previousWater, state.waterRemainingMillis, state.waterMaxMillis)
				|| reachedBoundary(previousFire, state.fireRemainingMillis, state.fireMaxMillis);
	}

	private static void checkpoint(RuntimeState state, byte mode, long nowNanos, long nowEpochMillis) {
		if (state.writable && state.persist) {
			boolean written = SoulChargeData.write(
					state.stack,
					mode,
					nowEpochMillis,
					state.waterRemainingMillis,
					state.fireRemainingMillis
			);
			if (!written) {
				state.writable = false;
				state.unavailable = true;
				state.waterRemainingMillis = 0L;
				state.fireRemainingMillis = 0L;
				state.dirty = true;
			}
		}
		state.lastCheckpointNanos = nowNanos;
	}

	private static void sendSnapshot(ServerPlayerEntity player, RuntimeState state, long nowNanos) {
		state.lastFullSyncNanos = nowNanos;
		sendState(player, state, SoulChargeSyncPayload.MODE_SNAPSHOT);
	}

	private static void sendDelta(ServerPlayerEntity player, RuntimeState state) {
		sendState(player, state, SoulChargeSyncPayload.MODE_DELTA);
	}

	private static void sendState(ServerPlayerEntity player, RuntimeState state, int mode) {
		state.dirty = false;
		if (!ServerPlayNetworking.canSend(player, SoulChargeSyncPayload.ID)) {
			return;
		}

		int mask = 0;
		if (state.waterMaxMillis > 0L) {
			mask |= SoulChargeSyncPayload.WATER_SOUL_MASK;
		}
		if (state.fireMaxMillis > 0L) {
			mask |= SoulChargeSyncPayload.FIRE_SOUL_MASK;
		}
		long sequence = nextSequence(player.getUuid());
		ServerPlayNetworking.send(player, new SoulChargeSyncPayload(
				SoulChargeSyncPayload.CURRENT_VERSION,
				mode,
				sequence,
				mask,
				state.waterRemainingMillis,
				state.waterMaxMillis,
				state.fireRemainingMillis,
				state.fireMaxMillis
		));
	}

	private static void sendEmptySnapshot(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, SoulChargeSyncPayload.ID)) {
			return;
		}
		ServerPlayNetworking.send(player, new SoulChargeSyncPayload(
				SoulChargeSyncPayload.CURRENT_VERSION,
				SoulChargeSyncPayload.MODE_SNAPSHOT,
				nextSequence(player.getUuid()),
				0,
				0L,
				0L,
				0L,
				0L
		));
	}

	private static long nextSequence(UUID uuid) {
		long next = SEQUENCES.getOrDefault(uuid, -1L) + 1L;
		SEQUENCES.put(uuid, next);
		return next;
	}

	private static void startSession(ServerPlayerEntity player) {
		long nowNanos = System.nanoTime();
		long nowEpochMillis = System.currentTimeMillis();
		deactivate(player, nowNanos, nowEpochMillis);
		SEQUENCES.remove(player.getUuid());
		activate(player, nowNanos, nowEpochMillis, true);
	}

	private static void endSession(ServerPlayerEntity player) {
		flushCurrent(player, System.nanoTime(), System.currentTimeMillis());
		SEQUENCES.remove(player.getUuid());
	}

	private static void restart(ServerPlayerEntity player) {
		long nowNanos = System.nanoTime();
		long nowEpochMillis = System.currentTimeMillis();
		flushCurrent(player, nowNanos, nowEpochMillis);
		activate(player, nowNanos, nowEpochMillis, true);
	}

	private static void flushCurrent(ServerPlayerEntity player, long nowNanos, long nowEpochMillis) {
		if (!ACTIVE.containsKey(player.getUuid())) {
			return;
		}
		ensureCurrent(player, nowNanos, nowEpochMillis, false);
		deactivate(player, nowNanos, nowEpochMillis);
	}

	private static void checkpointAll(MinecraftServer server) {
		long nowNanos = System.nanoTime();
		long nowEpochMillis = System.currentTimeMillis();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!ACTIVE.containsKey(player.getUuid())) {
				continue;
			}
			RuntimeState state = ensureCurrent(player, nowNanos, nowEpochMillis, false);
			settle(state, nowNanos, BootEnhancementEffects.isNearWater(player), BootEnhancementEffects.isNearLava(player));
			checkpoint(state, SoulChargeData.MODE_ACTIVE, nowNanos, nowEpochMillis);
		}
	}

	private static void stopServer(MinecraftServer server) {
		long nowNanos = System.nanoTime();
		long nowEpochMillis = System.currentTimeMillis();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			flushCurrent(player, nowNanos, nowEpochMillis);
		}
		ACTIVE.clear();
		SEQUENCES.clear();
	}

	private static long maxMillis(ItemStack stack, EnhancementType type) {
		return EnhancementSystem.getSoulSeconds(stack, type) * MILLIS_PER_SECOND;
	}

	private static long resize(long remainingMillis, long previousMaxMillis, long newMaxMillis, boolean unavailable) {
		if (unavailable || newMaxMillis <= 0L) {
			return 0L;
		}
		if (previousMaxMillis <= 0L) {
			return newMaxMillis;
		}
		return Math.min(remainingMillis, newMaxMillis);
	}

	private static long update(long remainingMillis, long maxMillis, long elapsedMillis, boolean drain) {
		if (maxMillis <= 0L) {
			return 0L;
		}
		if (drain) {
			return Math.max(0L, remainingMillis - elapsedMillis);
		}
		return remainingMillis + Math.min(maxMillis - remainingMillis, elapsedMillis);
	}

	private static boolean reachedBoundary(long previous, long current, long maxMillis) {
		return maxMillis > 0L
				&& ((previous > 0L && current == 0L) || (previous < maxMillis && current == maxMillis));
	}

	private static boolean elapsedAtLeast(long nowNanos, long previousNanos, long intervalNanos) {
		return nowNanos >= previousNanos && nowNanos - previousNanos >= intervalNanos;
	}

	static boolean setRemainingForTest(ServerPlayerEntity player, EnhancementType type, long remainingMillis) {
		RuntimeState state = ACTIVE.get(player.getUuid());
		if (state == null || state.unavailable || remainingMillis < 0L) {
			return false;
		}
		if (type == EnhancementType.WATER_SOUL && remainingMillis <= state.waterMaxMillis) {
			state.waterRemainingMillis = remainingMillis;
		} else if (type == EnhancementType.FIRE_SOUL && remainingMillis <= state.fireMaxMillis) {
			state.fireRemainingMillis = remainingMillis;
		} else {
			return false;
		}
		state.lastUpdateNanos = System.nanoTime();
		state.dirty = true;
		return true;
	}

	static void clearForTest() {
		ACTIVE.clear();
		SEQUENCES.clear();
	}

	/** Opaque rollback token for a mutation that has already flushed an active equipped item. */
	public static final class EquipmentMutation {
		private final UUID ownerUuid;
		private final RuntimeState state;

		private EquipmentMutation(UUID ownerUuid, RuntimeState state) {
			this.ownerUuid = ownerUuid;
			this.state = state;
		}
	}

	private static final class RuntimeState {
		private final ItemStack stack;
		private long waterRemainingMillis;
		private long waterMaxMillis;
		private long fireRemainingMillis;
		private long fireMaxMillis;
		private long lastUpdateNanos;
		private long lastCheckpointNanos;
		private long lastFullSyncNanos;
		private boolean writable;
		private boolean persist;
		private boolean unavailable;
		private boolean dirty;

		private RuntimeState(
				ItemStack stack,
				long waterRemainingMillis,
				long waterMaxMillis,
				long fireRemainingMillis,
				long fireMaxMillis,
				long nowNanos,
				boolean writable,
				boolean persist,
				boolean unavailable
		) {
			this.stack = stack;
			this.waterRemainingMillis = waterRemainingMillis;
			this.waterMaxMillis = waterMaxMillis;
			this.fireRemainingMillis = fireRemainingMillis;
			this.fireMaxMillis = fireMaxMillis;
			this.lastUpdateNanos = nowNanos;
			this.lastCheckpointNanos = nowNanos;
			this.lastFullSyncNanos = 0L;
			this.writable = writable;
			this.persist = persist;
			this.unavailable = unavailable;
		}
	}

	private record ClientPrediction(
			UUID ownerUuid,
			int mask,
			long waterRemainingMillis,
			long waterMaxMillis,
			long fireRemainingMillis,
			long fireMaxMillis
	) {
		private static ClientPrediction empty() {
			return new ClientPrediction(null, 0, 0L, 0L, 0L, 0L);
		}
	}
}
