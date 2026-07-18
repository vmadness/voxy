package me.cortex.voxy.client.compat;

import java.nio.file.Path;

public class FlashbackCompat {
    // 1.21.1 port: flashback compat is disabled (no compileOnly dep on this classpath, same as
    // nvidium/vivecraft/chunky, see build.gradle/PORT_1211.md T13), so this is hardcoded false
    // and getReplayStoragePath0()'s use of the flashback API is stubbed out below.
    public static final boolean FLASHBACK_INSTALLED = false;

    public static Path getReplayStoragePath() {
        if (!FLASHBACK_INSTALLED) {
            return null;
        }
        return getReplayStoragePath0();
    }

    private static Path getReplayStoragePath0() {
        //Flashback compat disabled for this port (FLASHBACK_INSTALLED is always false above), so
        //this path is unreachable; stubbed to null rather than referencing the flashback API.
        return null;
    }
}
