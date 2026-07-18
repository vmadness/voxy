package me.cortex.voxy.client.core;

/**
 * Minecraft 1.21.1's private GlStateManager.TEXTURES array is initialized with
 * IntStream.range(0, 12), so _bindTexture can only safely address these units.
 */
final class TextureUnitRestorePolicy {
    static final int GL_STATE_MANAGER_UNIT_COUNT = 12;

    private TextureUnitRestorePolicy() {
    }

    static int managedUnitCount(int capturedUnitCount) {
        if (capturedUnitCount < 0) {
            throw new IllegalArgumentException("captured texture unit count cannot be negative");
        }
        return Math.min(GL_STATE_MANAGER_UNIT_COUNT, capturedUnitCount);
    }
}
