package com.zoyluo.magic.block;

import com.zoyluo.magic.Magic;
import com.zoyluo.magic.inventory.ImplementedInventory;
import com.zoyluo.magic.screen.StrengtheningTableScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class StrengtheningTableBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, ImplementedInventory {
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

	public StrengtheningTableBlockEntity(BlockPos pos, BlockState state) {
		super(Magic.STRENGTHENING_TABLE_BLOCK_ENTITY, pos, state);
	}

	@Override
	public DefaultedList<ItemStack> getItems() {
		return items;
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
		Inventories.readNbt(nbt, items, registryLookup);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		Inventories.writeNbt(nbt, items, registryLookup);
	}
}
