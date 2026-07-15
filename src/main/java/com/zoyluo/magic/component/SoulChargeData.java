package com.zoyluo.magic.component;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * Versioned, item-owned persistence for the water and fire soul charge.
 */
public final class SoulChargeData {
	public static final int CURRENT_SCHEMA_VERSION = 1;
	public static final byte MODE_ACTIVE = 0;
	public static final byte MODE_RECOVERING = 1;
	public static final byte MODE_UNAVAILABLE = 2;

	public static final String RUNTIME_ROOT_KEY = "magic:runtime";
	private static final String SCHEMA_VERSION_KEY = "schema_version";
	private static final String SOUL_CHARGE_KEY = "soul_charge";
	private static final String MODE_KEY = "mode";
	private static final String UPDATED_AT_EPOCH_MILLIS_KEY = "updated_at_epoch_ms";
	private static final String WATER_SOUL_KEY = "water_soul";
	private static final String FIRE_SOUL_KEY = "fire_soul";
	private static final String REMAINING_MILLIS_KEY = "remaining_ms";

	private SoulChargeData() {
	}

	public static Snapshot read(ItemStack stack, long waterMaxMillis, long fireMaxMillis, long nowEpochMillis) {
		long safeWaterMax = nonNegative(waterMaxMillis);
		long safeFireMax = nonNegative(fireMaxMillis);
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (component == null) {
			return Snapshot.missing(safeWaterMax, safeFireMax);
		}

		NbtCompound nbt = component.copyNbt();
		if (!nbt.contains(RUNTIME_ROOT_KEY)) {
			return Snapshot.missing(safeWaterMax, safeFireMax);
		}
		if (!nbt.contains(RUNTIME_ROOT_KEY, NbtElement.COMPOUND_TYPE)) {
			return Snapshot.corrupt(false);
		}

		NbtCompound runtime = nbt.getCompound(RUNTIME_ROOT_KEY);
		if (!runtime.contains(SCHEMA_VERSION_KEY, NbtElement.INT_TYPE)) {
			// An unknown representation may belong to a newer writer. Fail closed and preserve it.
			return Snapshot.corrupt(false);
		}

		int schemaVersion = runtime.getInt(SCHEMA_VERSION_KEY);
		if (schemaVersion > CURRENT_SCHEMA_VERSION) {
			return Snapshot.future();
		}
		if (schemaVersion != CURRENT_SCHEMA_VERSION) {
			return Snapshot.corrupt(true);
		}
		if (!runtime.contains(SOUL_CHARGE_KEY, NbtElement.COMPOUND_TYPE)) {
			return Snapshot.corrupt(true);
		}

		NbtCompound soulCharge = runtime.getCompound(SOUL_CHARGE_KEY);
		boolean repaired = false;
		byte mode = MODE_ACTIVE;
		if (soulCharge.contains(MODE_KEY, NbtElement.BYTE_TYPE)) {
			byte storedMode = soulCharge.getByte(MODE_KEY);
			if (storedMode == MODE_ACTIVE || storedMode == MODE_RECOVERING) {
				mode = storedMode;
			} else {
				repaired = true;
			}
		} else {
			repaired = true;
		}

		long updatedAtEpochMillis = nowEpochMillis;
		if (soulCharge.contains(UPDATED_AT_EPOCH_MILLIS_KEY, NbtElement.LONG_TYPE)) {
			long storedUpdatedAt = soulCharge.getLong(UPDATED_AT_EPOCH_MILLIS_KEY);
			if (storedUpdatedAt > 0L && nowEpochMillis > 0L && storedUpdatedAt <= nowEpochMillis) {
				updatedAtEpochMillis = storedUpdatedAt;
			} else {
				mode = MODE_ACTIVE;
				repaired = true;
			}
		} else {
			mode = MODE_ACTIVE;
			repaired = true;
		}

		SoulValue water = readSoul(soulCharge, WATER_SOUL_KEY, safeWaterMax);
		SoulValue fire = readSoul(soulCharge, FIRE_SOUL_KEY, safeFireMax);
		repaired |= water.repaired || fire.repaired;
		long waterRemainingMillis = water.remainingMillis;
		long fireRemainingMillis = fire.remainingMillis;
		if (mode == MODE_RECOVERING && nowEpochMillis > updatedAtEpochMillis) {
			long elapsedMillis = nowEpochMillis - updatedAtEpochMillis;
			waterRemainingMillis = recover(waterRemainingMillis, safeWaterMax, elapsedMillis);
			fireRemainingMillis = recover(fireRemainingMillis, safeFireMax, elapsedMillis);
		}

		return new Snapshot(mode, waterRemainingMillis, fireRemainingMillis, repaired ? Status.CORRUPT : Status.VALID, true);
	}

