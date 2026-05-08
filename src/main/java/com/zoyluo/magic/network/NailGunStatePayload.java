package com.zoyluo.magic.network;

import com.zoyluo.magic.Magic;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

public record NailGunStatePayload(boolean active, Vec3d target) implements CustomPayload {
	public static final CustomPayload.Id<NailGunStatePayload> ID = new CustomPayload.Id<>(Magic.id("nail_gun_state"));
	public static final PacketCodec<RegistryByteBuf, NailGunStatePayload> CODEC = CustomPayload.codecOf(NailGunStatePayload::write, NailGunStatePayload::read);

	public NailGunStatePayload {
		if (target == null) {
			target = Vec3d.ZERO;
		}
	}

	public static NailGunStatePayload inactive() {
		return new NailGunStatePayload(false, Vec3d.ZERO);
	}

	private static NailGunStatePayload read(RegistryByteBuf buf) {
		return new NailGunStatePayload(buf.readBoolean(), buf.readVec3d());
	}

	private void write(RegistryByteBuf buf) {
		buf.writeBoolean(active);
		buf.writeVec3d(target);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
