package me.cortex.voxy.client.mixin.minecraft.util;

// Disabled for MC 1.21.1: BlockableEventLoop.doRunTask() no longer calls an
// isNonRecoverable(Throwable) hook (it just logs the exception via LOGGER.error
// and swallows it). There is no equivalent injection point to force a crash on
// LoadException in this version. The reference 1.21.1 port
// (ref_m3t4f1v3/mc_1211) disables this mixin the same way. See the removed
// entry in client.voxy.mixins.json.
//
// import me.cortex.voxy.client.LoadException;
// import net.minecraft.util.thread.BlockableEventLoop;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Redirect;
//
// @Mixin(BlockableEventLoop.class)
// public abstract class MixinBlockableEventLoop {
//
//     @Shadow public static boolean isNonRecoverable(Throwable throwable){return false;}
//
//     @Redirect(method = "doRunTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/BlockableEventLoop;isNonRecoverable(Ljava/lang/Throwable;)Z"))
//     private boolean voxy$forceCrashOnError(Throwable exception) {
//         if (exception instanceof LoadException le) {
//             if (le.getCause() instanceof RuntimeException cause) {
//                 throw cause;
//             }
//             throw le;
//         }
//         return isNonRecoverable(exception);
//     }
// }
