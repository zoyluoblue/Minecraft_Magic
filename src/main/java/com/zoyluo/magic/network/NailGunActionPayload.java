package com.zoyluo.magic.network;

import com.zoyluo.magic.Magic;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record NailGunActionPayload(Action action) implements CustomPayload {
	public static final CustomPayload.Id<NailGunActionPayload> ID = new CustomPayload.Id<>(Magic.id("nail_gun_action"));
	public static final PacketCodec<RegistryByteBuf, NailGunActionPayload> CODEC = CustomPayload.codecOf(NailGunActionPayload::write, NailGunActionPayload::read);

	private static NailGunActionPayload read(RegistryByteBuf buf) {
		return new NailGunActionPayload(buf.readEnumConstant(Action.class));
	}

	private void write(RegistryByteBuf buf) {
		buf.writeEnumConstant(action);
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public enum Action {
		FIRE_TOGGLE,
		CANCEL,
		PULL
	}
}
