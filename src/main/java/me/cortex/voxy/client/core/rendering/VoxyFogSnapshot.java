package me.cortex.voxy.client.core.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.FogShape;

/** A stable copy of terrain fog made before Voxy suppresses Minecraft's terrain fog. */
public record VoxyFogSnapshot(float environmentalStart, float environmentalEnd,
                              float renderDistanceStart, float renderDistanceEnd,
                              float red, float green, float blue, float alpha, FogShape shape) {
    public static final float DISABLED = 999_999_999.0F;

    private static VoxyFogSnapshot current = noFog();

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
