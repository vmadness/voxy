package me.cortex.voxy.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal {@link BlockGetter} stub standing in for a single {@link BlockState} at the origin, with
 * no real world/level behind it (no height, no build-height range, no block entities). Used
 * wherever code needs to hand a lone BlockState to a vanilla API that expects a BlockGetter (e.g.
 * {@code BlockState#getLightBlock}) without an actual level or chunk section around it.
 * <p>
 * {@link #getBlockState(BlockPos)} and {@link #getFluidState(BlockPos)} default to returning the
 * fixed state passed to the constructor, ignoring {@code pos}. Subclass and override them if a call
 * site needs position-dependent behaviour instead (see the client-side
 * {@code SingleStateBlockAndTintGetter} for the {@link net.minecraft.world.level.BlockAndTintGetter}
 * equivalent, used by call sites that also need shade/tint/light-engine stubs).
 */
public class SingleStateBlockGetter implements BlockGetter {
    protected final BlockState state;

    public SingleStateBlockGetter(BlockState state) {
        this.state = state;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
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
}
