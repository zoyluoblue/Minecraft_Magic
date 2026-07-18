package com.zoyluo.magic.component;

import com.zoyluo.magic.network.NailGunActionPayload;
import com.zoyluo.magic.network.NailGunCommandPayload;
import com.zoyluo.magic.network.NailGunRuntimePayload;
import com.zoyluo.magic.network.SoulChargeSyncPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.UUID;

@SuppressWarnings("removal")
public final class Phase1DataAndNailGameTests implements FabricGameTest {
	private static final long SECOND = 1_000L;

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void legacySoulBootsInitializeAtFullCharge(TestContext context) {
		ItemStack boots = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 10));
		SoulChargeData.Snapshot snapshot = SoulChargeData.read(boots, 10 * SECOND, 0L, 10_000L);

		require(context, snapshot.status() == SoulChargeData.Status.MISSING, "legacy boots were not recognized as missing runtime data");
		require(context, snapshot.waterRemainingMillis() == 10 * SECOND, "legacy water soul did not initialize at full charge");
		require(context, SoulChargeData.write(boots, SoulChargeData.MODE_ACTIVE, 10_000L, snapshot.waterRemainingMillis(), 0L), "legacy migration write failed");
		SoulChargeData.Snapshot migrated = SoulChargeData.read(boots, 10 * SECOND, 0L, 10_000L);
		require(context, migrated.status() == SoulChargeData.Status.VALID, "migrated boots did not read as v1");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void recoveringSoulChargeUsesElapsedRealTime(TestContext context) {
		ItemStack boots = soulBoots(EnhancementType.FIRE_SOUL, new EnhancementSystem.Level(1, 5));
		require(context, SoulChargeData.write(boots, SoulChargeData.MODE_RECOVERING, 10_000L, 0L, 1_000L), "recovery fixture write failed");
		SoulChargeData.Snapshot snapshot = SoulChargeData.read(boots, 0L, 5_000L, 12_500L);

		require(context, snapshot.fireRemainingMillis() == 3_500L, "offline recovery was not one-to-one with elapsed milliseconds");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void waterSoulKeepsDrainingOnTheSeabedAndAllowsSneakDive(TestContext context) {
		SoulChargeService.clearForTest();
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		try {
			ItemStack boots = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 10));
			EnhancementSystem.setLevel(boots, EnhancementType.FIRE_SOUL, new EnhancementSystem.Level(1, 10));
			player.equipStack(EquipmentSlot.FEET, boots);
			require(context, SoulChargeService.getRemainingMillis(player, EnhancementType.WATER_SOUL) == 10 * SECOND, "water soul did not initialize at full charge");

			FluidState water = Blocks.WATER.getDefaultState().getFluidState();
			FluidState lava = Blocks.LAVA.getDefaultState().getFluidState();
			player.setSneaking(false);
			require(context, BootEnhancementEffects.canWalkOnFluid(player, water), "charged water soul did not support surface walking");
			player.setSneaking(true);
			require(context, !BootEnhancementEffects.canWalkOnFluid(player, water), "sneaking did not disable water-surface walking");
			require(context, BootEnhancementEffects.canWalkOnFluid(player, lava), "sneaking unexpectedly disabled fire-soul lava walking");

			BlockPos floorRelative = new BlockPos(1, 1, 1);
			context.setBlockState(floorRelative, Blocks.STONE);
			context.setBlockState(floorRelative.up(), Blocks.WATER);
			context.setBlockState(floorRelative.up(2), Blocks.WATER);
			context.setBlockState(floorRelative.up(3), Blocks.WATER);
			BlockPos floor = context.getAbsolutePos(floorRelative);
			require(context, context.getWorld().getFluidState(floor.up()).isIn(FluidTags.WATER), "seabed fixture water block was not created");
			player.setPosition(floor.getX() + 0.5D, floor.getY() + 1.0D, floor.getZ() + 0.5D);
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.setSneaking(false);
			// The vanilla submergedInWater boolean consumes the eye-fluid tags gathered by the
			// previous base tick, so two deterministic refreshes are required for this fixture.
			player.baseTick();
			require(context, player.isSubmergedIn(FluidTags.WATER), "seabed fixture did not put the player's eyes in water");
			player.baseTick();

			require(context, player.isTouchingWater(), "seabed fixture did not put the player in water");
			require(context, player.isSubmergedInWater(), "seabed fixture did not submerge the player");
			require(context, BootEnhancementEffects.isNearWater(player), "standing on a submerged solid block was misclassified as leaving water");

			require(context, SoulChargeService.setRemainingForTest(player, EnhancementType.WATER_SOUL, 0L), "could not stage depleted water soul");
			require(context, SoulChargeService.advanceForTest(player, SECOND), "could not advance water soul runtime");
			require(context, SoulChargeService.getRemainingMillis(player, EnhancementType.WATER_SOUL) == 0L, "depleted water soul recovered while the player was still underwater");

			require(context, SoulChargeService.setRemainingForTest(player, EnhancementType.WATER_SOUL, 5 * SECOND), "could not stage charged submerged water soul");
			player.setSneaking(false);
			require(context, !BootEnhancementEffects.canWalkOnFluid(player, water), "submerged water soul re-enabled surface collision and could trap the player underwater");
			context.complete();
		} finally {
			SoulChargeService.clearForTest();
			player.networkHandler.disconnect(new DisconnectionInfo(Text.literal("GameTest complete")));
		}
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void soulChargeResizePreservesAbsoluteValueAndClamps(TestContext context) {
		ItemStack boots = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(2, 10));
		require(context, SoulChargeData.write(boots, SoulChargeData.MODE_ACTIVE, 10_000L, 5_000L, 0L), "resize fixture write failed");

		SoulChargeData.Snapshot upgraded = SoulChargeData.read(boots, 30_000L, 0L, 10_000L);
		require(context, upgraded.waterRemainingMillis() == 5_000L, "capacity upgrade gifted charge instead of preserving the absolute value");
		SoulChargeData.Snapshot downgraded = SoulChargeData.read(boots, 3_000L, 0L, 10_000L);
		require(context, downgraded.waterRemainingMillis() == 3_000L, "capacity downgrade did not clamp charge");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void futureSoulSchemaFailsClosedAndIsNotOverwritten(TestContext context) {
		ItemStack boots = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 10));
		require(context, SoulChargeData.write(boots, SoulChargeData.MODE_ACTIVE, 10_000L, 4_000L, 0L), "future-schema fixture write failed");
		NbtCompound customData = boots.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
		NbtCompound runtime = customData.getCompound(SoulChargeData.RUNTIME_ROOT_KEY);
		runtime.putInt("schema_version", SoulChargeData.CURRENT_SCHEMA_VERSION + 1);
		customData.put(SoulChargeData.RUNTIME_ROOT_KEY, runtime);
		boots.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));
		NbtCompound before = boots.get(DataComponentTypes.CUSTOM_DATA).copyNbt();

		SoulChargeData.Snapshot snapshot = SoulChargeData.read(boots, 10_000L, 0L, 20_000L);
		require(context, snapshot.status() == SoulChargeData.Status.FUTURE, "future schema was not recognized");
		require(context, snapshot.unavailable(), "future schema did not fail closed");
		require(context, !SoulChargeData.write(boots, SoulChargeData.MODE_ACTIVE, 20_000L, 10_000L, 0L), "future schema was unexpectedly writable");
		require(context, before.equals(boots.get(DataComponentTypes.CUSTOM_DATA).copyNbt()), "future schema was modified after rejected write");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void corruptSoulValueFailsClosed(TestContext context) {
		ItemStack boots = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 10));
		require(context, SoulChargeData.write(boots, SoulChargeData.MODE_ACTIVE, 10_000L, 4_000L, 0L), "corrupt fixture write failed");
		NbtCompound customData = boots.get(DataComponentTypes.CUSTOM_DATA).copyNbt();
		NbtCompound runtime = customData.getCompound(SoulChargeData.RUNTIME_ROOT_KEY);
		NbtCompound soulCharge = runtime.getCompound("soul_charge");
		NbtCompound water = soulCharge.getCompound("water_soul");
		water.putString("remaining_ms", "corrupt");
		soulCharge.put("water_soul", water);
		runtime.put("soul_charge", soulCharge);
		customData.put(SoulChargeData.RUNTIME_ROOT_KEY, runtime);
		boots.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

		SoulChargeData.Snapshot snapshot = SoulChargeData.read(boots, 10_000L, 0L, 20_000L);
		require(context, snapshot.status() == SoulChargeData.Status.CORRUPT, "wrong-typed charge was not recognized as corrupt");
		require(context, snapshot.waterRemainingMillis() == 0L && snapshot.unavailable(), "corrupt charge did not fail closed at zero");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void soulRuntimeOnlyChangesDoNotReapplyEquipment(TestContext context) {
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		try {
			ItemStack previous = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 10));
			require(context, SoulChargeData.write(previous, SoulChargeData.MODE_ACTIVE, 10_000L, 8_000L, 0L), "previous runtime fixture write failed");
			ItemStack current = previous.copy();
			require(context, SoulChargeData.write(current, SoulChargeData.MODE_ACTIVE, 11_000L, 7_000L, 0L), "current runtime fixture write failed");

			require(context, !player.areItemsDifferent(previous, current), "soul runtime caused vanilla equipment reapplication");

			ItemStack changedEnhancement = current.copy();
			EnhancementSystem.setLevel(changedEnhancement, EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 9));
			require(context, player.areItemsDifferent(previous, changedEnhancement), "enhancement change was hidden as soul runtime");

			ItemStack damaged = current.copy();
			damaged.setDamage(1);
			require(context, player.areItemsDifferent(previous, damaged), "durability change was hidden as soul runtime");
			context.complete();
		} finally {
			player.networkHandler.disconnect(new DisconnectionInfo(Text.literal("GameTest complete")));
		}
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void shiftClickingEquippedSoulBootsFlushesCurrentCharge(TestContext context) {
		SoulChargeService.clearForTest();
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		try {
			for (int slot = 0; slot < 36; slot++) {
				player.getInventory().setStack(slot, new ItemStack(Items.COBBLESTONE, 64));
			}
			ItemStack boots = soulBoots(EnhancementType.WATER_SOUL, new EnhancementSystem.Level(1, 10));
			player.equipStack(EquipmentSlot.FEET, boots);
			SoulChargeService.getRemainingMillis(player, EnhancementType.WATER_SOUL);
			require(context, SoulChargeService.setRemainingForTest(player, EnhancementType.WATER_SOUL, 250L), "could not stage sub-checkpoint soul charge");

			// Player inventory screen slot 8 is feet. Its quickMove path splits the source stack
			// directly inside ScreenHandler.insertItem instead of calling PlayerInventory.removeStack.
			ItemStack rejectedMove = player.playerScreenHandler.quickMove(player, 8);
			require(context, rejectedMove.isEmpty(), "full inventory unexpectedly accepted shift-clicked boots");
			require(context, player.getEquippedStack(EquipmentSlot.FEET) == boots, "failed shift-click removed the equipped boots");
			SoulChargeData.Snapshot restored = SoulChargeData.read(boots, 10_000L, 0L, System.currentTimeMillis());
			require(context, restored.storedMode() == SoulChargeData.MODE_ACTIVE, "failed shift-click left boots in recovery mode");
			require(context, restored.waterRemainingMillis() >= 250L && restored.waterRemainingMillis() < 1_000L, "failed shift-click lost or reset soul charge");
			require(
					context,
					restored.waterRemainingMillis() == SoulChargeService.getRemainingMillis(player, EnhancementType.WATER_SOUL),
					"failed shift-click desynchronized persisted and active soul charge"
			);

			player.getInventory().setStack(0, ItemStack.EMPTY);
			require(context, SoulChargeService.setRemainingForTest(player, EnhancementType.WATER_SOUL, 250L), "could not restage soul charge after failed move");
			ItemStack quickMoveResult = player.playerScreenHandler.quickMove(player, 8);
			ItemStack movedBoots = player.getInventory().getStack(0);
			SoulChargeData.Snapshot moved = SoulChargeData.read(movedBoots, 10_000L, 0L, System.currentTimeMillis());

			require(context, player.getEquippedStack(EquipmentSlot.FEET).isEmpty(), "shift-click did not remove the equipped boots");
			require(context, !quickMoveResult.isEmpty() && movedBoots != boots, "test did not exercise PlayerScreenHandler copy/split semantics");
			require(context, moved.waterRemainingMillis() >= 250L && moved.waterRemainingMillis() < 1_000L, "shift-clicked boots lost charge or rolled back to the previous full checkpoint");
			require(context, moved.storedMode() == SoulChargeData.MODE_RECOVERING, "shift-clicked boots were not flushed into recovery mode");
			context.complete();
		} finally {
			SoulChargeService.clearForTest();
			player.networkHandler.disconnect(new DisconnectionInfo(Text.literal("GameTest complete")));
		}
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void phase1PayloadCodecsRoundTripAndLegacyOrderStaysFrozen(TestContext context) {
		SoulChargeSyncPayload soul = new SoulChargeSyncPayload(
				SoulChargeSyncPayload.CURRENT_VERSION,
				(byte) 0,
				42L,
				(byte) 3,
				1_000L,
				10_000L,
				2_000L,
				20_000L
		);
		NailGunCommandPayload command = NailGunCommandPayload.current(7L, NailGunCommandPayload.Command.PULL);
		NailGunRuntimePayload runtime = NailGunRuntimePayload.current(
				UUID.fromString("00000000-0000-0000-0000-000000000123"),
				9L,
				7L,
				NailGunRuntimePayload.Phase.PULLING,
				new net.minecraft.util.math.Vec3d(1.25D, 2.5D, -3.75D)
		);

		require(context, soul.equals(roundTrip(context, SoulChargeSyncPayload.CODEC, soul)), "soul payload codec changed field values");
		require(context, command.equals(roundTrip(context, NailGunCommandPayload.CODEC, command)), "nail command codec changed field values");
		require(context, runtime.equals(roundTrip(context, NailGunRuntimePayload.CODEC, runtime)), "nail runtime codec changed field values");
		require(context, NailGunActionPayload.Action.values()[0] == NailGunActionPayload.Action.FIRE_TOGGLE, "legacy FIRE_TOGGLE ordinal changed");
		require(context, NailGunActionPayload.Action.values()[1] == NailGunActionPayload.Action.CANCEL, "legacy CANCEL ordinal changed");
		require(context, NailGunActionPayload.Action.values()[2] == NailGunActionPayload.Action.PULL, "legacy PULL ordinal changed");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void nailCommandGateIsIdempotentRateLimitedAndReleaseSafe(TestContext context) {
		NailGunCommandGate gate = new NailGunCommandGate();
		for (long sequence = 1L; sequence <= 8L; sequence++) {
			require(context, gate.evaluate(sequence, NailGunCommandPayload.Command.PULL, 100L).shouldProcess(), "command inside rate window was rejected");
		}
		require(context, !gate.evaluate(9L, NailGunCommandPayload.Command.PULL, 100L).shouldProcess(), "ninth command in one window bypassed rate limit");
		require(context, gate.evaluate(10L, NailGunCommandPayload.Command.RELEASE, 100L).shouldProcess(), "RELEASE was blocked by rate limit");
		require(context, !gate.evaluate(10L, NailGunCommandPayload.Command.RELEASE, 101L).shouldProcess(), "duplicate request sequence produced a side effect");

		NailGunCommandGate fireGate = new NailGunCommandGate();
		require(context, fireGate.evaluate(1L, NailGunCommandPayload.Command.FIRE, 200L).shouldProcess(), "first FIRE was rejected");
		require(context, !fireGate.evaluate(2L, NailGunCommandPayload.Command.FIRE, 201L).shouldProcess(), "FIRE cooldown was not enforced");
		require(context, fireGate.evaluate(3L, NailGunCommandPayload.Command.FIRE, 204L).shouldProcess(), "FIRE remained blocked after cooldown");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void nailRejectionAndEmptyReleaseRepliesAreThrottled(TestContext context) {
		NailGunEffects.clearForTest();
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		try {
			// Mock-player join sends an initial owner snapshot; isolate command replies here.
			NailGunEffects.clearForTest();
			for (long sequence = 1L; sequence <= 10L; sequence++) {
				NailGunEffects.handleCommand(player, NailGunCommandPayload.current(sequence, NailGunCommandPayload.Command.RELEASE));
			}
			require(context, NailGunEffects.runtimeSendAttemptsForTest() == 1, "empty RELEASE bypassed reply throttling");

			NailGunEffects.clearForTest();
			NailGunCommandPayload wrongVersion = new NailGunCommandPayload(
					NailGunCommandPayload.PROTOCOL_VERSION + 1,
					Long.MAX_VALUE,
					NailGunCommandPayload.Command.PULL
			);
			NailGunEffects.handleCommand(player, wrongVersion);
			NailGunEffects.handleCommand(player, wrongVersion);
			require(context, NailGunEffects.runtimeSendAttemptsForTest() == 1, "protocol rejection bypassed reply throttling");
			require(context, NailGunEffects.acknowledgedSequenceForTest(player) == 0L, "wrong protocol version polluted the valid request sequence");

			NailGunEffects.handleCommand(player, NailGunCommandPayload.current(1L, NailGunCommandPayload.Command.PULL));
			require(context, NailGunEffects.acknowledgedSequenceForTest(player) == 1L, "valid command stayed blocked after wrong-version rejection");
			context.complete();
		} finally {
			NailGunEffects.clearForTest();
			player.networkHandler.disconnect(new DisconnectionInfo(Text.literal("GameTest complete")));
		}
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
	public void nailGunPullVelocityFollowsTheAnchorInAStraightLine(TestContext context) {
		NailGunEffects.clearForTest();
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		try {
			player.changeGameMode(GameMode.SURVIVAL);
			BlockPos playerPos = context.getAbsolutePos(new BlockPos(1, 1, 1));
			player.setPosition(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
			player.setYaw(0.0F);
			player.setPitch(0.0F);
			ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
			EnhancementSystem.setLevel(chestplate, EnhancementType.NAIL_GUN, new EnhancementSystem.Level(4, 10));
			player.equipStack(EquipmentSlot.CHEST, chestplate);
			context.setBlockState(new BlockPos(1, 2, 6), Blocks.STONE);

			NailGunEffects.handleCommand(player, NailGunCommandPayload.current(1L, NailGunCommandPayload.Command.FIRE));
			NailGunEffects.handleCommand(player, NailGunCommandPayload.current(2L, NailGunCommandPayload.Command.PULL));
			Vec3d toAnchor = NailGunEffects.anchorForTest(player.getUuid()).subtract(
					player.getPos().add(0.0D, player.getStandingEyeHeight() * 0.35D, 0.0D)
			);
			player.setVelocity(0.9D, -0.4D, -0.3D);
			NailGunEffects.tickPlayer(player);

			Vec3d pullVelocity = player.getVelocity();
			require(context, NailGunEffects.phaseForTest(player.getUuid()) == NailGunRuntimePayload.Phase.PULLING, "straight pull unexpectedly released the hook");
			require(context, pullVelocity.lengthSquared() > 1.0E-5D, "straight pull produced no movement");
			require(
					context,
					pullVelocity.normalize().dotProduct(toAnchor.normalize()) > 0.999999D,
					"straight pull retained lateral momentum instead of following the anchor"
			);
			context.complete();
		} finally {
			NailGunEffects.clearForTest();
			player.networkHandler.disconnect(new DisconnectionInfo(Text.literal("GameTest complete")));
		}
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
	public void nailGunStateMachineIsIdempotentAndInvalidAnchorReleases(TestContext context) {
		NailGunEffects.clearForTest();
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		try {
			player.changeGameMode(GameMode.SURVIVAL);
			BlockPos playerPos = context.getAbsolutePos(new BlockPos(1, 1, 1));
			player.setPosition(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
			player.setYaw(0.0F);
			player.setPitch(0.0F);
			ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
			EnhancementSystem.setLevel(chestplate, EnhancementType.NAIL_GUN, new EnhancementSystem.Level(4, 10));
			player.equipStack(EquipmentSlot.CHEST, chestplate);
			BlockPos anchorRelative = new BlockPos(1, 2, 6);
			context.setBlockState(anchorRelative, Blocks.STONE);

			NailGunEffects.handleCommand(player, NailGunCommandPayload.current(1L, NailGunCommandPayload.Command.FIRE));
			require(context, NailGunEffects.phaseForTest(player.getUuid()) == NailGunRuntimePayload.Phase.ATTACHED, "valid FIRE did not attach");
			NailGunEffects.handleCommand(player, NailGunCommandPayload.current(2L, NailGunCommandPayload.Command.FIRE));
			require(context, NailGunEffects.phaseForTest(player.getUuid()) == NailGunRuntimePayload.Phase.ATTACHED, "idempotent FIRE toggled an existing hook off");
			NailGunEffects.handleCommand(player, NailGunCommandPayload.current(3L, NailGunCommandPayload.Command.PULL));
			require(context, NailGunEffects.phaseForTest(player.getUuid()) == NailGunRuntimePayload.Phase.PULLING, "PULL did not transition ATTACHED to PULLING");

			context.removeBlock(anchorRelative);
			NailGunEffects.tickPlayer(player);
			require(context, NailGunEffects.phaseForTest(player.getUuid()) == NailGunRuntimePayload.Phase.RELEASED, "destroyed anchor did not release hook");
			require(context, NailGunEffects.activeHookCountForTest() == 0, "released hook leaked server state");
			context.complete();
		} finally {
			NailGunEffects.clearForTest();
			player.networkHandler.disconnect(new DisconnectionInfo(Text.literal("GameTest complete")));
		}
	}

	private static ItemStack soulBoots(EnhancementType type, EnhancementSystem.Level level) {
		ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
		EnhancementSystem.setLevel(boots, type, level);
		return boots;
	}

	private static <T> T roundTrip(TestContext context, net.minecraft.network.codec.PacketCodec<RegistryByteBuf, T> codec, T value) {
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
		try {
			codec.encode(buffer, value);
			return codec.decode(buffer);
		} finally {
			buffer.release();
		}
	}

	private static void require(TestContext context, boolean condition, String message) {
		if (!condition) {
			context.throwGameTestException(message);
		}
	}
}
