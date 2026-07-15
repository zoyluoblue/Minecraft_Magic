package com.zoyluo.magic.client.render;

import com.zoyluo.magic.Magic;
import com.zoyluo.magic.block.StrengtheningTableBlock;
import com.zoyluo.magic.block.StrengtheningTableBlockEntity;
import com.zoyluo.magic.component.EnhancementSystem;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/** Renders the animated astral rings and the two authoritative table slots. */
public final class StrengtheningTableBlockEntityRenderer implements BlockEntityRenderer<StrengtheningTableBlockEntity> {
	private static final Identifier RING_TEXTURE = Magic.id("textures/entity/strengthening_table_ring.png");
	private static final Identifier ENERGY_TEXTURE = Magic.id("textures/entity/strengthening_table_energy.png");
	private static final int FULL_LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
	private static final int RING_SEGMENTS = 32;
	private static final int SUCCESS_MOTES = 12;
	private static final float TAU = (float) (Math.PI * 2.0D);
	private static final float CENTER_Y = 1.18F;
	private static final float SUCCESS_DURATION_TICKS = 26.0F;
	private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
	private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];

	static {
		for (int index = 0; index <= RING_SEGMENTS; index++) {
			float angle = TAU * index / RING_SEGMENTS;
			RING_COS[index] = MathHelper.cos(angle);
			RING_SIN[index] = MathHelper.sin(angle);
		}
	}

	private final ItemRenderer itemRenderer;

	public StrengtheningTableBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.itemRenderer = context.getItemRenderer();
	}

	public static void register() {
		BlockEntityRendererFactories.register(
				Magic.STRENGTHENING_TABLE_BLOCK_ENTITY,
				StrengtheningTableBlockEntityRenderer::new
		);
	}

	@Override
	public void render(
			StrengtheningTableBlockEntity blockEntity,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light,
			int overlay
	) {
		World world = blockEntity.getWorld();
		if (world == null) {
			return;
		}

		double worldTicks = world.getTime() + (double) tickDelta;
		float ticks = Math.floorMod(world.getTime(), 36_000L) + tickDelta;
		float positionPhase = Math.floorMod(blockEntity.getPos().hashCode(), 360);
		VisualStyle idleStyle = styleForStack(blockEntity.getDisplayedTool());
		float animationAge = (float) (worldTicks - blockEntity.getUpgradeAnimationStartTick());
		boolean successActive = blockEntity.getUpgradeAnimationTotalLevel() > 0
				&& animationAge >= 0.0F
				&& animationAge < SUCCESS_DURATION_TICKS;
		float successProgress = successActive ? animationAge / SUCCESS_DURATION_TICKS : 1.0F;
		VisualStyle successStyle = successActive
				? styleForTotalLevel(blockEntity.getUpgradeAnimationTotalLevel())
				: idleStyle;
		float successBlend = successActive ? 1.0F - smoothStep(0.76F, 1.0F, successProgress) : 0.0F;
		VisualStyle activeStyle = blend(idleStyle, successStyle, successBlend);
		float stackedBurst = 1.0F + Math.max(0, blockEntity.getUpgradeAnimationBurstCount() - 1) * 0.22F;
		float ritualPulse = successActive ? MathHelper.sin(successProgress * (float) Math.PI) * stackedBurst : 0.0F;
		float speedMultiplier = 1.0F + ritualPulse * 2.5F;

		VertexConsumer ringVertices = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(RING_TEXTURE));
		int red = activeStyle.red();
		int green = activeStyle.green();
		int blue = activeStyle.blue();
		int baseAlpha = MathHelper.clamp((int) (108.0F + activeStyle.intensity() * 118.0F + ritualPulse * 24.0F), 0, 255);

		matrices.push();
		matrices.translate(0.5F, CENTER_Y, 0.5F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facingRotation(blockEntity.getCachedState().get(StrengtheningTableBlock.FACING))));

		renderRingLayer(matrices, ringVertices, 0.56F, 0.026F, ticks * 0.38F * speedMultiplier + positionPhase, 10.0F, red, green, blue, baseAlpha, overlay);
		renderRingLayer(matrices, ringVertices, 0.49F, 0.022F, -ticks * 0.29F * speedMultiplier + positionPhase * 0.6F + 78.0F, -17.0F, red, green, blue, Math.max(48, baseAlpha - 28), overlay);
		renderRingLayer(matrices, ringVertices, 0.39F, 0.019F, ticks * 0.22F * speedMultiplier - positionPhase * 0.35F, 70.0F, red, green, blue, Math.max(40, baseAlpha - 46), overlay);

		if (successActive) {
			VertexConsumer energyVertices = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(ENERGY_TEXTURE));
			renderSuccessMotes(
					matrices,
					energyVertices,
					successProgress,
					positionPhase,
					blockEntity.getUpgradeAnimationBurstCount(),
					red,
					green,
					blue,
					overlay
			);
		}

		renderDisplayedTool(blockEntity, matrices, vertexConsumers, ticks, positionPhase);
		renderMaterialOrbit(blockEntity, matrices, vertexConsumers, ticks, positionPhase);
		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox(StrengtheningTableBlockEntity blockEntity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 48;
	}

	private void renderDisplayedTool(
			StrengtheningTableBlockEntity blockEntity,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			float ticks,
			float positionPhase
	) {
		ItemStack tool = blockEntity.getDisplayedTool();
		if (tool.isEmpty() || blockEntity.getWorld() == null) {
			return;
		}

		matrices.push();
		float bob = MathHelper.sin((ticks + positionPhase) * 0.08F) * 0.035F;
		matrices.translate(0.0F, bob, 0.0F);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ticks * 0.7F + positionPhase));
		matrices.scale(0.48F, 0.48F, 0.48F);
		itemRenderer.renderItem(
				tool,
				ModelTransformationMode.FIXED,
				FULL_LIGHT,
				OverlayTexture.DEFAULT_UV,
				matrices,
				vertexConsumers,
				blockEntity.getWorld(),
				(int) blockEntity.getPos().asLong()
		);
		matrices.pop();
	}

	private void renderMaterialOrbit(
			StrengtheningTableBlockEntity blockEntity,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			float ticks,
			float positionPhase
	) {
		ItemStack material = blockEntity.getDisplayedMaterial();
		if (material.isEmpty() || blockEntity.getWorld() == null) {
			return;
		}

		for (int index = 0; index < 3; index++) {
			float angle = (ticks * 1.2F + positionPhase + index * 120.0F) * ((float) Math.PI / 180.0F);
			float orbitX = MathHelper.cos(angle) * 0.5F;
			float orbitZ = MathHelper.sin(angle) * 0.5F;
			float orbitY = -0.25F + MathHelper.sin(angle * 1.7F) * 0.035F;

			matrices.push();
			matrices.translate(orbitX, orbitY, orbitZ);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-angle * 180.0F / (float) Math.PI + 90.0F));
			matrices.scale(0.16F, 0.16F, 0.16F);
			itemRenderer.renderItem(
					material,
					ModelTransformationMode.GROUND,
					FULL_LIGHT,
					OverlayTexture.DEFAULT_UV,
					matrices,
					vertexConsumers,
					blockEntity.getWorld(),
					(int) (blockEntity.getPos().asLong() + index * 31L)
			);
			matrices.pop();
		}
	}

	private static void renderRingLayer(
			MatrixStack matrices,
			VertexConsumer vertices,
			float radius,
			float halfWidth,
			float yaw,
			float tilt,
			int red,
			int green,
			int blue,
			int alpha,
			int overlay
	) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));
		renderRing(matrices, vertices, radius, halfWidth, red, green, blue, alpha, overlay);
		matrices.pop();
	}

	private static void renderRing(
			MatrixStack matrices,
			VertexConsumer vertices,
			float radius,
			float halfWidth,
			int red,
			int green,
			int blue,
			int alpha,
			int overlay
	) {
		MatrixStack.Entry entry = matrices.peek();
		float innerRadius = radius - halfWidth;
		float outerRadius = radius + halfWidth;

		for (int index = 0; index < RING_SEGMENTS; index++) {
			float u0 = (float) index / RING_SEGMENTS;
			float u1 = (float) (index + 1) / RING_SEGMENTS;
			float outerX0 = RING_COS[index] * outerRadius;
			float outerY0 = RING_SIN[index] * outerRadius;
			float innerX0 = RING_COS[index] * innerRadius;
			float innerY0 = RING_SIN[index] * innerRadius;
			float outerX1 = RING_COS[index + 1] * outerRadius;
			float outerY1 = RING_SIN[index + 1] * outerRadius;
			float innerX1 = RING_COS[index + 1] * innerRadius;
			float innerY1 = RING_SIN[index + 1] * innerRadius;

			emitVertex(vertices, entry, outerX0, outerY0, 0.0F, red, green, blue, alpha, u0, 0.0F, overlay, 0.0F, 0.0F, 1.0F);
			emitVertex(vertices, entry, innerX0, innerY0, 0.0F, red, green, blue, alpha, u0, 1.0F, overlay, 0.0F, 0.0F, 1.0F);
			emitVertex(vertices, entry, innerX1, innerY1, 0.0F, red, green, blue, alpha, u1, 1.0F, overlay, 0.0F, 0.0F, 1.0F);
			emitVertex(vertices, entry, outerX1, outerY1, 0.0F, red, green, blue, alpha, u1, 0.0F, overlay, 0.0F, 0.0F, 1.0F);

			emitVertex(vertices, entry, outerX1, outerY1, 0.0F, red, green, blue, alpha, u1, 0.0F, overlay, 0.0F, 0.0F, -1.0F);
			emitVertex(vertices, entry, innerX1, innerY1, 0.0F, red, green, blue, alpha, u1, 1.0F, overlay, 0.0F, 0.0F, -1.0F);
			emitVertex(vertices, entry, innerX0, innerY0, 0.0F, red, green, blue, alpha, u0, 1.0F, overlay, 0.0F, 0.0F, -1.0F);
			emitVertex(vertices, entry, outerX0, outerY0, 0.0F, red, green, blue, alpha, u0, 0.0F, overlay, 0.0F, 0.0F, -1.0F);
		}
	}

	private static void renderSuccessMotes(
			MatrixStack matrices,
			VertexConsumer vertices,
			float progress,
			float positionPhase,
			int burstCount,
			int red,
			int green,
			int blue,
			int overlay
	) {
		boolean converging = progress < 0.44F;
		float phaseProgress = converging
				? smoothStep(0.0F, 0.44F, progress)
				: smoothStep(0.44F, 1.0F, progress);
		float radius = converging
				? MathHelper.lerp(phaseProgress, 0.72F, 0.08F)
				: MathHelper.lerp(phaseProgress, 0.08F, 0.78F);
		float alphaFactor = converging ? 0.45F + phaseProgress * 0.55F : 1.0F - phaseProgress;
		int alpha = MathHelper.clamp((int) (alphaFactor * 245.0F), 0, 255);
		float size = converging ? 0.035F : MathHelper.lerp(phaseProgress, 0.055F, 0.018F);

		int moteCount = SUCCESS_MOTES + Math.max(0, burstCount - 1) * 6;
		for (int index = 0; index < moteCount; index++) {
			float angle = TAU * index / moteCount + positionPhase * ((float) Math.PI / 180.0F);
			float vertical = MathHelper.sin(angle * 2.0F + index * 0.37F) * radius * 0.48F;
			matrices.push();
			matrices.translate(MathHelper.cos(angle) * radius, vertical, MathHelper.sin(angle) * radius);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(index * 31.0F + progress * 180.0F));
			renderMote(matrices, vertices, size, red, green, blue, alpha, overlay);
			matrices.pop();
		}
	}

	private static void renderMote(
			MatrixStack matrices,
			VertexConsumer vertices,
			float size,
			int red,
			int green,
			int blue,
			int alpha,
			int overlay
	) {
		MatrixStack.Entry entry = matrices.peek();
		emitDoubleSidedQuad(vertices, entry, -size, -size, 0.0F, size, -size, 0.0F, size, size, 0.0F, -size, size, 0.0F, red, green, blue, alpha, overlay, 0.0F, 0.0F, 1.0F);
		emitDoubleSidedQuad(vertices, entry, -size, 0.0F, -size, size, 0.0F, -size, size, 0.0F, size, -size, 0.0F, size, red, green, blue, alpha, overlay, 0.0F, 1.0F, 0.0F);
		emitDoubleSidedQuad(vertices, entry, 0.0F, -size, -size, 0.0F, size, -size, 0.0F, size, size, 0.0F, -size, size, red, green, blue, alpha, overlay, 1.0F, 0.0F, 0.0F);
	}

	private static void emitDoubleSidedQuad(
			VertexConsumer vertices,
			MatrixStack.Entry entry,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			int red, int green, int blue, int alpha,
			int overlay,
			float normalX, float normalY, float normalZ
	) {
		emitVertex(vertices, entry, x0, y0, z0, red, green, blue, alpha, 0.0F, 1.0F, overlay, normalX, normalY, normalZ);
		emitVertex(vertices, entry, x1, y1, z1, red, green, blue, alpha, 1.0F, 1.0F, overlay, normalX, normalY, normalZ);
		emitVertex(vertices, entry, x2, y2, z2, red, green, blue, alpha, 1.0F, 0.0F, overlay, normalX, normalY, normalZ);
		emitVertex(vertices, entry, x3, y3, z3, red, green, blue, alpha, 0.0F, 0.0F, overlay, normalX, normalY, normalZ);
		emitVertex(vertices, entry, x3, y3, z3, red, green, blue, alpha, 0.0F, 0.0F, overlay, -normalX, -normalY, -normalZ);
		emitVertex(vertices, entry, x2, y2, z2, red, green, blue, alpha, 1.0F, 0.0F, overlay, -normalX, -normalY, -normalZ);
		emitVertex(vertices, entry, x1, y1, z1, red, green, blue, alpha, 1.0F, 1.0F, overlay, -normalX, -normalY, -normalZ);
		emitVertex(vertices, entry, x0, y0, z0, red, green, blue, alpha, 0.0F, 1.0F, overlay, -normalX, -normalY, -normalZ);
	}

	private static void emitVertex(
			VertexConsumer vertices,
			MatrixStack.Entry entry,
			float x,
			float y,
			float z,
			int red,
			int green,
			int blue,
			int alpha,
			float u,
			float v,
			int overlay,
			float normalX,
			float normalY,
			float normalZ
	) {
		vertices.vertex(entry, x, y, z)
				.color(red, green, blue, alpha)
				.texture(u, v)
				.overlay(overlay)
				.light(FULL_LIGHT)
				.normal(entry, normalX, normalY, normalZ);
	}

	private static VisualStyle styleForStack(ItemStack stack) {
		return styleForTotalLevel(EnhancementSystem.getHighestApplicableLevel(stack).totalLevels());
	}

	private static VisualStyle styleForTotalLevel(int totalLevel) {
		if (totalLevel <= 0) {
			return VisualStyle.EMPTY;
		}
		int safeLevel = Math.min(StrengtheningTableBlockEntity.MAX_TOTAL_LEVEL, totalLevel);
		int major = (safeLevel - 1) / EnhancementSystem.MAX_MINOR + 1;
		int minor = (safeLevel - 1) % EnhancementSystem.MAX_MINOR + 1;
		int rgb = switch (major) {
			case 1 -> 0x4EA8FF;
			case 2 -> 0xA970FF;
			case 3 -> 0xFF70C8;
			case 4 -> 0xFF9A45;
			default -> 0xA8E9FF;
		};
		return new VisualStyle(rgb, 0.48F + (minor - 1) * 0.052F);
	}

	private static VisualStyle blend(VisualStyle from, VisualStyle to, float amount) {
		float safeAmount = MathHelper.clamp(amount, 0.0F, 1.0F);
		int red = Math.round(MathHelper.lerp(safeAmount, from.red(), to.red()));
		int green = Math.round(MathHelper.lerp(safeAmount, from.green(), to.green()));
		int blue = Math.round(MathHelper.lerp(safeAmount, from.blue(), to.blue()));
		int rgb = red << 16 | green << 8 | blue;
		return new VisualStyle(rgb, MathHelper.lerp(safeAmount, from.intensity(), to.intensity()));
	}

	private static float smoothStep(float edge0, float edge1, float value) {
		float normalized = MathHelper.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return normalized * normalized * (3.0F - 2.0F * normalized);
	}

	private static float facingRotation(Direction facing) {
		return switch (facing) {
			case EAST -> 90.0F;
			case SOUTH -> 180.0F;
			case WEST -> 270.0F;
			default -> 0.0F;
		};
	}

	private record VisualStyle(int rgb, float intensity) {
		private static final VisualStyle EMPTY = new VisualStyle(0xA8E9FF, 0.46F);

		private int red() {
			return rgb >> 16 & 0xFF;
		}

		private int green() {
			return rgb >> 8 & 0xFF;
		}

		private int blue() {
			return rgb & 0xFF;
		}
	}
}
