package com.zoyluo.magic.block;

import com.zoyluo.magic.Magic;
import com.zoyluo.magic.inventory.ImplementedInventory;
import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class StrengtheningTableBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
	public static final int UPGRADE_EVENT = 1;
	public static final int MIN_TOTAL_LEVEL = 1;
	public static final int MAX_TOTAL_LEVEL = 40;
	public static final int TOOL_SLOT = 0;
	public static final int MATERIAL_SLOT = 1;
	private static final int MAX_UPGRADE_EVENT = 0xFF;
	private static final int MAX_SIMULTANEOUS_BURSTS = 3;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);
	private int upgradeEventSequence;
	private int upgradeAnimationTotalLevel;
	private int upgradeAnimationBurstCount;
	private long upgradeAnimationStartTick = Long.MIN_VALUE;

	public StrengtheningTableBlockEntity(BlockPos pos, BlockState state) {
		super(Magic.STRENGTHENING_TABLE_BLOCK_ENTITY, pos, state);
	}

	@Override
	public DefaultedList<ItemStack> getItems() {
		return items;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack removed = Inventories.removeStack(items, slot);
		if (!removed.isEmpty()) {
			markDirty();
		}
		return removed;
	}

	@Override
	public void clear() {
		if (isEmpty()) {
			return;
		}
		items.clear();
		markDirty();
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("container.magic.strengthening_table");
	}

	@Nullable
	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new StrengtheningTableScreenHandler(syncId, playerInventory, this, pos);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		items.clear();
		Inventories.readNbt(nbt, items, registryLookup);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		Inventories.writeNbt(nbt, items, registryLookup);
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null && !world.isClient) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}

	public void triggerUpgradeAnimation(int totalLevel) {
		if (world == null || world.isClient || totalLevel < MIN_TOTAL_LEVEL || totalLevel > MAX_TOTAL_LEVEL) {
			return;
		}
		upgradeEventSequence = upgradeEventSequence >= MAX_UPGRADE_EVENT ? UPGRADE_EVENT : upgradeEventSequence + 1;
		world.addSyncedBlockEvent(pos, getCachedState().getBlock(), upgradeEventSequence, totalLevel);
	}

	@Override
	public boolean onSyncedBlockEvent(int type, int data) {
		if (type < UPGRADE_EVENT || type > MAX_UPGRADE_EVENT || data < MIN_TOTAL_LEVEL || data > MAX_TOTAL_LEVEL) {
			return super.onSyncedBlockEvent(type, data);
		}

		long eventTick = world == null ? 0L : world.getTime();
		upgradeAnimationBurstCount = eventTick == upgradeAnimationStartTick
				? Math.min(MAX_SIMULTANEOUS_BURSTS, upgradeAnimationBurstCount + 1)
				: 1;
		upgradeAnimationTotalLevel = data;
		upgradeAnimationStartTick = eventTick;
		return true;
	}

	public ItemStack getDisplayedTool() {
		return items.get(TOOL_SLOT);
	}

	public ItemStack getDisplayedMaterial() {
		return items.get(MATERIAL_SLOT);
	}

	public int getUpgradeAnimationTotalLevel() {
		return upgradeAnimationTotalLevel;
	}

	public long getUpgradeAnimationStartTick() {
		return upgradeAnimationStartTick;
	}

	public int getUpgradeAnimationBurstCount() {
		return upgradeAnimationBurstCount;
	}
}
