package com.zoyluo.magic.client.render;

import com.zoyluo.magic.Magic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.HashMap;
import java.util.Map;

/** A translucent, depth-aware silhouette layer that samples texture alpha but ignores texture RGB. */
@Environment(EnvType.CLIENT)
final class EnhancementOutlineRenderLayer {
	private static final ShaderProgramKey PROGRAM_KEY = new ShaderProgramKey(
			Magic.id("core/rendertype_enhancement_outline"),
			VertexFormats.POSITION_TEXTURE_COLOR,
			Defines.EMPTY
	);
	private static final RenderPhase.ShaderProgram PROGRAM = new RenderPhase.ShaderProgram(PROGRAM_KEY);
	private static final Map<LayerKey, RenderLayer> CACHE = new HashMap<>();

	private EnhancementOutlineRenderLayer() {
	}

	static RenderLayer get(RenderPhase.TextureBase texture, RenderPhase.Target target) {
		return CACHE.computeIfAbsent(new LayerKey(texture, target), EnhancementOutlineRenderLayer::create);
	}

	private static RenderLayer create(LayerKey key) {
		return RenderLayer.of(
				"magic_enhancement_outline",
				VertexFormats.POSITION_TEXTURE_COLOR,
				VertexFormat.DrawMode.QUADS,
				RenderLayer.DEFAULT_BUFFER_SIZE,
				false,
				true,
				RenderLayer.MultiPhaseParameters.builder()
						.program(PROGRAM)
						.texture(key.texture())
						.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
						.depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
						.cull(RenderPhase.DISABLE_CULLING)
						.writeMaskState(RenderPhase.COLOR_MASK)
						.target(key.target())
						.build(false)
		);
	}

	private record LayerKey(RenderPhase.TextureBase texture, RenderPhase.Target target) {
	}
}
