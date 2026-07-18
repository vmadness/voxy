package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.VoxyFogSnapshot;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.glViewport;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
                    shift = At.Shift.AFTER), order = 100)
    private void voxy$captureIrisViewport(DeltaTracker deltaTracker, boolean renderOutline, Camera camera,
                                          GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f modelView,
                                          Matrix4f projection, CallbackInfo ci) {
        if (!IrisUtil.irisShaderPackEnabled() || IrisUtil.irisShadowActive()) return;
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null) return;
        var target = Minecraft.getInstance().getMainRenderTarget();
        glViewport(0, 0, target.viewWidth, target.viewHeight);
        var position = camera.getPosition();
        renderer.setupViewport(projection, modelView, VoxyFogSnapshot.current(), target.viewWidth, target.viewHeight,
                position.x, position.y, position.z);
    }
}
