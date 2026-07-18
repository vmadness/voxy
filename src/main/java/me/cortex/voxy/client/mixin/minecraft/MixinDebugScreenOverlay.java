package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.DebugEntries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 1.21.1 port of MixinDebugScreenEntryList: 1.21.1 has no DebugScreenEntry/DebugScreenEntries
 * registry, so voxy's F3 lines are appended directly to DebugScreenOverlay#getGameInformation,
 * matching the mechanism used by the mc_1211 reference port.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {
    @Shadow
    private boolean renderDebug;

    @Inject(method = "render", at = @At("HEAD"))
    private void voxy$manageGpuTiming(GuiGraphics guiGraphics, CallbackInfo ci) {
        DebugEntries.onDebugScreenStateChanged(this.renderDebug);
    }

    @Inject(at = @At("RETURN"), method = "getGameInformation")
    private void voxy$appendDebugLines(CallbackInfoReturnable<List<String>> cir) {
        DebugEntries.addLines(cir.getReturnValue());
    }
}
