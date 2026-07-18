package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.gl.BoundFramebufferSnapshot;
import me.cortex.voxy.client.core.integration.sodium.SodiumGlInterop;
import me.cortex.voxy.client.core.rendering.VoxyFogSnapshot;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer {
    protected MixinDefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }

    @Inject(method = "render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void voxy$cancelSodium(ChunkRenderMatrices matrices, CommandList commandList,
                                   ChunkRenderListIterable renderLists, TerrainRenderPass renderPass,
                                   CameraTransform camera, boolean indexedRenderingEnabled, CallbackInfo ci) {
        if (VoxyClient.disableSodiumChunkRender()) {
            super.begin(renderPass);
            try {
                this.voxy$render(matrices, renderPass, camera);
            } finally {
                super.end(renderPass);
            }
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Z)V", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V", shift = At.Shift.BEFORE), remap = false)
    private void voxy$renderBeforeEnd(ChunkRenderMatrices matrices, CommandList commandList,
                                      ChunkRenderListIterable renderLists, TerrainRenderPass renderPass,
                                      CameraTransform camera, boolean indexedRenderingEnabled, CallbackInfo ci) {
        this.voxy$render(matrices, renderPass, camera);
    }

    @Unique
    private void voxy$render(ChunkRenderMatrices matrices, TerrainRenderPass renderPass, CameraTransform camera) {
        if (renderPass != DefaultTerrainRenderPasses.CUTOUT) {
            return;
        }
        SodiumGlInterop.runForeignRenderer(() -> {
            var renderer = IVoxyRenderSystemHolder.getNullable();
            if (renderer == null) {
                return;
            }
            BoundFramebufferSnapshot framebuffer = BoundFramebufferSnapshot.capture();
            if (!framebuffer.hasTextureAttachments()) {
                return;
            }
            var viewport = IrisUtil.irisShaderPackEnabled() ? renderer.getViewport()
                    : renderer.setupViewport(matrices.projection(), matrices.modelView(), VoxyFogSnapshot.current(),
                    framebuffer.viewportWidth(), framebuffer.viewportHeight(), camera.x, camera.y, camera.z);
            renderer.renderOpaque(viewport, framebuffer.depthTexture(), framebuffer.colorTexture());
        });
    }
}
