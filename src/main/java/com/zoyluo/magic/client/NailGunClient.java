package com.zoyluo.magic.client;

import com.zoyluo.magic.network.NailGunActionPayload;
import com.zoyluo.magic.network.NailGunStatePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class NailGunClient {
	private static final int LINE_RED = 0;
	private static final int LINE_GREEN = 0;
	private static final int LINE_BLUE = 0;
	private static final int LINE_ALPHA = 245;
	private static final double THICK_LINE_OFFSET = 0.018D;
	private static final int SHOT_FLASH_TICKS = 8;

	private static boolean hookActive;
	private static boolean pulling;
	private static boolean altOneWasDown;
	private static boolean jumpWasDown;
	private static boolean escapeWasDown;
	private static int shotFlashTicks;
	private static Vec3d target = Vec3d.ZERO;

	private NailGunClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(NailGunStatePayload.ID, (payload, context) -> {
			hookActive = payload.active();
			pulling = false;
			target = payload.target();
			shotFlashTicks = payload.active() ? SHOT_FLASH_TICKS : 0;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(NailGunClient::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(NailGunClient::renderLine);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null || client.getWindow() == null) {
			clear();
			return;
		}

		long window = client.getWindow().getHandle();
		boolean altOneDown = isAltDown(window) && isKeyDown(window, GLFW.GLFW_KEY_1);
		boolean jumpDown = client.options.jumpKey.isPressed();
		boolean escapeDown = isKeyDown(window, GLFW.GLFW_KEY_ESCAPE);

		if (altOneDown && !altOneWasDown && client.currentScreen == null) {
			if (hookActive) {
				sendAction(NailGunActionPayload.Action.FIRE_TOGGLE);
				clear();
			} else {
				sendAction(NailGunActionPayload.Action.FIRE_TOGGLE);
			}
		}

		if (hookActive && escapeDown && !escapeWasDown) {
			sendAction(NailGunActionPayload.Action.CANCEL);
			clear();
			if (client.currentScreen != null) {
				client.setScreen(null);
			}
		}

		if (hookActive && !pulling && jumpDown && !jumpWasDown && client.currentScreen == null) {
			sendAction(NailGunActionPayload.Action.PULL);
			pulling = true;
		}

		if (hookActive) {
			stopLocalMovement(client.player);
		}
		if (shotFlashTicks > 0) {
			shotFlashTicks--;
		}

		altOneWasDown = altOneDown;
		jumpWasDown = jumpDown;
		escapeWasDown = escapeDown;
	}

	private static void stopLocalMovement(ClientPlayerEntity player) {
		if (player.input != null) {
			player.input.movementForward = 0.0F;
			player.input.movementSideways = 0.0F;
		}
		if (!pulling) {
			player.setVelocity(Vec3d.ZERO);
		}
	}

	private static void renderLine(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!hookActive || client.player == null || client.options.hudHidden || context.matrixStack() == null || context.consumers() == null) {
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(false);
		Vec3d start = client.player.getLeashPos(tickDelta).add(0.0D, -0.2D, 0.0D);
		Vec3d line = target.subtract(start);
		if (line.lengthSquared() < 1.0E-4D) {
			return;
		}

		Vec3d camera = context.camera().getPos();
		Vec3d from = start.subtract(camera);
		Vec3d to = target.subtract(camera);
		Vec3d direction = line.normalize();
		Vec3d side = perpendicular(direction);
		Vec3d up = side.crossProduct(direction).normalize();
		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();
		VertexConsumer vertices = consumers.getBuffer(RenderLayer.getLines());
		MatrixStack.Entry entry = matrices.peek();
		drawCable(vertices, entry, from, to, direction, side, up);
		drawGunHead(vertices, entry, from, direction, side, up);
		drawNailHead(vertices, entry, to, direction, side, up);
		drawShotFlash(vertices, entry, from, direction, side, up);
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

	private static void drawShotFlash(VertexConsumer vertices, MatrixStack.Entry entry, Vec3d start, Vec3d direction, Vec3d side, Vec3d up) {
		if (shotFlashTicks <= 0) {
			return;
		}

		double progress = shotFlashTicks / (double) SHOT_FLASH_TICKS;
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

	private static void sendAction(NailGunActionPayload.Action action) {
		if (ClientPlayNetworking.canSend(NailGunActionPayload.ID)) {
			ClientPlayNetworking.send(new NailGunActionPayload(action));
		}
	}

	private static void clear() {
		hookActive = false;
		pulling = false;
		shotFlashTicks = 0;
		target = Vec3d.ZERO;
	}
}
