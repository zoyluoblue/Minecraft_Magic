package com.zoyluo.magic.client.gui;

import com.zoyluo.magic.component.EnhancementSystem;
import com.zoyluo.magic.component.EnhancementType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HelmetOreLocator {
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final float OVERLAY_SCALE = 0.34F;
	private static final int OVERLAY_TOP = 7;
	private static final int OVERLAY_RIGHT_MARGIN = 7;
	private static final int ARROW_BOUNDING_RADIUS = 21;
	private static final Set<OreTarget> selectedTargets = new LinkedHashSet<>();
	@Nullable
	private static LocatedOre locatedOre;
	private static int nextScanTick;

	private HelmetOreLocator() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HelmetOreLocator::tick);
		HudRenderCallback.EVENT.register(HelmetOreLocator::render);
	}

	public static List<OreSelectionEntry> collectSelectionEntries(PlayerEntity player) {
		ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
		if (EnhancementSystem.getLevel(helmet, EnhancementType.ORE_SEEKER).isEmpty()) {
			return List.of();
		}

		List<OreSelectionEntry> entries = new ArrayList<>();
		for (OreTarget target : OreTarget.values()) {
			entries.add(new OreSelectionEntry(
					target.id(),
					Text.translatable("gui.magic.ore_target", Text.translatable(target.translationKey())),
					selectedTargets.contains(target)
			));
		}
		return entries;
	}

	public static void setSelectedOreTargets(Set<String> targetIds) {
		selectedTargets.clear();
		for (OreTarget target : OreTarget.values()) {
			if (targetIds.contains(target.id())) {
				selectedTargets.add(target);
			}
		}
		locatedOre = null;
		nextScanTick = 0;
	}

	public static void clearSelectedOreTargets() {
		selectedTargets.clear();
		locatedOre = null;
		nextScanTick = 0;
	}

	private static void tick(MinecraftClient client) {
		PlayerEntity player = client.player;
		if (player == null || client.world == null) {
			locatedOre = null;
			return;
		}

		if (player.age < nextScanTick) {
			return;
		}
		nextScanTick = player.age + SCAN_INTERVAL_TICKS;
		locatedOre = findNearestOre(client.world, player);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || client.options.hudHidden || locatedOre == null) {
			return;
		}

		ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
		double range = EnhancementSystem.getOreSearchRange(helmet);
		if (range <= 0.0D || selectedTargets.isEmpty()) {
			return;
		}

		double distance = Math.sqrt(player.getPos().squaredDistanceTo(locatedOre.pos().toCenterPos()));
		TextRenderer textRenderer = client.textRenderer;
		Text label = Text.translatable(
				"hud.magic.ore_locator",
				Text.translatable(locatedOre.target().translationKey()),
				String.format(Locale.ROOT, "%.1f", distance),
				verticalText(player, locatedOre.pos())
		);
		int textWidth = textRenderer.getWidth(label);
		int contentHalfWidth = Math.max((textWidth + 1) / 2, ARROW_BOUNDING_RADIUS);
		int scaledHalfWidth = MathHelper.ceil(contentHalfWidth * OVERLAY_SCALE);
		int anchorX = context.getScaledWindowWidth() - OVERLAY_RIGHT_MARGIN - scaledHalfWidth;
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(anchorX, OVERLAY_TOP, 0.0D);
		matrices.scale(OVERLAY_SCALE, OVERLAY_SCALE, 1.0F);
		drawArrow(context, 0, 10, getRelativeAngle(player, locatedOre.pos()));
		context.drawText(textRenderer, label, -textWidth / 2, 23, 0xCCFDE68A, false);
		matrices.pop();
	}

	@Nullable
	private static LocatedOre findNearestOre(ClientWorld world, PlayerEntity player) {
		ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
		double range = EnhancementSystem.getOreSearchRange(helmet);
		if (range <= 0.0D || selectedTargets.isEmpty()) {
			return null;
		}

		int radius = MathHelper.ceil(range);
		int minX = MathHelper.floor(player.getX() - range);
		int maxX = MathHelper.floor(player.getX() + range);
		int minY = Math.max(world.getBottomY(), MathHelper.floor(player.getY() - range));
		int maxY = Math.min(world.getTopYInclusive(), MathHelper.floor(player.getY() + range));
		int minZ = MathHelper.floor(player.getZ() - range);
		int maxZ = MathHelper.floor(player.getZ() + range);
		int minChunkX = ChunkSectionPos.getSectionCoord(minX);
		int maxChunkX = ChunkSectionPos.getSectionCoord(maxX);
		int minChunkZ = ChunkSectionPos.getSectionCoord(minZ);
		int maxChunkZ = ChunkSectionPos.getSectionCoord(maxZ);
		int minSectionY = ChunkSectionPos.getSectionCoord(minY);
		int maxSectionY = ChunkSectionPos.getSectionCoord(maxY);
		Vec3d playerPos = player.getPos();
		LocatedOre best = null;
		double bestDistanceSq = Double.MAX_VALUE;
		Set<OreTarget> targets = EnumSet.copyOf(selectedTargets);

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				LocatedOre ore = scanChunk(world, chunk, targets, playerPos, radius, minX, maxX, minY, maxY, minZ, maxZ, minSectionY, maxSectionY, bestDistanceSq);
				if (ore != null) {
					best = ore;
					bestDistanceSq = playerPos.squaredDistanceTo(ore.pos().toCenterPos());
				}
			}
		}
		return best;
	}

	@Nullable
	private static LocatedOre scanChunk(
			ClientWorld world,
			WorldChunk chunk,
			Set<OreTarget> targets,
			Vec3d playerPos,
			int radius,
			int minX,
			int maxX,
			int minY,
			int maxY,
			int minZ,
			int maxZ,
			int minSectionY,
			int maxSectionY,
			double bestDistanceSq
	) {
		LocatedOre best = null;
		int chunkStartX = chunk.getPos().getStartX();
		int chunkStartZ = chunk.getPos().getStartZ();
		int localMinX = Math.max(minX, chunkStartX) - chunkStartX;
		int localMaxX = Math.min(maxX, chunkStartX + 15) - chunkStartX;
		int localMinZ = Math.max(minZ, chunkStartZ) - chunkStartZ;
		int localMaxZ = Math.min(maxZ, chunkStartZ + 15) - chunkStartZ;

		for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
			int sectionIndex = world.sectionCoordToIndex(sectionY);
			if (sectionIndex < 0 || sectionIndex >= chunk.getSectionArray().length) {
				continue;
			}

			ChunkSection section = chunk.getSection(sectionIndex);
			if (section == null || section.isEmpty() || !section.hasAny(state -> matchesAny(targets, state))) {
				continue;
			}

			int sectionStartY = ChunkSectionPos.getBlockCoord(sectionY);
			int localMinY = Math.max(minY, sectionStartY) - sectionStartY;
			int localMaxY = Math.min(maxY, sectionStartY + 15) - sectionStartY;
			for (int localY = localMinY; localY <= localMaxY; localY++) {
				int y = sectionStartY + localY;
				for (int localX = localMinX; localX <= localMaxX; localX++) {
					int x = chunkStartX + localX;
					if (Math.abs(x - playerPos.x) > radius) {
						continue;
					}
					for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
						int z = chunkStartZ + localZ;
						if (Math.abs(z - playerPos.z) > radius || Math.abs(y - playerPos.y) > radius) {
							continue;
						}

						BlockState state = section.getBlockState(localX, localY, localZ);
						OreTarget target = matchingTarget(targets, state);
						if (target == null) {
							continue;
						}

						BlockPos pos = new BlockPos(x, y, z);
						double distanceSq = playerPos.squaredDistanceTo(pos.toCenterPos());
						if (distanceSq < bestDistanceSq) {
							bestDistanceSq = distanceSq;
							best = new LocatedOre(target, pos);
						}
					}
				}
			}
		}
		return best;
	}

	private static boolean matchesAny(Set<OreTarget> targets, BlockState state) {
		return matchingTarget(targets, state) != null;
	}

	@Nullable
	private static OreTarget matchingTarget(Set<OreTarget> targets, BlockState state) {
		for (OreTarget target : targets) {
			if (target.matches(state)) {
				return target;
			}
		}
		return null;
	}

	private static float getRelativeAngle(PlayerEntity player, BlockPos targetPos) {
		double dx = targetPos.getX() + 0.5D - player.getX();
		double dz = targetPos.getZ() + 0.5D - player.getZ();
		float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		return MathHelper.wrapDegrees(targetYaw - player.getYaw());
	}

	private static Text verticalText(PlayerEntity player, BlockPos targetPos) {
		int deltaY = targetPos.getY() - MathHelper.floor(player.getY());
		if (deltaY > 1) {
			return Text.translatable("hud.magic.vertical.above", deltaY);
		}
		if (deltaY < -1) {
			return Text.translatable("hud.magic.vertical.below", Math.abs(deltaY));
		}
		return Text.translatable("hud.magic.vertical.same_level");
	}

	private static void drawArrow(DrawContext context, int centerX, int centerY, float angle) {
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(centerX, centerY, 0.0D);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
		context.fill(-2, -14, 2, 8, 0xAA5EEAD4);
		context.fill(-7, -13, 7, -8, 0xAA5EEAD4);
		context.fill(-5, -17, 5, -12, 0xAA5EEAD4);
		context.fill(-3, -20, 3, -16, 0xAA5EEAD4);
		matrices.pop();
	}

	public enum OreTarget {
		DIAMOND("diamond", "ore.magic.diamond", BlockTags.DIAMOND_ORES, null),
		GOLD("gold", "ore.magic.gold", BlockTags.GOLD_ORES, null),
		IRON("iron", "ore.magic.iron", BlockTags.IRON_ORES, null),
		OBSIDIAN("obsidian", "ore.magic.obsidian", null, Blocks.OBSIDIAN),
		EMERALD("emerald", "ore.magic.emerald", BlockTags.EMERALD_ORES, null);

		private final String id;
		private final String translationKey;
		@Nullable
		private final TagKey<Block> tag;
		@Nullable
		private final Block block;

		OreTarget(String id, String translationKey, @Nullable TagKey<Block> tag, @Nullable Block block) {
			this.id = id;
			this.translationKey = translationKey;
			this.tag = tag;
			this.block = block;
		}

		public String id() {
			return id;
		}

		public String translationKey() {
			return translationKey;
		}

		private boolean matches(BlockState state) {
			if (tag != null) {
				return state.isIn(tag);
			}
			return block != null && state.isOf(block);
		}
	}

	public record OreSelectionEntry(String key, Text text, boolean enabled) {
	}

	private record LocatedOre(OreTarget target, BlockPos pos) {
	}
}
