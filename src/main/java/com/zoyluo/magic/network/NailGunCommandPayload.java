package com.zoyluo.magic.network;

import com.zoyluo.magic.Magic;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record NailGunCommandPayload(int protocolVersion, long requestSequence, Command command) implements CustomPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final CustomPayload.Id<NailGunCommandPayload> ID = new CustomPayload.Id<>(Magic.id("nail_gun_command_v1"));
	public static final PacketCodec<RegistryByteBuf, NailGunCommandPayload> CODEC = CustomPayload.codecOf(NailGunCommandPayload::write, NailGunCommandPayload::read);

	public NailGunCommandPayload {
		if (command == null) {
			throw new IllegalArgumentException("Nail gun command cannot be null");
		}
	}

	public static NailGunCommandPayload current(long requestSequence, Command command) {
		return new NailGunCommandPayload(PROTOCOL_VERSION, requestSequence, command);
	}

	private static NailGunCommandPayload read(RegistryByteBuf buf) {
		int version = buf.readVarInt();
		long requestSequence = buf.readVarLong();
		return new NailGunCommandPayload(version, requestSequence, Command.byId(buf.readUnsignedByte()));
	}

	private void write(RegistryByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeVarLong(requestSequence);
		buf.writeByte(command.id());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}

	public enum Command {
		FIRE(0),
		PULL(1),
		RELEASE(2);

		private final int id;

		Command(int id) {
			this.id = id;
		}

		public int id() {
			return id;
		}

		public static Command byId(int id) {
			for (Command command : values()) {
				if (command.id == id) {
					return command;
				}
			}
			throw new IllegalArgumentException("Unknown nail gun command id: " + id);
		}
	}
}
