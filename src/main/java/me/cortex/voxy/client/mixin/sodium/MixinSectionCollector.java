package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.integration.sodium.SodiumVisibilityBridge;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SectionCollector.class, remap = false)
final class MixinSectionCollector {
    @Inject(method = "visit(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;I)V", at = @At("HEAD"), remap = false)
    private void voxy$captureVisible(RenderSection section, int flags, CallbackInfo ci) {
        SodiumVisibilityBridge.accept(section);
    }
}
