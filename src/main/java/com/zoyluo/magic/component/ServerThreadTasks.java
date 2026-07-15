package com.zoyluo.magic.component;

import net.minecraft.server.MinecraftServer;

/** Keeps world and runtime-state mutations on the authoritative server thread. */
final class ServerThreadTasks {
	private ServerThreadTasks() {
	}

	static void execute(MinecraftServer server, Runnable task) {
		if (server.isOnThread()) {
			task.run();
		} else {
			server.execute(task);
		}
	}
}
