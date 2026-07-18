package me.cortex.voxy.client.core.integration.sodium;

import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;

/** Brackets raw Voxy GL so Sodium invalidates its managed-state cache on both sides. */
public final class SodiumGlInterop {
    private SodiumGlInterop() {
    }

    public static void runForeignRenderer(Runnable action) {
        RenderDevice.exitManagedCode();
        try {
            action.run();
        } finally {
            RenderDevice.enterManagedCode();
        }
    }
}
