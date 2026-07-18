package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.ICheekyClientChunkCache;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.integration.sodium.SodiumVisibilityBridge;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Unique private static final boolean BOBBY_INSTALLED = FabricLoader.getInstance().isModLoaded("bobby");
    @Shadow @Final private ClientLevel level;
    @Unique private long cachedChunkPos = -1;
    @Unique private int cachedChunkStatus;
    @Unique private int bottomSectionY;

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;ILnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;)V", at = @At("TAIL"), remap = false)
    private void voxy$resetChunkTracker(ClientLevel level, int renderDistance, SortBehavior sortBehavior,
                                        CommandList commandList, CallbackInfo ci) {
        this.bottomSectionY = level.getMinSection();
    }

    @Inject(method = "createTerrainRenderList(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;IZ)Z", at = @At("HEAD"), remap = false)
    private void voxy$beginVisibilityCollection(Camera camera, Viewport viewport, int frame, boolean spectator,
                                                CallbackInfoReturnable<Boolean> cir) {
        SodiumVisibilityBridge.beginCollection();
    }

    @Inject(method = "onChunkRemoved(II)V", at = @At("HEAD"), remap = false)
    private void voxy$ingestRemovedChunk(int x, int z, CallbackInfo ci) {
        if (VoxyConfig.CONFIG.ingestEnabled && !BOBBY_INSTALLED) {
            var cache = (ICheekyClientChunkCache) this.level.getChunkSource();
            var chunk = cache == null ? null : cache.voxy$cheekyGetChunk(x, z);
            if (chunk != null) VoxelIngestService.tryAutoIngestChunk(chunk);
        }
    }

    @Inject(method = "onChunkAdded(II)V", at = @At("HEAD"), remap = false)
    private void voxy$ingestAddedChunk(int x, int z, CallbackInfo ci) {
        if (VoxyConfig.CONFIG.ingestEnabled) {
            var chunk = this.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk != null) VoxelIngestService.tryAutoIngestChunk(chunk);
        }
    }

    @Redirect(method = "updateSectionInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z"), remap = false)
    private boolean voxy$updateOnUpload(RenderSection section, BuiltSectionInfo info) {
        boolean beforeRenderable = RenderSectionFlags.needsRender(section.getFlags());
        boolean changed = section.setInfo(info);
        if (changed && beforeRenderable && !RenderSectionFlags.needsRender(section.getFlags())) {
            this.voxy$ingestSection(section);
        }
        return changed;
    }

    @Unique
    private void voxy$ingestSection(RenderSection section) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null || !VoxyConfig.CONFIG.ingestEnabled) return;
        int x = section.getChunkX(), y = section.getChunkY(), z = section.getChunkZ();
        long key = ChunkPos.asLong(x, z);
        if (key != this.cachedChunkPos) {
            this.cachedChunkPos = key;
            this.cachedChunkStatus = ((AccessorChunkTracker) ChunkTrackerHolder.get(this.level)).getChunkStatus().getOrDefault(key, 0);
        }
        if (this.cachedChunkStatus != 3) return;
        var chunk = this.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        int sectionIndex = y - this.bottomSectionY;
        if (chunk == null || sectionIndex < 0 || sectionIndex >= chunk.getSections().length) return;
        var lightEngine = this.level.getLightEngine();
        var position = SectionPos.of(x, y, z);
        var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(position);
        var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(position);
        VoxelIngestService.rawIngest(renderer.getEngine(), chunk.getSection(sectionIndex), x, y, z,
                blockLight == null ? null : blockLight.copy(), skyLight == null ? null : skyLight.copy());
    }
}
