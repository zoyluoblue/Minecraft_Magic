package com.zoyluo.magic.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only bridge used to preserve a source layer's texture and render target. */
@Environment(EnvType.CLIENT)
@Mixin(RenderLayer.MultiPhase.class)
public interface RenderLayerMultiPhaseAccessor {
	@Accessor("phases")
	RenderLayer.MultiPhaseParameters magic$getPhases();
}
