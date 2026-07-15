package com.zoyluo.magic.gametest;

import com.zoyluo.magic.Magic;
import com.zoyluo.magic.block.StrengtheningTableBlock;
import com.zoyluo.magic.block.StrengtheningTableBlockEntity;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/** Dedicated-server coverage for the Phase 3A table data and collision contracts. */
@SuppressWarnings("removal")
public final class Phase3ATableGameTests implements FabricGameTest {
	private static final double EPSILON = 1.0E-9D;

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void strengtheningTableInventoryRoundTripsThroughInitialAndUpdateNbt(TestContext context) {
		BlockPos relativePos = new BlockPos(1, 1, 1);
		context.setBlockState(relativePos, Magic.STRENGTHENING_TABLE.getDefaultState());
		StrengtheningTableBlockEntity source = context.getBlockEntity(relativePos);

		ItemStack tool = new ItemStack(Items.DIAMOND_SWORD);
		EnhancementSystem.setLevel(tool, EnhancementType.POWER, new EnhancementSystem.Level(3, 7));
		ItemStack material = new ItemStack(Items.DIAMOND, 17);
		source.setStack(StrengtheningTableBlockEntity.TOOL_SLOT, tool);
		source.setStack(StrengtheningTableBlockEntity.MATERIAL_SLOT, material);

		RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
		NbtCompound initialNbt = source.toInitialChunkDataNbt(registries);
		StrengtheningTableBlockEntity restored = new StrengtheningTableBlockEntity(
				BlockPos.ORIGIN,
				Magic.STRENGTHENING_TABLE.getDefaultState()
		);
		restored.read(initialNbt, registries);

		require(context, ItemStack.areEqual(tool, restored.getDisplayedTool()), "initial NBT lost the displayed tool");
		require(context, ItemStack.areEqual(material, restored.getDisplayedMaterial()), "initial NBT lost the displayed material");
		require(
				context,
				EnhancementSystem.getLevel(restored.getDisplayedTool(), EnhancementType.POWER)
						.equals(new EnhancementSystem.Level(3, 7)),
				"initial NBT lost the displayed tool's enhancement data"
		);
		restored.read(new NbtCompound(), registries);
		require(context, restored.getDisplayedTool().isEmpty(), "empty NBT retained a stale displayed tool");
		require(context, restored.getDisplayedMaterial().isEmpty(), "empty NBT retained a stale displayed material");

		BlockEntityUpdateS2CPacket updatePacket = source.toUpdatePacket();
		require(context, updatePacket != null, "strengthening table did not create an update packet");
		require(context, updatePacket.getPos().equals(source.getPos()), "update packet used the wrong block position");
		require(
				context,
				updatePacket.getBlockEntityType() == Magic.STRENGTHENING_TABLE_BLOCK_ENTITY,
				"update packet used the wrong block-entity type"
		);
		require(context, updatePacket.getNbt().equals(initialNbt), "update packet and initial chunk NBT diverged");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void strengtheningTableRemovalAndClearMarkDirtyOnlyForRealChanges(TestContext context) {
		CountingStrengtheningTableBlockEntity table = new CountingStrengtheningTableBlockEntity();
		table.setStack(StrengtheningTableBlockEntity.TOOL_SLOT, new ItemStack(Items.DIAMOND_SWORD));
		table.resetDirtyCount();

		require(context, !table.removeStack(StrengtheningTableBlockEntity.TOOL_SLOT).isEmpty(), "populated tool slot did not remove its stack");
		require(context, table.getDirtyCount() == 1, "removing a populated slot did not mark dirty exactly once");
		require(context, table.removeStack(StrengtheningTableBlockEntity.TOOL_SLOT).isEmpty(), "empty tool slot returned a stack");
		require(context, table.getDirtyCount() == 1, "removing an empty slot marked dirty");

		table.setStack(StrengtheningTableBlockEntity.TOOL_SLOT, new ItemStack(Items.DIAMOND_SWORD));
		table.setStack(StrengtheningTableBlockEntity.MATERIAL_SLOT, new ItemStack(Items.DIAMOND, 2));
		table.resetDirtyCount();
		table.clear();
		require(context, table.isEmpty(), "clear left a Strengthening Table slot populated");
		require(context, table.getDirtyCount() == 1, "clearing populated slots did not mark dirty exactly once");
		table.clear();
		require(context, table.getDirtyCount() == 1, "clearing an already empty table marked dirty");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void strengtheningTableCollisionStaysWithinTheBase(TestContext context) {
		BlockPos relativePos = new BlockPos(1, 1, 1);
		BlockPos absolutePos = context.getAbsolutePos(relativePos);
		for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			BlockState state = Magic.STRENGTHENING_TABLE.getDefaultState().with(StrengtheningTableBlock.FACING, facing);
			context.setBlockState(relativePos, state);
			VoxelShape collision = state.getCollisionShape(context.getWorld(), absolutePos, ShapeContext.absent());
			VoxelShape outline = state.getOutlineShape(context.getWorld(), absolutePos, ShapeContext.absent());

			require(context, !collision.isEmpty(), "strengthening table collision was empty for " + facing);
			require(context, collision.getMin(Direction.Axis.X) >= -EPSILON, "collision escaped the block on min X for " + facing);
			require(context, collision.getMax(Direction.Axis.X) <= 1.0D + EPSILON, "collision escaped the block on max X for " + facing);
			require(context, collision.getMin(Direction.Axis.Z) >= -EPSILON, "collision escaped the block on min Z for " + facing);
			require(context, collision.getMax(Direction.Axis.Z) <= 1.0D + EPSILON, "collision escaped the block on max Z for " + facing);
			require(context, collision.getMin(Direction.Axis.Y) >= -EPSILON, "collision escaped below the base for " + facing);
			require(context, collision.getMax(Direction.Axis.Y) <= 12.0D / 16.0D + EPSILON, "collision exceeded the base for " + facing);
			require(context, outline.getMax(Direction.Axis.Y) <= 12.0D / 16.0D + EPSILON, "outline exceeded the base for " + facing);
		}
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
	public void strengtheningTableUpgradeEventsStackAcrossWireTypesAndRejectInvalidData(TestContext context) {
		StrengtheningTableBlockEntity table = new StrengtheningTableBlockEntity(
				BlockPos.ORIGIN,
				Magic.STRENGTHENING_TABLE.getDefaultState()
		);

		require(context, table.onSyncedBlockEvent(1, 1), "minimum valid upgrade event was rejected");
		require(context, table.getUpgradeAnimationBurstCount() == 1, "first same-tick upgrade did not start one burst");
		require(context, table.onSyncedBlockEvent(2, 11), "second wire event type was rejected");
		require(context, table.getUpgradeAnimationBurstCount() == 2, "second same-tick upgrade did not stack a burst");
		require(context, table.onSyncedBlockEvent(255, 40), "maximum wire event type or level was rejected");
		require(context, table.getUpgradeAnimationBurstCount() == 3, "third same-tick upgrade did not reach the burst cap");
		require(context, table.getUpgradeAnimationTotalLevel() == 40, "maximum level was not retained by the animation state");

		require(context, table.onSyncedBlockEvent(254, 30), "valid event after the burst cap was rejected");
		require(context, table.getUpgradeAnimationBurstCount() == 3, "same-tick burst count exceeded its cap");
		require(context, table.getUpgradeAnimationTotalLevel() == 30, "latest valid event did not update the animation level");

		assertRejectedEventDoesNotMutate(context, table, 0, 20, "zero event type");
		assertRejectedEventDoesNotMutate(context, table, 256, 20, "event type above the byte range");
		assertRejectedEventDoesNotMutate(context, table, 1, 0, "zero total level");
		assertRejectedEventDoesNotMutate(context, table, 1, 41, "total level above the supported range");
		context.complete();
	}

	private static void assertRejectedEventDoesNotMutate(
			TestContext context,
			StrengtheningTableBlockEntity table,
			int type,
			int data,
			String description
	) {
		int levelBefore = table.getUpgradeAnimationTotalLevel();
		int burstsBefore = table.getUpgradeAnimationBurstCount();
		long startTickBefore = table.getUpgradeAnimationStartTick();
		require(context, !table.onSyncedBlockEvent(type, data), description + " was unexpectedly accepted");
		require(context, table.getUpgradeAnimationTotalLevel() == levelBefore, description + " changed the animation level");
		require(context, table.getUpgradeAnimationBurstCount() == burstsBefore, description + " changed the burst count");
		require(context, table.getUpgradeAnimationStartTick() == startTickBefore, description + " changed the animation tick");
	}

	private static void require(TestContext context, boolean condition, String message) {
		if (!condition) {
			context.throwGameTestException(message);
		}
	}

	private static final class CountingStrengtheningTableBlockEntity extends StrengtheningTableBlockEntity {
		private int dirtyCount;

		private CountingStrengtheningTableBlockEntity() {
			super(BlockPos.ORIGIN, Magic.STRENGTHENING_TABLE.getDefaultState());
		}

		@Override
		public void markDirty() {
			dirtyCount++;
		}

		private int getDirtyCount() {
			return dirtyCount;
		}

		private void resetDirtyCount() {
			dirtyCount = 0;
		}
	}
}
