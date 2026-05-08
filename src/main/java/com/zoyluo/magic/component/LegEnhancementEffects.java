package com.zoyluo.magic.component;

import com.zoyluo.magic.Magic;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class LegEnhancementEffects {
	private static final Identifier SPEED_MODIFIER_ID = Magic.id("legs_speed");
	private static final Identifier HEIGHT_MODIFIER_ID = Magic.id("legs_height");

	private LegEnhancementEffects() {
	}

	public static void tickPlayer(PlayerEntity player) {
		ItemStack leggings = player.getEquippedStack(EquipmentSlot.LEGS);
		updateAttribute(player, EntityAttributes.MOVEMENT_SPEED, SPEED_MODIFIER_ID, getSpeedBonus(player));
		updateAttribute(player, EntityAttributes.JUMP_STRENGTH, HEIGHT_MODIFIER_ID, EnhancementSystem.getBootPercentBonus(leggings, EnhancementType.HEIGHT));
	}

	public static double getSpeedBonus(PlayerEntity player) {
		return EnhancementSystem.getBootPercentBonus(player.getEquippedStack(EquipmentSlot.LEGS), EnhancementType.SPEED);
	}

	public static double getFallDamageReduction(PlayerEntity player) {
		ItemStack leggings = player.getEquippedStack(EquipmentSlot.LEGS);
		int levels = EnhancementSystem.getLevel(leggings, EnhancementType.HEIGHT).totalLevels();
		return Math.min(0.8D, levels * 0.02D);
	}

	private static void updateAttribute(PlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double value) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}

		EntityAttributeModifier current = instance.getModifier(id);
		if (value <= 0.0D) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}

		if (current != null && Double.compare(current.value(), value) == 0) {
			return;
		}

		instance.removeModifier(id);
		instance.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}
}
