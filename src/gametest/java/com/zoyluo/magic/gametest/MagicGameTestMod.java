package com.zoyluo.magic.gametest;

import net.fabricmc.api.ModInitializer;

/** Test-only entrypoint for the isolated Magic GameTest source set. */
public final class MagicGameTestMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// The production Magic mod owns all registrations; this entrypoint only loads test classes.
	}
}
