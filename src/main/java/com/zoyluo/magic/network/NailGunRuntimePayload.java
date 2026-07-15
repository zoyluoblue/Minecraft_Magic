package com.zoyluo.magic.network;

import com.zoyluo.magic.Magic;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public record NailGunRuntimePayload(
		int protocolVersion,
		UUID ownerUuid,
		long stateVersion,
		long acknowledgedRequestSequence,
		Phase phase,
		Vec3d anchor
) implements CustomPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final CustomPayload.Id<NailGunRuntimePayload> ID = new CustomPayload.Id<>(Magic.id("nail_gun_runtime_v1"));
	public static final PacketCodec<RegistryByteBuf, NailGunRuntimePayload> CODEC = CustomPayload.codecOf(NailGunRuntimePayload::write, NailGunRuntimePayload::read);

	public NailGunRuntimePayload {
		if (ownerUuid == null) {
			throw new IllegalArgumentException("Nail gun owner UUID cannot be null");
		}
		if (phase == null) {
			throw new IllegalArgumentException("Nail gun phase cannot be null");
		}
		if (anchor == null || !Double.isFinite(anchor.x) || !Double.isFinite(anchor.y) || !Double.isFinite(anchor.z)) {
			anchor = Vec3d.ZERO;
		}
		if (phase == Phase.RELEASED) {
			anchor = Vec3d.ZERO;
		}
	}

	public static NailGunRuntimePayload current(UUID ownerUuid, long stateVersion, long acknowledgedRequestSequence, Phase phase, Vec3d anchor) {
		return new NailGunRuntimePayload(PROTOCOL_VERSION, ownerUuid, stateVersion, acknowledgedRequestSequence, phase, anchor);
	}

	private static NailGunRuntimePayload read(RegistryByteBuf buf) {
		int version = buf.readVarInt();
		UUID ownerUuid = buf.readUuid();
		long stateVersion = buf.readVarLong();
		long acknowledgedRequestSequence = buf.readVarLong();
		Phase phase = Phase.byId(buf.readUnsignedByte());
		Vec3d anchor = buf.readVec3d();
		return new NailGunRuntimePayload(version, ownerUuid, stateVersion, acknowledgedRequestSequence, phase, anchor);
	}

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeUuid(ownerUuid);
		buf.writeVarLong(stateVersion);
		buf.writeVarLong(acknowledgedRequestSequence);
		buf.writeByte(phase.id());
		buf.writeVec3d(anchor);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public enum Phase {
		RELEASED(0),
		ATTACHED(1),
		PULLING(2);

		private final int id;

		Phase(int id) {
			this.id = id;
		}

		public int id() {
			return id;
		}

		public static Phase byId(int id) {
			for (Phase phase : values()) {
				if (phase.id == id) {
					return phase;
				}
			}
			throw new IllegalArgumentException("Unknown nail gun phase id: " + id);
		}
	}
}
