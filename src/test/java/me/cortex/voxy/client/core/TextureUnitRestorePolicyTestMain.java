package me.cortex.voxy.client.core;

public final class TextureUnitRestorePolicyTestMain {
    private TextureUnitRestorePolicyTestMain() {
    }

    public static void runAll() {
        check(TextureUnitRestorePolicy.managedUnitCount(16) == 12,
                "16-unit snapshot must use GlStateManager only for units 0-11");
        check(TextureUnitRestorePolicy.managedUnitCount(12) == 12,
                "12-unit snapshot must remain fully cache-managed");
        check(TextureUnitRestorePolicy.managedUnitCount(8) == 8,
                "short snapshots must not invent texture units");
        try {
            TextureUnitRestorePolicy.managedUnitCount(-1);
            throw new AssertionError("negative snapshot size was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
