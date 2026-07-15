package com.zoyluo.magic.component;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HelmetEnhancementEffects {
	private static final int UPDATE_INTERVAL_TICKS = 5;
	private static final int MIN_VISIBLE_LIGHT_LEVEL = 12;
	private static final Map<UUID, LightPlacement> PLAYER_LIGHTS = new HashMap<>();
	private static final Map<LightKey, Set<UUID>> LIGHT_OWNERS = new HashMap<>();

	private HelmetEnhancementEffects() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ServerThreadTasks.execute(server, () -> clearPlayerLight(server, handler.player.getUuid()))
		);
		ServerLifecycleEvents.SERVER_STOPPING.register(HelmetEnhancementEffects::clearAllLights);
	}

	public static void tickPlayer(PlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld world) || player.age % UPDATE_INTERVAL_TICKS != 0) {
			return;
		}

		ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
		int lightRadius = EnhancementSystem.getIlluminationRadius(helmet);
		if (lightRadius <= 0) {
			clearPlayerLight(world.getServer(), player.getUuid());
			return;
		}

		BlockPos pos = findLightPosition(world, player);
		if (pos == null || !updatePlayerLight(world, player.getUuid(), pos, visibleLightLevel(lightRadius))) {
			clearPlayerLight(world.getServer(), player.getUuid());
		}
	}

	public static void clearPlayerLight(MinecraftServer server, UUID playerUuid) {
		LightPlacement placement = PLAYER_LIGHTS.remove(playerUuid);
		if (placement == null) {
			return;
		}

		ServerWorld world = server.getWorld(placement.worldKey());
		if (world == null) {
			LIGHT_OWNERS.remove(new LightKey(placement.worldKey(), placement.pos()));
			return;
		}
		removeOwner(world, playerUuid, placement.pos());
	}

	private static void clearAllLights(MinecraftServer server) {
		for (UUID playerUuid : Set.copyOf(PLAYER_LIGHTS.keySet())) {
			clearPlayerLight(server, playerUuid);
		}
		LIGHT_OWNERS.clear();
	}

	private static BlockPos findLightPosition(ServerWorld world, PlayerEntity player) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		double[] offsets = {1.0D, 0.1D, 1.8D, -0.1D};
		for (double offset : offsets) {
			BlockPos pos = BlockPos.ofFloored(x, y + offset, z);
			if (canUseLightPosition(world, pos)) {
				return pos.toImmutable();
			}
		}
		return null;
	}

	private static boolean updatePlayerLight(ServerWorld world, UUID playerUuid, BlockPos pos, int lightLevel) {
		LightPlacement oldPlacement = PLAYER_LIGHTS.get(playerUuid);
		if (oldPlacement != null && (!oldPlacement.worldKey().equals(world.getRegistryKey()) || !oldPlacement.pos().equals(pos))) {
			clearPlayerLight(world.getServer(), playerUuid);
		}

		if (!canUseLightPosition(world, pos)) {
			return false;
		}

		LightKey key = new LightKey(world.getRegistryKey(), pos);
		LIGHT_OWNERS.computeIfAbsent(key, ignored -> new HashSet<>()).add(playerUuid);
		PLAYER_LIGHTS.put(playerUuid, new LightPlacement(world.getRegistryKey(), pos));

		BlockState state = world.getBlockState(pos);
		if (!state.isOf(Blocks.LIGHT) || state.get(LightBlock.LEVEL_15) != lightLevel) {
			BlockState lightState = Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, lightLevel);
			world.setBlockState(pos, lightState, Block.NOTIFY_ALL);
		}
		return true;
	}

	private static void removeOwner(ServerWorld world, UUID playerUuid, BlockPos pos) {
		LightKey key = new LightKey(world.getRegistryKey(), pos);
		Set<UUID> owners = LIGHT_OWNERS.get(key);
		if (owners == null) {
			return;
		}

		owners.remove(playerUuid);
		if (!owners.isEmpty()) {
			return;
		}

		LIGHT_OWNERS.remove(key);
		if (world.getBlockState(pos).isOf(Blocks.LIGHT)) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	private static boolean canUseLightPosition(ServerWorld world, BlockPos pos) {
		if (!world.isInBuildLimit(pos)) {
			return false;
		}

		BlockState state = world.getBlockState(pos);
		if (state.isAir()) {
			return true;
		}
		return state.isOf(Blocks.LIGHT) && LIGHT_OWNERS.containsKey(new LightKey(world.getRegistryKey(), pos));
	}

	private static int visibleLightLevel(int lightRadius) {
		return Math.min(15, MIN_VISIBLE_LIGHT_LEVEL + Math.max(0, lightRadius - 1) / 2);
	}

	private record LightPlacement(RegistryKey<World> worldKey, BlockPos pos) {
	}

	private record LightKey(RegistryKey<World> worldKey, BlockPos pos) {
	}
}
