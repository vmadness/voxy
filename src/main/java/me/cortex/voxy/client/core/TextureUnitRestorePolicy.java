package me.cortex.voxy.client.core;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;

import static org.lwjgl.opengl.GL45C.glBindTextureUnit;

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

    static void restoreBinding(int unit, int texture) {
        if (unit < 0) {
            throw new IllegalArgumentException("texture unit cannot be negative");
        }
        if (unit < GL_STATE_MANAGER_UNIT_COUNT) {
            GlStateManager._activeTexture(GlConst.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(texture);
        }
        // Voxy binds through DSA, bypassing GlStateManager's cache. Always issue the
        // raw bind as well in case the cached binding already matched the target.
        glBindTextureUnit(unit, texture);
    }
}
