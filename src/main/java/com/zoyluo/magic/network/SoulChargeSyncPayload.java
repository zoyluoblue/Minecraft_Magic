package com.zoyluo.magic.network;

import com.zoyluo.magic.Magic;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SoulChargeSyncPayload(
		int version,
		int mode,
		long sequence,
		int mask,
		long waterRemainingMillis,
		long waterMaxMillis,
		long fireRemainingMillis,
		long fireMaxMillis
) implements CustomPayload {
	public static final int CURRENT_VERSION = 1;
	public static final int MODE_SNAPSHOT = 0;
	public static final int MODE_DELTA = 1;
	public static final int WATER_SOUL_MASK = 1;
	public static final int FIRE_SOUL_MASK = 1 << 1;
	public static final CustomPayload.Id<SoulChargeSyncPayload> ID = new CustomPayload.Id<>(Magic.id("soul_charge_sync_v1"));
	public static final PacketCodec<RegistryByteBuf, SoulChargeSyncPayload> CODEC =
			CustomPayload.codecOf(SoulChargeSyncPayload::write, SoulChargeSyncPayload::read);

	private static SoulChargeSyncPayload read(RegistryByteBuf buf) {
		return new SoulChargeSyncPayload(
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readVarLong(),
				buf.readVarInt(),
				buf.readVarLong(),
				buf.readVarLong(),
				buf.readVarLong(),
				buf.readVarLong()
		);
	}

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(version);
		buf.writeVarInt(mode);
		buf.writeVarLong(sequence);
		buf.writeVarInt(mask);
		buf.writeVarLong(waterRemainingMillis);
		buf.writeVarLong(waterMaxMillis);
		buf.writeVarLong(fireRemainingMillis);
		buf.writeVarLong(fireMaxMillis);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
