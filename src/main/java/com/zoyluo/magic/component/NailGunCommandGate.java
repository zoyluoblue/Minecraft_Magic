package com.zoyluo.magic.component;

import com.zoyluo.magic.network.NailGunCommandPayload;

public final class NailGunCommandGate {
	public static final int WINDOW_TICKS = 20;
	public static final int MAX_COMMANDS_PER_WINDOW = 8;
	public static final int FIRE_COOLDOWN_TICKS = 4;
	private static final int REPLY_COOLDOWN_TICKS = 5;

	private long lastObservedSequence;
	private long windowStartTick = Long.MIN_VALUE;
	private int commandsInWindow;
	private long lastFireTick = Long.MIN_VALUE;
	private long lastReplyTick = Long.MIN_VALUE;

	public Decision evaluate(long requestSequence, NailGunCommandPayload.Command command, long serverTick) {
		if (command == null || requestSequence <= 0L) {
			return reject(serverTick, Reason.INVALID);
		}

		advanceWindow(serverTick);
		commandsInWindow++;
		if (requestSequence <= lastObservedSequence) {
			return reject(serverTick, Reason.STALE_SEQUENCE);
		}
		lastObservedSequence = requestSequence;

		if (command == NailGunCommandPayload.Command.RELEASE) {
			return new Decision(true, allowReply(serverTick), Reason.ACCEPTED, lastObservedSequence);
		}
		if (commandsInWindow > MAX_COMMANDS_PER_WINDOW) {
			return reject(serverTick, Reason.RATE_LIMITED);
		}
		if (command == NailGunCommandPayload.Command.FIRE && elapsed(serverTick, lastFireTick) < FIRE_COOLDOWN_TICKS) {
			return reject(serverTick, Reason.FIRE_COOLDOWN);
		}
		if (command == NailGunCommandPayload.Command.FIRE) {
			lastFireTick = serverTick;
		}
		return new Decision(true, true, Reason.ACCEPTED, lastObservedSequence);
	}

	public long lastObservedSequence() {
		return lastObservedSequence;
	}

	private void advanceWindow(long serverTick) {
		if (windowStartTick == Long.MIN_VALUE || serverTick < windowStartTick || serverTick - windowStartTick >= WINDOW_TICKS) {
			windowStartTick = serverTick;
			commandsInWindow = 0;
		}
	}

	private Decision reject(long serverTick, Reason reason) {
		return new Decision(false, allowReply(serverTick), reason, lastObservedSequence);
	}

	private boolean allowReply(long serverTick) {
		if (lastReplyTick == Long.MIN_VALUE || serverTick < lastReplyTick || serverTick - lastReplyTick >= REPLY_COOLDOWN_TICKS) {
			lastReplyTick = serverTick;
			return true;
		}
		return false;
	}

	private static long elapsed(long now, long before) {
		if (before == Long.MIN_VALUE || now < before) {
			return Long.MAX_VALUE;
		}
		return now - before;
	}

	public record Decision(boolean shouldProcess, boolean shouldReply, Reason reason, long acknowledgedSequence) {
	}

	public enum Reason {
		ACCEPTED,
		INVALID,
		STALE_SEQUENCE,
		RATE_LIMITED,
		FIRE_COOLDOWN
	}
}
