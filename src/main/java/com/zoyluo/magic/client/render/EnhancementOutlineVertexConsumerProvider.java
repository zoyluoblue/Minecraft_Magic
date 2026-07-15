package com.zoyluo.magic.client.render;

import com.zoyluo.magic.component.EnhancementGlowProfile;
import com.zoyluo.magic.mixin.client.RenderLayerMultiPhaseAccessor;
import com.zoyluo.magic.mixin.client.RenderLayerMultiPhaseParametersAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.ColorHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/** Captures only a solid tier-colored silhouette; the original provider remains untouched. */
@Environment(EnvType.CLIENT)
public final class EnhancementOutlineVertexConsumerProvider implements VertexConsumerProvider {
	private static final int OUTLINE_BUFFER_SIZE = 256 * 1024;
	private static final BufferAllocator OUTLINE_ALLOCATOR = new BufferAllocator(OUTLINE_BUFFER_SIZE);
	private static final VertexConsumerProvider.Immediate OUTLINE_BUFFERS = VertexConsumerProvider.immediate(OUTLINE_ALLOCATOR);
	private static final VertexConsumer NO_OP = new NoOpVertexConsumer();

	private final EnhancementGlowProfile profile;
	private final Set<RenderLayer> usedLayers = new LinkedHashSet<>();

	public EnhancementOutlineVertexConsumerProvider(EnhancementGlowProfile profile) {
		this.profile = profile;
	}

	public static boolean isOutlinePass(VertexConsumerProvider provider) {
		return provider instanceof EnhancementOutlineVertexConsumerProvider;
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer sourceLayer) {
		if (isVanillaGlint(sourceLayer) || sourceLayer.getDrawMode() != net.minecraft.client.render.VertexFormat.DrawMode.QUADS) {
			return NO_OP;
		}

		LayerSource source = describe(sourceLayer);
		if (source == null || source.texture() == RenderPhase.NO_TEXTURE) {
			return NO_OP;
		}

		RenderLayer outlineLayer = EnhancementOutlineRenderLayer.get(source.texture(), source.target());
		usedLayers.add(outlineLayer);
		return new OutlineVertexConsumer(
				OUTLINE_BUFFERS.getBuffer(outlineLayer),
				profile
		);
	}

	public void flush() {
		for (RenderLayer layer : usedLayers) {
			OUTLINE_BUFFERS.draw(layer);
		}
		usedLayers.clear();
	}

	private static LayerSource describe(RenderLayer layer) {
		if (!(layer instanceof RenderLayer.MultiPhase multiPhase)) {
			return null;
		}

		RenderLayer.MultiPhaseParameters parameters =
				((RenderLayerMultiPhaseAccessor) (Object) multiPhase).magic$getPhases();
		RenderLayerMultiPhaseParametersAccessor accessor =
				(RenderLayerMultiPhaseParametersAccessor) (Object) parameters;
		return new LayerSource(accessor.magic$getTexture(), accessor.magic$getTarget());
	}

	private static boolean isVanillaGlint(RenderLayer layer) {
		return layer == RenderLayer.getArmorEntityGlint()
				|| layer == RenderLayer.getGlintTranslucent()
				|| layer == RenderLayer.getGlint()
				|| layer == RenderLayer.getEntityGlint();
	}

	private record LayerSource(RenderPhase.TextureBase texture, RenderPhase.Target target) {
	}

	private static final class OutlineVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int color;

		private OutlineVertexConsumer(
				VertexConsumer delegate,
				EnhancementGlowProfile profile
		) {
			this.delegate = delegate;
			this.color = ColorHelper.getArgb(
					Math.round(profile.outlineOpacity() * 255.0F),
					profile.red(),
					profile.green(),
					profile.blue()
			);
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			delegate.vertex(x, y, z).color(color);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			delegate.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}

		@Override
		public void vertex(
				float x,
				float y,
				float z,
				int ignoredColor,
				float u,
				float v,
				int ignoredOverlay,
				int ignoredLight,
				float normalX,
				float normalY,
				float normalZ
		) {
			delegate.vertex(x, y, z).color(color).texture(u, v);
		}
	}

	private static final class NoOpVertexConsumer implements VertexConsumer {
		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}
	}
}
