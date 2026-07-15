package com.zoyluo.magic.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.zoyluo.magic.client.render.EnhancementOutlineVertexConsumerProvider;
import com.zoyluo.magic.component.EnhancementGlowProfile;
import com.zoyluo.magic.component.EnhancementSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.spongepowered.asm.mixin.Mixin;

/** Covers GUI, held, dropped, framed, and other vanilla item rendering paths. */
@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	private static final float DIAGONAL = 0.70710677F;
	private static final float[][] OUTLINE_DIRECTIONS = {
			{1.0F, 0.0F},
			{-1.0F, 0.0F},
			{0.0F, 1.0F},
			{0.0F, -1.0F},
			{DIAGONAL, DIAGONAL},
			{DIAGONAL, -DIAGONAL},
			{-DIAGONAL, DIAGONAL},
			{-DIAGONAL, -DIAGONAL}
	};

	@WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;Z)V")
	private void magic$applyEnhancementGlow(
			ItemStack stack,
			ModelTransformationMode transformationMode,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light,
			int overlay,
			BakedModel model,
			boolean useInventoryModel,
			Operation<Void> original
	) {
		EnhancementGlowProfile profile = EnhancementGlowProfile.forLevel(
				EnhancementSystem.getHighestApplicableLevel(stack)
		);
		if (profile.isEmpty() || EnhancementOutlineVertexConsumerProvider.isOutlinePass(vertexConsumers)) {
			original.call(
					stack,
					transformationMode,
					matrices,
					vertexConsumers,
					light,
					overlay,
					model,
					useInventoryModel
			);
			return;
		}

		EnhancementOutlineVertexConsumerProvider outlineConsumers =
				new EnhancementOutlineVertexConsumerProvider(profile);
		float offset = magic$outlineOffset(transformationMode, profile.outlineWidth());
		try {
			for (float[] direction : OUTLINE_DIRECTIONS) {
				matrices.push();
				try {
					matrices.translate(direction[0] * offset, direction[1] * offset, 0.0F);
					original.call(
							stack,
							transformationMode,
							matrices,
							outlineConsumers,
							light,
							overlay,
							model,
							useInventoryModel
					);
				} finally {
					matrices.pop();
				}
			}
		} finally {
			outlineConsumers.flush();
		}

		original.call(
				stack,
				transformationMode,
				matrices,
				vertexConsumers,
				light,
				overlay,
				model,
				useInventoryModel
		);
	}

	private static float magic$outlineOffset(ModelTransformationMode mode, float width) {
		return switch (mode) {
			case GUI -> 0.04F + width * 0.055F;
			case GROUND, FIXED -> 0.015F + width * 0.035F;
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.010F + width * 0.027F;
			case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.010F + width * 0.024F;
			default -> 0.010F + width * 0.022F;
		};
	}
}
