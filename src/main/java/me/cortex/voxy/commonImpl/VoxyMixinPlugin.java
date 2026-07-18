package me.cortex.voxy.commonImpl;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * T13: common-side counterpart of {@code me.cortex.voxy.client.VoxyMixinPlugin} -- gates
 * chunky's compat mixin (which lives in common.voxy.mixins.json since it hooks a
 * server-side/world-gen chunk cache used on both integrated and dedicated servers) behind
 * FabricLoader.isModLoaded("chunky") so Voxy doesn't crash mixing into chunky's classes when
 * chunky isn't installed.
 */
public class VoxyMixinPlugin implements IMixinConfigPlugin {
    private static boolean chunkyInstalled;

    @Override
    public void onLoad(String mixinPackage) {
        chunkyInstalled = FabricLoader.getInstance().isModLoaded("chunky");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String pkg = mixinClassName.substring(0, mixinClassName.length() - simple.length());
        if (pkg.endsWith(".chunky.")) {
            return chunkyInstalled;
        }
        return true;
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
