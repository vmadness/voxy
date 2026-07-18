package me.cortex.voxy.client.core.integration.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.minecraft.core.SectionPos;

/** Collects Sodium's built visible sections for Voxy's near-terrain depth bounds. */
public final class SodiumVisibilityBridge {
    private static final LongOpenHashSet SECTIONS = new LongOpenHashSet();

    //Resolved once per collection cycle in beginCollection() and reused by accept() for every
    //visible section (thousands/frame at high render distance), instead of re-resolving the
    //renderer holder on every single section.
    private static VoxyRenderSystem currentRenderer;

    private SodiumVisibilityBridge() {
    }

    public static void beginCollection() {
        SECTIONS.clear();
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer != null && !IrisUtil.irisShadowActive()) {
            renderer.visbleSectionStream.reset();
        } else {
            renderer = null;
        }
        currentRenderer = renderer;
    }

    public static void accept(RenderSection section) {
        var renderer = currentRenderer;
        if (renderer == null || section == null || !section.isBuilt() || IrisUtil.irisShadowActive()) {
            return;
        }
        long position = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
        if (SECTIONS.add(position)) {
            renderer.visbleSectionStream.put(position);
        }
    }

}
