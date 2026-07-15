package com.zoyluo.magic.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class MagicNetworking {
	private MagicNetworking() {
	}

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.playC2S().register(NailGunActionPayload.ID, NailGunActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(NailGunCommandPayload.ID, NailGunCommandPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NailGunStatePayload.ID, NailGunStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(NailGunRuntimePayload.ID, NailGunRuntimePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SoulChargeSyncPayload.ID, SoulChargeSyncPayload.CODEC);
	}
}
