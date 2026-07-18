package me.cortex.voxy.client.core.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.FogShape;

/**
 * A stable copy of terrain fog made before Voxy suppresses Minecraft's terrain fog.
 *
 * <p>This class is a static mutable holder: {@link #publish} is called from one mixin site to
 * record the fog state Minecraft was about to use, and {@link #current} is read back from a
 * different mixin site later in the same frame's render pass. A static holder is the simplest way
 * to pass this value across that mixin boundary without threading an extra parameter through
 * unrelated vanilla call chains. All writes and reads are expected to happen on the render thread
 * only; nothing in this class enforces that beyond the {@code volatile} field below (which
 * guarantees visibility, not exclusion, in case that assumption is ever violated).
 *
 * <p>Contrast with {@link me.cortex.voxy.client.core.gl.BoundFramebufferSnapshot}, which is a pure
 * capture-value: it is created, consumed, and discarded within a single call site with no shared
 * static state. Use that style whenever a value doesn't need to cross a mixin boundary; use this
 * style only when it does.
 */
public record VoxyFogSnapshot(float environmentalStart, float environmentalEnd,
                              float renderDistanceStart, float renderDistanceEnd,
                              float red, float green, float blue, float alpha, FogShape shape) {
    public static final float DISABLED = 999_999_999.0F;

    private static volatile VoxyFogSnapshot current = noFog();

    public static VoxyFogSnapshot captureRenderSystem(float renderDistanceStart, float renderDistanceEnd) {
        float[] color = RenderSystem.getShaderFogColor();
        return new VoxyFogSnapshot(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                renderDistanceStart, renderDistanceEnd, color[0], color[1], color[2], color[3],
                RenderSystem.getShaderFogShape());
    }

    public static VoxyFogSnapshot current() {
        return current;
    }

    public static void publish(VoxyFogSnapshot snapshot) {
        current = snapshot;
    }

    public static VoxyFogSnapshot noFog() {
        return new VoxyFogSnapshot(DISABLED, DISABLED, DISABLED, DISABLED,
                1.0F, 1.0F, 1.0F, 1.0F, FogShape.SPHERE);
    }
}
