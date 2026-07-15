package com.zoyluo.magic.client;

import com.zoyluo.magic.network.NailGunCommandPayload;
import com.zoyluo.magic.network.NailGunRuntimePayload;
import com.zoyluo.magic.network.NailGunStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class NailGunClient {
	private static final int LINE_RED = 0;
	private static final int LINE_GREEN = 0;
	private static final int LINE_BLUE = 0;
	private static final int LINE_ALPHA = 245;
	private static final double THICK_LINE_OFFSET = 0.018D;
	private static final int SHOT_FLASH_TICKS = 8;
	private static final int MISSING_OWNER_TTL_TICKS = 40;
	private static final int UNSUPPORTED_WARNING_COOLDOWN_TICKS = 100;

	private static final Map<UUID, ClientHookState> HOOKS = new HashMap<>();
	private static final Map<UUID, Long> LAST_STATE_VERSIONS = new HashMap<>();
	private static boolean altOneWasDown;
	private static boolean jumpWasDown;
	private static boolean escapeWasDown;
	private static long nextRequestSequence = 1L;
	private static long clientTicks;
	private static long lastUnsupportedWarningTick = Long.MIN_VALUE;

	private NailGunClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NailGunRuntimePayload.ID, (payload, context) -> acceptRuntime(payload));
		ClientPlayNetworking.registerGlobalReceiver(NailGunStatePayload.ID, (payload, context) -> {
			// Phase 1 never falls back to the old toggle protocol. An inactive legacy reply only
			// confirms that the server rejected an obsolete command.
			if (!payload.active() && context.client().player != null) {
				HOOKS.remove(context.client().player.getUuid());
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> clearRuntimeStates());
		ClientTickEvents.END_CLIENT_TICK.register(NailGunClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(NailGunClient::renderLines);
	}

	private static void acceptRuntime(NailGunRuntimePayload payload) {
		if (payload.protocolVersion() != NailGunRuntimePayload.PROTOCOL_VERSION || payload.stateVersion() <= 0L) {
			HOOKS.remove(payload.ownerUuid());
			return;
		}

		long previousVersion = LAST_STATE_VERSIONS.getOrDefault(payload.ownerUuid(), 0L);
		if (payload.stateVersion() <= previousVersion) {
			return;
		}
		LAST_STATE_VERSIONS.put(payload.ownerUuid(), payload.stateVersion());
		if (payload.phase() == NailGunRuntimePayload.Phase.RELEASED) {
			HOOKS.remove(payload.ownerUuid());
			return;
		}

		ClientHookState previous = HOOKS.get(payload.ownerUuid());
		int flashTicks = previous == null ? SHOT_FLASH_TICKS : previous.flashTicks;
		HOOKS.put(payload.ownerUuid(), new ClientHookState(
				payload.phase(),
				payload.anchor(),
				clientTicks,
				flashTicks
		));
	}

	private static void tick(MinecraftClient client) {
		clientTicks++;
		if (client.player == null || client.world == null || client.getWindow() == null) {
			clearRuntimeStates();
			return;
		}

		long window = client.getWindow().getHandle();
		boolean altOneDown = isAltDown(window) && isKeyDown(window, GLFW.GLFW_KEY_1);
		boolean jumpDown = client.options.jumpKey.isPressed();
		boolean escapeDown = isKeyDown(window, GLFW.GLFW_KEY_ESCAPE);
		NailGunRuntimePayload.Phase localPhase = localPhase(client);

		if (altOneDown && !altOneWasDown && client.currentScreen == null) {
			sendCommand(client, localPhase == NailGunRuntimePayload.Phase.RELEASED
					? NailGunCommandPayload.Command.FIRE
					: NailGunCommandPayload.Command.RELEASE);
		}

		if (localPhase != NailGunRuntimePayload.Phase.RELEASED
				&& escapeDown
				&& !escapeWasDown
				&& client.currentScreen == null) {
			sendCommand(client, NailGunCommandPayload.Command.RELEASE);
		}

		if (localPhase == NailGunRuntimePayload.Phase.ATTACHED
				&& jumpDown
				&& !jumpWasDown
				&& client.currentScreen == null) {
			sendCommand(client, NailGunCommandPayload.Command.PULL);
		}

		pruneAndAnimate(client);
		altOneWasDown = altOneDown;
		jumpWasDown = jumpDown;
		escapeWasDown = escapeDown;
	}

	private static void pruneAndAnimate(MinecraftClient client) {
		Iterator<Map.Entry<UUID, ClientHookState>> iterator = HOOKS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ClientHookState> entry = iterator.next();
			ClientHookState state = entry.getValue();
			if (findPlayer(client, entry.getKey()) == null && clientTicks - state.lastUpdatedTick > MISSING_OWNER_TTL_TICKS) {
				iterator.remove();
				continue;
			}
			if (state.flashTicks > 0) {
				state.flashTicks--;
			}
		}
	}

	private static NailGunRuntimePayload.Phase localPhase(MinecraftClient client) {
		ClientHookState state = HOOKS.get(client.player.getUuid());
		return state == null ? NailGunRuntimePayload.Phase.RELEASED : state.phase;
	}

	private static void sendCommand(MinecraftClient client, NailGunCommandPayload.Command command) {
		if (!ClientPlayNetworking.canSend(NailGunCommandPayload.ID)) {
			if (lastUnsupportedWarningTick == Long.MIN_VALUE
					|| clientTicks < lastUnsupportedWarningTick
					|| clientTicks - lastUnsupportedWarningTick >= UNSUPPORTED_WARNING_COOLDOWN_TICKS) {
				lastUnsupportedWarningTick = clientTicks;
				client.player.sendMessage(Text.translatable("message.magic.nail_gun.protocol_mismatch"), true);
			}
			return;
		}

		long sequence = nextRequestSequence++;
		if (nextRequestSequence <= 0L) {
			nextRequestSequence = 1L;
		}
		ClientPlayNetworking.send(NailGunCommandPayload.current(sequence, command));
	}

	private static void renderLines(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (HOOKS.isEmpty() || client.world == null || client.options.hudHidden || context.matrixStack() == null || context.consumers() == null) {
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getLines());
		MatrixStack.Entry entry = matrices.peek();
		for (Map.Entry<UUID, ClientHookState> hookEntry : HOOKS.entrySet()) {
			AbstractClientPlayerEntity owner = findPlayer(client, hookEntry.getKey());
			if (owner == null) {
				continue;
			}
			ClientHookState state = hookEntry.getValue();
			Vec3d start = owner.getLeashPos(tickDelta).add(0.0D, -0.2D, 0.0D);
			Vec3d line = state.anchor.subtract(start);
			if (line.lengthSquared() < 1.0E-4D) {
				continue;
			}

			Vec3d from = start.subtract(camera);
			Vec3d to = state.anchor.subtract(camera);
			Vec3d direction = line.normalize();
			Vec3d side = perpendicular(direction);
			Vec3d up = side.crossProduct(direction).normalize();
			drawCable(vertices, entry, from, to, direction, side, up);
			drawGunHead(vertices, entry, from, direction, side, up);
			drawNailHead(vertices, entry, to, direction, side, up);
			drawShotFlash(vertices, entry, from, direction, side, up, state.flashTicks);
		}
	}

	private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
		if (client.world == null) {
			return null;
		}
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player.getUuid().equals(uuid)) {
				return player;
			}
		}
		return null;
	}

	private static void drawCable(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d from, Vec3d to, Vec3d direction, Vec3d side, Vec3d up) {
		drawSegment(vertices, entry, from, to, direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, from.add(side.multiply(THICK_LINE_OFFSET)), to.add(side.multiply(THICK_LINE_OFFSET)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, from.add(side.multiply(-THICK_LINE_OFFSET)), to.add(side.multiply(-THICK_LINE_OFFSET)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, from.add(up.multiply(THICK_LINE_OFFSET)), to.add(up.multiply(THICK_LINE_OFFSET)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, from.add(up.multiply(-THICK_LINE_OFFSET)), to.add(up.multiply(-THICK_LINE_OFFSET)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
	}

	private static void drawGunHead(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d start, Vec3d direction, Vec3d side, Vec3d up) {
		Vec3d barrelStart = start.add(direction.multiply(0.12D));
		Vec3d barrelEnd = start.add(direction.multiply(0.72D));
		Vec3d rear = start.add(direction.multiply(-0.08D));
		drawCable(vertices, entry, barrelStart, barrelEnd, direction, side, up);
		drawSegment(vertices, entry, rear.add(side.multiply(-0.18D)), rear.add(side.multiply(0.18D)), side, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, barrelEnd.add(side.multiply(-0.22D)), barrelEnd.add(side.multiply(0.22D)), side, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, barrelEnd.add(up.multiply(-0.16D)), barrelEnd.add(up.multiply(0.16D)), up, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, rear.add(up.multiply(-0.20D)), start.add(direction.multiply(0.22D)).add(up.multiply(-0.20D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, 210);
		drawSegment(vertices, entry, rear.add(up.multiply(0.20D)), start.add(direction.multiply(0.22D)).add(up.multiply(0.20D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, 210);
	}

	private static void drawNailHead(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d end, Vec3d direction, Vec3d side, Vec3d up) {
		Vec3d tip = end;
		Vec3d shank = end.subtract(direction.multiply(0.54D));
		Vec3d barbBase = end.subtract(direction.multiply(0.22D));
		drawCable(vertices, entry, shank, tip, direction, side, up);
		drawSegment(vertices, entry, tip, barbBase.add(side.multiply(0.22D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, tip, barbBase.add(side.multiply(-0.22D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, tip, barbBase.add(up.multiply(0.18D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
		drawSegment(vertices, entry, tip, barbBase.add(up.multiply(-0.18D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, LINE_ALPHA);
	}

	private static void drawShotFlash(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d start, Vec3d direction, Vec3d side, Vec3d up, int flashTicks) {
		if (flashTicks <= 0) {
			return;
		}

		double progress = flashTicks / (double) SHOT_FLASH_TICKS;
		int alpha = Math.max(40, (int) (180.0D * progress));
		double reach = 0.38D + (1.0D - progress) * 0.28D;
		Vec3d center = start.add(direction.multiply(0.82D));
		drawSegment(vertices, entry, center.add(side.multiply(-reach)), center.add(side.multiply(reach)), side, LINE_RED, LINE_GREEN, LINE_BLUE, alpha);
		drawSegment(vertices, entry, center.add(up.multiply(-reach * 0.7D)), center.add(up.multiply(reach * 0.7D)), up, LINE_RED, LINE_GREEN, LINE_BLUE, alpha);
		drawSegment(vertices, entry, center.subtract(direction.multiply(0.12D)).add(side.multiply(-reach * 0.45D)), center.add(direction.multiply(0.28D)).add(side.multiply(reach * 0.45D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, alpha);
		drawSegment(vertices, entry, center.subtract(direction.multiply(0.12D)).add(side.multiply(reach * 0.45D)), center.add(direction.multiply(0.28D)).add(side.multiply(-reach * 0.45D)), direction, LINE_RED, LINE_GREEN, LINE_BLUE, alpha);
	}

	private static Vec3d perpendicular(Vec3d direction) {
		Vec3d side = direction.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
		if (side.lengthSquared() < 1.0E-4D) {
			side = direction.crossProduct(new Vec3d(1.0D, 0.0D, 0.0D));
		}
		return side.normalize();
	}

	private static void drawSegment(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d from, Vec3d to, Vec3d normal, int red, int green, int blue, int alpha) {
		vertices.vertex(entry, (float) from.x, (float) from.y, (float) from.z).color(red, green, blue, alpha).normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
		vertices.vertex(entry, (float) to.x, (float) to.y, (float) to.z).color(red, green, blue, alpha).normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
	}

	private static boolean isAltDown(long window) {
		return isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
	}

	private static boolean isKeyDown(long window, int key) {
		return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
	}

	private static void clearRuntimeStates() {
		HOOKS.clear();
		LAST_STATE_VERSIONS.clear();
	}

	private static void clear() {
		clearRuntimeStates();
		altOneWasDown = false;
		jumpWasDown = false;
		escapeWasDown = false;
		nextRequestSequence = 1L;
		lastUnsupportedWarningTick = Long.MIN_VALUE;
	}

	private static final class ClientHookState {
		private final NailGunRuntimePayload.Phase phase;
		private final Vec3d anchor;
		private final long lastUpdatedTick;
		private int flashTicks;

		private ClientHookState(NailGunRuntimePayload.Phase phase, Vec3d anchor, long lastUpdatedTick, int flashTicks) {
			this.phase = phase;
			this.anchor = anchor;
			this.lastUpdatedTick = lastUpdatedTick;
			this.flashTicks = flashTicks;
		}
	}
}
