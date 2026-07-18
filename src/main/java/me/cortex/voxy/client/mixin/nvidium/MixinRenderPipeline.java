package me.cortex.voxy.client.mixin.nvidium;

import me.cortex.nvidium.RenderPipeline;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.gl.BoundFramebufferSnapshot;
import me.cortex.voxy.client.core.rendering.VoxyFogSnapshot;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// T13: retargeted from the dev/26.x blaze3d-era signature (TerrainRenderPass/FogParameters/
// GpuSampler/GlTextureView, none of which exist on 1.21.1's blaze3d) to nvidium 0.4.1-beta9's
// actual 1.21.1 renderFrame(Viewport, ChunkRenderMatrices, double, double, double) -- confirmed
// via javap against the loom-remapped nvidium-0.4.1-beta9-1.21-1.21.1.jar, matches the reference
// 1.21.1 port's MixinRenderPipeline. Grabs the currently bound framebuffer the same way
// sodium's MixinDefaultChunkRenderer does, since nvidium (like sodium's DefaultChunkRenderer)
// renders straight into whatever framebuffer is bound rather than handing Voxy a target.
@Mixin(value = RenderPipeline.class, remap = false)
public class MixinRenderPipeline {
    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void voxy$injectRender(Viewport frustum, ChunkRenderMatrices crm, double px, double py, double pz, CallbackInfo ci) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null) {
            return;
        }
        BoundFramebufferSnapshot framebuffer = BoundFramebufferSnapshot.capture();
        if (!framebuffer.hasTextureAttachments()) {
            return;
        }
        var viewport = renderer.setupViewport(crm.projection(), crm.modelView(), VoxyFogSnapshot.current(),
                framebuffer.viewportWidth(), framebuffer.viewportHeight(), px, py, pz);
        renderer.renderOpaque(viewport, framebuffer.depthTexture(), framebuffer.colorTexture());
    }
}