	public static boolean write(
			ItemStack stack,
			byte mode,
			long updatedAtEpochMillis,
			long waterRemainingMillis,
			long fireRemainingMillis
	) {
		if (stack.isEmpty()
				|| (mode != MODE_ACTIVE && mode != MODE_RECOVERING)
				|| updatedAtEpochMillis <= 0L
				|| waterRemainingMillis < 0L
				|| fireRemainingMillis < 0L) {
			return false;
		}

		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
		NbtCompound runtime;
		if (nbt.contains(RUNTIME_ROOT_KEY)) {
			if (!nbt.contains(RUNTIME_ROOT_KEY, NbtElement.COMPOUND_TYPE)) {
				return false;
			}
			runtime = nbt.getCompound(RUNTIME_ROOT_KEY);
			if (!runtime.contains(SCHEMA_VERSION_KEY, NbtElement.INT_TYPE)) {
				return false;
			}
			if (runtime.getInt(SCHEMA_VERSION_KEY) > CURRENT_SCHEMA_VERSION) {
				return false;
			}
		} else {
			runtime = new NbtCompound();
		}

		NbtCompound soulCharge = runtime.contains(SOUL_CHARGE_KEY, NbtElement.COMPOUND_TYPE)
				? runtime.getCompound(SOUL_CHARGE_KEY) : new NbtCompound();
		NbtCompound waterSoul = soulCharge.contains(WATER_SOUL_KEY, NbtElement.COMPOUND_TYPE)
				? soulCharge.getCompound(WATER_SOUL_KEY) : new NbtCompound();
		waterSoul.putLong(REMAINING_MILLIS_KEY, waterRemainingMillis);
		NbtCompound fireSoul = soulCharge.contains(FIRE_SOUL_KEY, NbtElement.COMPOUND_TYPE)
				? soulCharge.getCompound(FIRE_SOUL_KEY) : new NbtCompound();
		fireSoul.putLong(REMAINING_MILLIS_KEY, fireRemainingMillis);
		soulCharge.putByte(MODE_KEY, mode);
		soulCharge.putLong(UPDATED_AT_EPOCH_MILLIS_KEY, updatedAtEpochMillis);
		soulCharge.put(WATER_SOUL_KEY, waterSoul);
		soulCharge.put(FIRE_SOUL_KEY, fireSoul);
		runtime.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
		runtime.put(SOUL_CHARGE_KEY, soulCharge);
		nbt.put(RUNTIME_ROOT_KEY, runtime);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		return true;
	}

	/**
	 * Returns whether two equipped stacks differ only in this mod's mutable soul-charge payload.
	 * Other runtime fields and every other item component remain part of the comparison.
	 */
	public static boolean differsOnlyBySoulRuntime(ItemStack previous, ItemStack current) {
		if (previous == current
				|| previous.isEmpty()
				|| current.isEmpty()
				|| previous.getCount() != current.getCount()
				|| !previous.isOf(current.getItem())) {
			return false;
		}

		NbtComponent previousComponent = previous.get(DataComponentTypes.CUSTOM_DATA);
		NbtComponent currentComponent = current.get(DataComponentTypes.CUSTOM_DATA);
		if (previousComponent == null || currentComponent == null) {
			return false;
		}

		NbtCompound previousNbt = previousComponent.copyNbt();
		NbtCompound currentNbt = currentComponent.copyNbt();
		boolean previousHadSoulCharge = stripSoulCharge(previousNbt);
		boolean currentHadSoulCharge = stripSoulCharge(currentNbt);
		if (!previousHadSoulCharge && !currentHadSoulCharge) {
			return false;
		}

		ItemStack sanitizedPrevious = previous.copy();
		ItemStack sanitizedCurrent = current.copy();
		sanitizedPrevious.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(previousNbt));
		sanitizedCurrent.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(currentNbt));
		return ItemStack.areEqual(sanitizedPrevious, sanitizedCurrent);
	}

	private static SoulValue readSoul(NbtCompound soulCharge, String soulKey, long maxMillis) {
		if (!soulCharge.contains(soulKey)) {
			return new SoulValue(maxMillis, true);
		}
		if (!soulCharge.contains(soulKey, NbtElement.COMPOUND_TYPE)) {
			return new SoulValue(0L, true);
		}
		NbtCompound soul = soulCharge.getCompound(soulKey);
		if (!soul.contains(REMAINING_MILLIS_KEY, NbtElement.LONG_TYPE)) {
			return new SoulValue(0L, true);
		}
		long stored = soul.getLong(REMAINING_MILLIS_KEY);
		long clamped = Math.max(0L, Math.min(stored, maxMillis));
		return new SoulValue(clamped, stored != clamped);
	}

	private static boolean stripSoulCharge(NbtCompound nbt) {
		if (!nbt.contains(RUNTIME_ROOT_KEY, NbtElement.COMPOUND_TYPE)) {
			return false;
		}
		NbtCompound runtime = nbt.getCompound(RUNTIME_ROOT_KEY);
		boolean removed = runtime.contains(SOUL_CHARGE_KEY);
		runtime.remove(SOUL_CHARGE_KEY);
		nbt.put(RUNTIME_ROOT_KEY, runtime);
		return removed;
	}

	private static long recover(long remainingMillis, long maxMillis, long elapsedMillis) {
		if (remainingMillis >= maxMillis || elapsedMillis <= 0L) {
			return remainingMillis;
		}
		return remainingMillis + Math.min(maxMillis - remainingMillis, elapsedMillis);
	}

	private static long nonNegative(long value) {
		return Math.max(0L, value);
	}

	private record SoulValue(long remainingMillis, boolean repaired) {
	}

	public enum Status {
		MISSING,
		VALID,
		CORRUPT,
		FUTURE
	}

	public record Snapshot(
			byte storedMode,
			long waterRemainingMillis,
			long fireRemainingMillis,
			Status status,
			boolean writable
	) {
		private static Snapshot missing(long waterMaxMillis, long fireMaxMillis) {
			return new Snapshot(MODE_RECOVERING, waterMaxMillis, fireMaxMillis, Status.MISSING, true);
		}

		private static Snapshot corrupt(boolean writable) {
			return new Snapshot(MODE_UNAVAILABLE, 0L, 0L, Status.CORRUPT, writable);
		}

		private static Snapshot future() {
			return new Snapshot(MODE_UNAVAILABLE, 0L, 0L, Status.FUTURE, false);
		}

		public boolean unavailable() {
			return status == Status.CORRUPT || status == Status.FUTURE;
		}
	}
}
