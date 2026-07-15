package com.zoyluo.magic.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only bridge used by the enhancement outline layer factory. */
@Environment(EnvType.CLIENT)
@Mixin(RenderLayer.MultiPhaseParameters.class)
public interface RenderLayerMultiPhaseParametersAccessor {
	@Accessor("texture")
	RenderPhase.TextureBase magic$getTexture();

	@Accessor("target")
	RenderPhase.Target magic$getTarget();
}
