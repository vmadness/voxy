package me.cortex.voxy.client.core.model;

import me.cortex.voxy.client.core.RenderResourceReuse;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class ModelStore {
    public static final int MODEL_SIZE = 64;
    final GlBuffer modelBuffer;
    final GlBuffer modelColourBuffer;
    final GlTexture textures;
    public final int blockSampler = glGenSamplers();

    public ModelStore() {
        GlBuffer modelBuffer = null;
        GlBuffer modelColourBuffer = null;
        GlTexture textures = null;
        try {
            modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16)).name("ModelData");
            modelColourBuffer = new GlBuffer(4 * (1<<16)).name("ModelColour");
            textures = RenderResourceReuse.getOrCreateModelStoreTextureAtlas();

            //Limit the mips of the texture to match that of the terrain atlas
            //1.21.1: TextureAtlas's mip field is named "mipLevel" (dev's 26.x "maxMipLevel" doesn't exist);
            //NOTE: voxy.accesswidener still widens the old "maxMipLevel" name (task 8/access-widener rewrite,
            //outside this file-ownership pass) and needs updating to "mipLevel" for this to actually resolve.
            int mipLvl = ((TextureAtlas) Minecraft.getInstance().getTextureManager()
                    .getTexture(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")))
                    .mipLevel;

            glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
            glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_LOD, 0);
            glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAX_LOD, mipLvl);//Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE)
        } catch (Throwable t) {
            if (modelBuffer != null) modelBuffer.free();
            if (modelColourBuffer != null) modelColourBuffer.free();
            if (textures != null) RenderResourceReuse.giveBackModelStoreTextureAtlas(textures);
            glDeleteSamplers(this.blockSampler);
            throw t;
        }
        this.modelBuffer = modelBuffer;
        this.modelColourBuffer = modelColourBuffer;
        this.textures = textures;
    }


    public void free() {
        this.modelBuffer.free();
        this.modelColourBuffer.free();
        RenderResourceReuse.giveBackModelStoreTextureAtlas(this.textures);
        glDeleteSamplers(this.blockSampler);
    }


    public void bind(int modelBindingIndex, int colourBindingIndex, int textureBindingIndex) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, modelBindingIndex, this.modelBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, colourBindingIndex, this.modelColourBuffer.id);
        glBindTextureUnit(textureBindingIndex, this.textures.id);
        glBindSampler(textureBindingIndex, this.blockSampler);
    }
}
