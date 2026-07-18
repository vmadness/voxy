package me.cortex.voxy.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * T13: runtime gating for client-side compat mixins that target optional mods (flashback,
 * nvidium) plus iris (which is currently a forced runtime dependency, but is gated too for
 * defense-in-depth in case that ever changes). Mixins under {@code mixin.<subpackage>.*} are
 * only applied if the corresponding mod is present, so a build of Voxy with these compat mods
 * absent from the classpath at runtime doesn't hard-crash trying to mixin into classes that
 * don't exist. Modeled on the reference 1.21.1 port's
 * {@code me.cortex.voxy.client.mixin.ClientVoxyMixinPlugin}.
 */
public class VoxyMixinPlugin implements IMixinConfigPlugin {
    private static boolean flashbackInstalled;
    private static boolean nvidiumInstalled;
    private static boolean irisInstalled;

    @Override
    public void onLoad(String mixinPackage) {
        flashbackInstalled = FabricLoader.getInstance().isModLoaded("flashback");
        nvidiumInstalled = FabricLoader.getInstance().isModLoaded("nvidium");
        irisInstalled = FabricLoader.getInstance().isModLoaded("iris");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // mixinClassName is fully-qualified, e.g. "me.cortex.voxy.client.mixin.flashback.MixinFlashbackMeta"
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String pkg = mixinClassName.substring(0, mixinClassName.length() - simple.length());
        if (pkg.endsWith(".flashback.")) {
            return flashbackInstalled;
        }
        if (pkg.endsWith(".nvidium.")) {
            return nvidiumInstalled;
        }
        if (pkg.endsWith(".iris.")) {
            return irisInstalled;
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
