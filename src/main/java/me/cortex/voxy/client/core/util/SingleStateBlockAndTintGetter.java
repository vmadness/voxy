package me.cortex.voxy.client.core.util;

import me.cortex.voxy.common.util.SingleStateBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Shared {@link BlockAndTintGetter} stub for code that needs to hand a lone {@link BlockState} to a
 * vanilla model/colour API (model baking, colour capture) without a real level behind it. Factors
 * out the boilerplate ({@code getBrightness}, {@code getLightEngine}, {@code getBlockEntity},
 * {@code getHeight}, {@code getMinBuildHeight}) that was previously duplicated across four anonymous
 * BlockAndTintGetter/BlockGetter implementations in ModelFactory, SoftwareModelTextureBakery and
 * Mapper (see the common {@link SingleStateBlockGetter} for the plain-BlockGetter case used by
 * Mapper, which only needs BlockGetter and not the tint/shade extensions here).
 * <p>
 * {@link #getBlockState(BlockPos)} and {@link #getFluidState(BlockPos)} default to the fixed state
 * passed to the constructor, matching every current call site except
 * {@code SoftwareModelTextureBakery#bakeFluidState}, which overrides both to vary by face (see that
 * call site for why). {@link #getShade} and {@link #getBlockTint} have no sensible shared default -
 * every call site needs different behaviour there - so they stay abstract.
 */
public abstract class SingleStateBlockAndTintGetter implements BlockAndTintGetter {
    /**
     * No-op {@link LevelLightEngine}, returned by {@link #getLightEngine()} instead of {@code null}.
     * Vanilla model/colour providers never actually call methods on the light engine in these
     * colour-capture contexts (see IMPROVEMENTS.md item 5), so an inert instance is safe: both of its
     * internal light engines are disabled (constructed with blockLight=false, skyLight=false), and
     * {@link LightChunkGetter#getChunkForLighting} is never invoked as a result. This exists mainly so
     * callers that merely null-check the light engine (rather than assuming its absence) keep working.
     */
    private static final LevelLightEngine EMPTY_LIGHT_ENGINE = new LevelLightEngine(new LightChunkGetter() {
        @Override
        public LightChunk getChunkForLighting(int x, int z) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return new SingleStateBlockGetter(Blocks.AIR.defaultBlockState());
        }
    }, false, false);

    protected final BlockState state;

    protected SingleStateBlockAndTintGetter(BlockState state) {
        this.state = state;
    }

    @Override
    public abstract float getShade(net.minecraft.core.Direction direction, boolean shaded);

    @Override
    public abstract int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver colorResolver);

    @Override
    public int getBrightness(LightLayer type, BlockPos pos) {
        return 0;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return EMPTY_LIGHT_ENGINE;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.state.getFluidState();
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }
}
