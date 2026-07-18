package me.cortex.voxy.client.core.integration.sodium;

import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;

/**
 * Brackets raw Voxy GL so Sodium invalidates its managed-state cache on both sides.
 *
 * <p>Why this exists (not just what it does): Voxy issues raw OpenGL calls directly, bypassing
 * Sodium's {@link RenderDevice}, which normally tracks GL state itself so it can skip redundant
 * state-changing calls. If Voxy's raw calls happen while Sodium still trusts its cached state,
 * Sodium's next draw can silently use stale bindings/state that no longer match what's actually
 * bound on the GPU - a subtle, hard-to-repro rendering corruption bug, not a crash. Calling
 * {@code exitManagedCode()}/{@code enterManagedCode()} around the foreign GL section tells Sodium
 * "don't trust your cache across this boundary", forcing it to resynchronize afterward. Do not
 * remove this bracketing as a "simplification" - without it, rendering breaks in ways that are easy
 * to miss in testing and only show up intermittently depending on what Sodium last cached.
 */
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
