package com.zoyluo.magic.screen;

import com.zoyluo.magic.Magic;
import com.zoyluo.magic.block.StrengtheningTableBlockEntity;
import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class StrengtheningTableScreenHandler extends ScreenHandler {
	private static final int INPUT_SLOT = 0;
	private static final int MATERIAL_SLOT = 1;
	private static final int TABLE_INVENTORY_SIZE = 2;
	private static final int PLAYER_INVENTORY_START = 2;
	private static final int PLAYER_INVENTORY_END = 29;
	private static final int HOTBAR_START = 29;
	private static final int HOTBAR_END = 38;

	private final Inventory inventory;
	private final PlayerInventory playerInventory;
	private final ScreenHandlerContext context;

	public StrengtheningTableScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(TABLE_INVENTORY_SIZE), BlockPos.ORIGIN);
	}

	public StrengtheningTableScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos pos) {
		super(Magic.STRENGTHENING_TABLE_SCREEN_HANDLER, syncId);
		checkSize(inventory, TABLE_INVENTORY_SIZE);
		this.inventory = inventory;
		this.playerInventory = playerInventory;
		this.context = ScreenHandlerContext.create(playerInventory.player.getWorld(), pos);

		inventory.onOpen(playerInventory.player);

		addSlot(new Slot(inventory, INPUT_SLOT, 44, 22) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return EnhancementSystem.isUpgradeableTool(stack);
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});

		addSlot(new Slot(inventory, MATERIAL_SLOT, 116, 22) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return !EnhancementSystem.isUpgradeableTool(stack);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 132 + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 190));
		}
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(context, player, Magic.STRENGTHENING_TABLE);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		EnhancementType type = EnhancementType.byButtonId(id);
		if (type == null) {
			return false;
		}

		EnhancementSystem.UpgradeRequirement requirement = EnhancementSystem.getNextRequirement(getToolStack(), type);
		if (requirement == null || !hasSelectedRequiredMaterial(requirement) || !hasEnoughMaterial(requirement)) {
			return false;
		}

		if (!player.isCreative() && !consumeMaterial(requirement)) {
			return false;
		}
		EnhancementSystem.setLevel(getToolStack(), type, requirement.nextLevel());
		inventory.markDirty();
		context.run((world, pos) -> {
			if (world.getBlockEntity(pos) instanceof StrengtheningTableBlockEntity table) {
				table.triggerUpgradeAnimation(requirement.nextLevel().totalLevels());
			}
		});
		sendContentUpdates();
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = slots.get(slotIndex);
		if (slot == null || !slot.hasStack()) {
			return original;
		}

		ItemStack stack = slot.getStack();
		original = stack.copy();

		if (slotIndex < TABLE_INVENTORY_SIZE) {
			if (!insertItem(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (EnhancementSystem.isUpgradeableTool(stack)) {
			if (!insertItem(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!insertItem(stack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}

		return original;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		inventory.onClose(player);
	}

	public ItemStack getToolStack() {
		return inventory.getStack(INPUT_SLOT);
	}

	public ItemStack getMaterialStack() {
		return inventory.getStack(MATERIAL_SLOT);
	}

	public boolean canUpgrade(EnhancementType type) {
		EnhancementSystem.UpgradeRequirement requirement = EnhancementSystem.getNextRequirement(getToolStack(), type);
		return requirement != null && hasSelectedRequiredMaterial(requirement) && hasEnoughMaterial(requirement);
	}

	public EnhancementSystem.UpgradeRequirement getNextRequirement(EnhancementType type) {
		return EnhancementSystem.getNextRequirement(getToolStack(), type);
	}

	private boolean hasSelectedRequiredMaterial(EnhancementSystem.UpgradeRequirement requirement) {
		return getMaterialStack().isOf(requirement.material());
	}

	private boolean hasEnoughMaterial(EnhancementSystem.UpgradeRequirement requirement) {
		return countMaterial(requirement.material()) >= requirement.count();
	}

	private int countMaterial(Item material) {
		int count = 0;
		ItemStack selected = getMaterialStack();
		if (selected.isOf(material)) {
			count += selected.getCount();
		}
		for (int i = 0; i < playerInventory.size(); i++) {
			ItemStack stack = playerInventory.getStack(i);
			if (stack.isOf(material)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private boolean consumeMaterial(EnhancementSystem.UpgradeRequirement requirement) {
		List<StackDeduction> deductions = new ArrayList<>();
		int remaining = requirement.count();
		remaining = planDeduction(getMaterialStack(), requirement.material(), remaining, deductions);
		for (int i = 0; i < playerInventory.size() && remaining > 0; i++) {
			remaining = planDeduction(playerInventory.getStack(i), requirement.material(), remaining, deductions);
		}
		if (remaining > 0) {
			return false;
		}

		for (StackDeduction deduction : deductions) {
			deduction.stack().decrement(deduction.count());
		}
		playerInventory.markDirty();
		return true;
	}

	private int planDeduction(ItemStack stack, Item material, int amount, List<StackDeduction> deductions) {
		if (amount <= 0 || !stack.isOf(material)) {
			return amount;
		}
		int removed = Math.min(stack.getCount(), amount);
		deductions.add(new StackDeduction(stack, removed));
		return amount - removed;
	}

	private record StackDeduction(ItemStack stack, int count) {
	}
}
