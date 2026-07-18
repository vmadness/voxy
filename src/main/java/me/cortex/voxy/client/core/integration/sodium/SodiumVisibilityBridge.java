package me.cortex.voxy.client.core.integration.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.minecraft.core.SectionPos;

/** Collects Sodium's built visible sections for Voxy's near-terrain depth bounds. */
public final class SodiumVisibilityBridge {
    private static final LongOpenHashSet SECTIONS = new LongOpenHashSet();

    private SodiumVisibilityBridge() {
    }

    public static void beginCollection() {
        SECTIONS.clear();
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer != null && !IrisUtil.irisShadowActive()) {
            renderer.visbleSectionStream.reset();
        }
    }

    public static void accept(RenderSection section) {
        if (section == null || !section.isBuilt() || IrisUtil.irisShadowActive()) {
            return;
        }
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null) {
            return;
        }
        long position = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
        if (SECTIONS.add(position)) {
            renderer.visbleSectionStream.put(position);
        }
    }
}
