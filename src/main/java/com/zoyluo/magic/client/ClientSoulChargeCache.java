package com.zoyluo.magic.client;

import com.zoyluo.magic.component.EnhancementType;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.SoulChargeService;
import com.zoyluo.magic.network.SoulChargeSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.UUID;

public final class ClientSoulChargeCache {
	private static final int VALID_MASK = SoulChargeSyncPayload.WATER_SOUL_MASK | SoulChargeSyncPayload.FIRE_SOUL_MASK;
	private static final long MAX_CHARGE_MILLIS = 40_000L;
	private static State state = State.empty();
	private static boolean registered;

	private ClientSoulChargeCache() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientPlayNetworking.registerGlobalReceiver(SoulChargeSyncPayload.ID, (payload, context) -> accept(payload, context.player().getUuid()));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clear());
	}

	public static int getRemainingSeconds(EnhancementType type) {
		long remainingMillis = getRemainingMillis(type);
		return (int) ((remainingMillis + 999L) / 1_000L);
	}

	public static long getRemainingMillis(EnhancementType type) {
		if (!hasAuthoritativeValue(type)) {
			return 0L;
		}
		return switch (type) {
			case WATER_SOUL -> state.hasWaterSoul() ? state.waterRemainingMillis() : 0L;
			case FIRE_SOUL -> state.hasFireSoul() ? state.fireRemainingMillis() : 0L;
			default -> 0L;
		};
	}

	public static boolean hasAuthoritativeValue(EnhancementType type) {
		return state.status() == Status.READY && matchesLocalEquipment(type);
	}

	public static State getState() {
		return state;
	}

	public static void clear() {
		state = State.empty();
		SoulChargeService.clearClientPrediction();
	}

	private static void accept(SoulChargeSyncPayload payload, UUID ownerUuid) {
		if (payload.version() != SoulChargeSyncPayload.CURRENT_VERSION) {
			state = State.incompatible();
			SoulChargeService.clearClientPrediction();
			return;
		}
		if (payload.sequence() <= state.sequence()) {
			return;
		}
		if (!isValid(payload)) {
			state = State.loading(Math.max(state.sequence(), payload.sequence()));
			SoulChargeService.clearClientPrediction();
			return;
		}
		if (payload.mode() == SoulChargeSyncPayload.MODE_DELTA
				&& (state.status() != Status.READY || payload.sequence() != state.sequence() + 1L)) {
			state = State.loading(payload.sequence());
			SoulChargeService.clearClientPrediction();
			return;
		}

		state = new State(
				Status.READY,
				payload.sequence(),
				payload.mask(),
				payload.waterRemainingMillis(),
				payload.waterMaxMillis(),
				payload.fireRemainingMillis(),
				payload.fireMaxMillis()
		);
		SoulChargeService.updateClientPrediction(
				ownerUuid,
				payload.mask(),
				payload.waterRemainingMillis(),
				payload.waterMaxMillis(),
				payload.fireRemainingMillis(),
				payload.fireMaxMillis()
		);
	}

	private static boolean isValid(SoulChargeSyncPayload payload) {
		if (payload.sequence() < 0L
				|| (payload.mask() & ~VALID_MASK) != 0
				|| (payload.mode() != SoulChargeSyncPayload.MODE_SNAPSHOT
				&& payload.mode() != SoulChargeSyncPayload.MODE_DELTA)) {
			return false;
		}
		if (!validRange(payload.waterRemainingMillis(), payload.waterMaxMillis())
				|| !validRange(payload.fireRemainingMillis(), payload.fireMaxMillis())) {
			return false;
		}
		if ((payload.mask() & SoulChargeSyncPayload.WATER_SOUL_MASK) == 0
				&& (payload.waterRemainingMillis() != 0L || payload.waterMaxMillis() != 0L)) {
			return false;
		}
		if ((payload.mask() & SoulChargeSyncPayload.FIRE_SOUL_MASK) == 0
				&& (payload.fireRemainingMillis() != 0L || payload.fireMaxMillis() != 0L)) {
			return false;
		}
		return true;
	}

	private static boolean matchesLocalEquipment(EnhancementType type) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return false;
		}
		ItemStack boots = client.player.getEquippedStack(EquipmentSlot.FEET);
		long expectedMax = EnhancementSystem.getSoulSeconds(boots, type) * 1_000L;
		return switch (type) {
			case WATER_SOUL -> state.hasWaterSoul() && state.waterMaxMillis() == expectedMax;
			case FIRE_SOUL -> state.hasFireSoul() && state.fireMaxMillis() == expectedMax;
			default -> false;
		};
	}

	private static boolean validRange(long remainingMillis, long maxMillis) {
		return remainingMillis >= 0L
				&& maxMillis >= 0L
				&& remainingMillis <= maxMillis
				&& maxMillis <= MAX_CHARGE_MILLIS;
	}

	public record State(
			Status status,
			long sequence,
			int mask,
			long waterRemainingMillis,
			long waterMaxMillis,
			long fireRemainingMillis,
			long fireMaxMillis
	) {
		private static State empty() {
			return new State(Status.EMPTY, -1L, 0, 0L, 0L, 0L, 0L);
		}

		private static State loading(long sequence) {
			return new State(Status.LOADING, sequence, 0, 0L, 0L, 0L, 0L);
		}

		private static State incompatible() {
			return new State(Status.INCOMPATIBLE, -1L, 0, 0L, 0L, 0L, 0L);
		}

		public boolean hasWaterSoul() {
			return (mask & SoulChargeSyncPayload.WATER_SOUL_MASK) != 0;
		}

		public boolean hasFireSoul() {
			return (mask & SoulChargeSyncPayload.FIRE_SOUL_MASK) != 0;
		}
	}

	public enum Status {
		EMPTY,
		LOADING,
		READY,
		INCOMPATIBLE
	}
}
