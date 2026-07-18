package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.Logger;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_SAMPLES;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;

/** Texture attachments of the framebuffer currently receiving Sodium's immediate GL draws. */
public record BoundFramebufferSnapshot(int framebuffer, int colorTexture, int depthTexture, int viewportWidth,
                                       int viewportHeight, int depthWidth, int depthHeight) {
    private static boolean warnedInvalidTarget;
    private static int generation;
    private static int resolvedGeneration = -1;
    private static int resolvedFramebuffer = -1;
    private static BoundFramebufferSnapshot resolvedSnapshot;

    public static BoundFramebufferSnapshot capture() {
        int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        if (resolvedSnapshot != null && resolvedFramebuffer == framebuffer && resolvedGeneration == generation) {
            return resolvedSnapshot;
        }
        if (framebuffer == 0) {
            return resolved(framebuffer, invalid("the default framebuffer is bound"));
        }

        int colorAttachment = glGetInteger(GL_DRAW_BUFFER0);
        int colorTexture = colorAttachment == GL_NONE ? 0 : textureAttachment(colorAttachment);
        int depthTexture = textureAttachment(GL_DEPTH_ATTACHMENT);
        if (depthTexture == 0) {
            depthTexture = textureAttachment(GL_DEPTH_STENCIL_ATTACHMENT);
        }
        if (colorTexture == 0 || depthTexture == 0) {
            return resolved(framebuffer, invalid("the draw framebuffer does not have texture-backed color and depth attachments"));
        }

        int width = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_WIDTH);
        int height = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_HEIGHT);
        int depthWidth = glGetTextureLevelParameteri(depthTexture, 0, GL_TEXTURE_WIDTH);
        int depthHeight = glGetTextureLevelParameteri(depthTexture, 0, GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0 || width != depthWidth || height != depthHeight
                || glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_SAMPLES) > 0
                || glGetTextureLevelParameteri(depthTexture, 0, GL_TEXTURE_SAMPLES) > 0) {
            return resolved(framebuffer, invalid("the draw framebuffer attachments are empty, mismatched, or multisampled"));
        }
        return resolved(framebuffer, new BoundFramebufferSnapshot(framebuffer, colorTexture, depthTexture,
                width, height, depthWidth, depthHeight));
    }

    public static void invalidate() {
        generation++;
        resolvedGeneration = -1;
        resolvedFramebuffer = -1;
        resolvedSnapshot = null;
        warnedInvalidTarget = false;
    }

    public static long depthSize(int depthTexture) {
        BoundFramebufferSnapshot snapshot = resolvedDepthTexture(depthTexture);
        return ((long)snapshot.depthWidth << 32) | (snapshot.depthHeight & 0xffffffffL);
    }

    public static int drawFramebuffer(int depthTexture) {
        return resolvedDepthTexture(depthTexture).framebuffer;
    }

    private static BoundFramebufferSnapshot resolvedDepthTexture(int depthTexture) {
        BoundFramebufferSnapshot snapshot = resolvedSnapshot;
        if (snapshot == null || snapshot.depthTexture != depthTexture || !snapshot.hasTextureAttachments()) {
            throw new IllegalStateException("Depth texture is not from the resolved draw framebuffer");
        }
        return snapshot;
    }

    private static BoundFramebufferSnapshot resolved(int framebuffer, BoundFramebufferSnapshot snapshot) {
        resolvedFramebuffer = framebuffer;
        resolvedGeneration = generation;
        return resolvedSnapshot = snapshot;
    }

    private static BoundFramebufferSnapshot invalid(String reason) {
        if (!warnedInvalidTarget) {
            warnedInvalidTarget = true;
            Logger.warn("Skipping Voxy rendering because " + reason);
        }
        return new BoundFramebufferSnapshot(0, 0, 0, 0, 0, 0, 0);
    }

    private static int textureAttachment(int attachment) {
        if (glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL_TEXTURE) {
            return 0;
        }
        return glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, attachment,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    }

    public boolean hasTextureAttachments() {
        return this.framebuffer != 0 && this.colorTexture != 0 && this.depthTexture != 0
                && this.viewportWidth > 0 && this.viewportHeight > 0;
    }
}
