package me.cortex.voxy.client;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.GPUTiming;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 has no DebugScreenEntry/DebugScreenEntries registry (that is a 26.x-only API), so instead of
 * registering entries we just append voxy's F3 lines directly from a mixin on DebugScreenOverlay
 * (see MixinDebugScreenOverlay). This mirrors the mechanism used by the mc_1211 reference port's
 * MixinDebugScreenOverlay, while keeping dev's line content.
 */
public class DebugEntries {
    public static void init() {
        //Nothing to register on 1.21.1; kept as a no-op so callers don't need to change.
    }

    public static void addLines(List<String> lines) {
        if (!VoxyCommon.isAvailable()) {
            lines.add(ChatFormatting.RED + "voxy-" + VoxyCommon.MOD_VERSION);//Voxy installed, not avalible
            return;
        }
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            lines.add(ChatFormatting.YELLOW + "voxy-" + VoxyCommon.MOD_VERSION);//Voxy avalible, no instance active
            return;
        }

        VoxyRenderSystem vrs = IVoxyRenderSystemHolder.getNullable();

        //Voxy instance active
        lines.add((vrs == null ? ChatFormatting.DARK_GREEN : ChatFormatting.GREEN) + "voxy-" + VoxyCommon.MOD_VERSION);

        List<String> instanceLines = new ArrayList<>();
        instance.addDebug(instanceLines);
        lines.addAll(instanceLines);

        if (vrs != null) {
            List<String> renderLines = new ArrayList<>();
            vrs.addDebugInfo(renderLines);
            lines.addAll(renderLines);
        }
    }

    private static boolean previousGpuDebugEnabled = false;
    public static void onDebugScreenStateChanged(boolean debugScreenOpen) {
        if (debugScreenOpen != previousGpuDebugEnabled) {
            previousGpuDebugEnabled = debugScreenOpen;

            GPUTiming.INSTANCE.setEnabled(previousGpuDebugEnabled);
            RenderStatistics.enabled = previousGpuDebugEnabled;
        }
    }
}
