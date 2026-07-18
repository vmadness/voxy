package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.VoxyFogSnapshot;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, priority = 900)
public class MixinFogRenderer {
    private static final float EPSILON = 1.0E-3F;

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("TAIL"))
    private static void voxy$captureTerrainFog(Camera camera, FogRenderer.FogMode fogMode, float viewDistance,
                                                boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }

        float legacyStart = RenderSystem.getShaderFogStart();
        float legacyEnd = RenderSystem.getShaderFogEnd();
        FogShape shape = RenderSystem.getShaderFogShape();
        float distanceStart = viewDistance - Mth.clamp(viewDistance / 10.0F, 4.0F, 64.0F);
        boolean normalDistanceFog = camera.getFluidInCamera() == FogType.NONE && !thickFog
                && Math.abs(legacyStart - distanceStart) <= EPSILON
                && Math.abs(legacyEnd - viewDistance) <= EPSILON
                && shape == FogShape.CYLINDER;

        float[] color = RenderSystem.getShaderFogColor();
        VoxyFogSnapshot snapshot = new VoxyFogSnapshot(
                normalDistanceFog ? VoxyFogSnapshot.DISABLED : legacyStart,
                normalDistanceFog ? VoxyFogSnapshot.DISABLED : legacyEnd,
                distanceStart, viewDistance,
                color[0], color[1], color[2], color[3], shape);
        VoxyFogSnapshot.publish(snapshot);

        if (!VoxyConfig.CONFIG.isRenderingEnabled() || IVoxyRenderSystemHolder.getNullable() == null) {
            return;
        }

        if (normalDistanceFog || (!VoxyConfig.CONFIG.useEnvironmentalFog && legacyEnd >= 10.0F)) {
            RenderSystem.setShaderFogStart(VoxyFogSnapshot.DISABLED);
            RenderSystem.setShaderFogEnd(VoxyFogSnapshot.DISABLED);
        }
    }
}
