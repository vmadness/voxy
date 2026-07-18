package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VoxelIngestService {
    private static final ThreadLocal<VoxelizedSection> SECTION_CACHE = ThreadLocal.withInitial(VoxelizedSection::createEmpty);
    private final Service service;
    /** The submission result and the point at which every accepted section is committed to the world. */
    public record IngestHandle(boolean accepted, CompletableFuture<Boolean> completion) {
        private static IngestHandle rejected() {
            return new IngestHandle(false, CompletableFuture.completedFuture(false));
        }
    }

    private static final class IngestBatch {
        private final AtomicInteger remaining;
        private final AtomicBoolean successful = new AtomicBoolean(true);
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

        private IngestBatch(int count) {
            this.remaining = new AtomicInteger(count);
        }

        private void finish(boolean success) {
            if (!success) successful.set(false);
            if (remaining.decrementAndGet() == 0) completion.complete(successful.get());
        }
    }

    private record IngestSection(int cx, int cy, int cz, WorldEngine world, LevelChunkSection section,
                                 DataLayer blockLight, DataLayer skyLight, IngestBatch batch){}
    private final ConcurrentLinkedDeque<IngestSection> ingestQueue = new ConcurrentLinkedDeque<>();

    public VoxelIngestService(ServiceManager pool) {
        this.service = pool.createServiceNoCleanup(()->this::processJob, 5000, "Ingest service");
    }

    private void processJob() {
        var task = this.ingestQueue.pop();
        boolean success = false;
        try {
            var section = task.section;
            var vs = SECTION_CACHE.get().setPosition(task.cx, task.cy, task.cz);

            if (section.hasOnlyAir() && task.blockLight == null && task.skyLight == null) {//If the chunk section has lighting data, propagate it
                WorldUpdater.insertUpdate(task.world, vs.zero());
            } else {
                VoxelizedSection csec = WorldConversionFactory.convert(
                        vs,
                        task.world.getMapper(),
                        section.getStates(),
                        section.getBiomes(),
                        getLightingSupplier(task)
                );
                WorldVoxilizedSectionMipper.mipSection(csec, task.world.getMapper());
                WorldUpdater.insertUpdate(task.world, csec);
            }
            success = true;
        } catch (Throwable throwable) {
            Logger.error("Voxel ingest job failed", throwable);
        } finally {
            //Release the ref we had acquired for the world
            task.world.releaseRef();
            task.batch.finish(success);
        }
    }

    @NotNull
    private static ILightingSupplier getLightingSupplier(IngestSection task) {
        ILightingSupplier supplier = (x,y,z) -> (byte) 0;
        var sla = task.skyLight;
        var bla = task.blockLight;
        boolean sl = sla != null && !sla.isEmpty();
        boolean bl = bla != null && !bla.isEmpty();
        if (sl || bl) {
            if (sl && bl) {
                supplier = (x,y,z)-> {
                    int block = Math.min(15,bla.get(x, y, z));
                    int sky = Math.min(15,sla.get(x, y, z));
                    return (byte) (sky|(block<<4));
                };
            } else if (bl) {
                supplier = (x,y,z)-> {
                    int block = Math.min(15,bla.get(x, y, z));
                    int sky = 0;
                    return (byte) (sky|(block<<4));
                };
            } else {
                supplier = (x,y,z)-> {
                    int block = 0;
                    int sky = Math.min(15,sla.get(x, y, z));
                    return (byte) (sky|(block<<4));
                };
            }
        }
        return supplier;
    }

    private static boolean shouldIngestSection(LevelChunkSection section, int cx, int cy, int cz) {
        return true;
    }

    public boolean enqueueIngest(WorldEngine engine, LevelChunk chunk) {
        return enqueueIngestWithCompletion(engine, chunk).accepted();
    }

    public IngestHandle enqueueIngestWithCompletion(WorldEngine engine, LevelChunk chunk) {
        if (!this.service.isLive()) {
            return IngestHandle.rejected();
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried inserting chunk into WorldEngine that was not alive");
        }

        engine.markActive();

        var lightingProvider = chunk.getLevel().getLightEngine();
        boolean gotLighting = false;

        int i = chunk.getMinSection() - 1;
        boolean allEmpty = true;
        for (var section : chunk.getSections()) {
            i++;
            if (section == null || !shouldIngestSection(section, chunk.getPos().x, i, chunk.getPos().z)) continue;
            allEmpty&=section.hasOnlyAir();
            //if (section.isEmpty()) continue;
            var pos = SectionPos.of(chunk.getPos(), i);
            if (lightingProvider.getDebugSectionType(LightLayer.SKY, pos) != LayerLightSectionStorage.SectionType.LIGHT_AND_DATA && lightingProvider.getDebugSectionType(LightLayer.BLOCK, pos) != LayerLightSectionStorage.SectionType.LIGHT_AND_DATA)
                continue;
            gotLighting = true;
        }

        if (!gotLighting && !allEmpty) return IngestHandle.rejected();

        var blp = gotLighting ? lightingProvider.getLayerListener(LightLayer.BLOCK) : null;
        var slp = gotLighting ? lightingProvider.getLayerListener(LightLayer.SKY) : null;

        int taskCount = 0;
        int countY = chunk.getMinSection() - 1;
        for (var section : chunk.getSections()) {
            countY++;
            if (section != null && shouldIngestSection(section, chunk.getPos().x, countY, chunk.getPos().z)) taskCount++;
        }
        if (taskCount == 0) return IngestHandle.rejected();
        IngestBatch batch = new IngestBatch(taskCount);
        boolean anyAccepted = false;


        i = chunk.getMinSection() - 1;
        for (var section : chunk.getSections()) {
            i++;
            if (section == null || !shouldIngestSection(section, chunk.getPos().x, i, chunk.getPos().z)) continue;
            //if (section.isEmpty()) continue;
            var pos = SectionPos.of(chunk.getPos(), i);

            var bl = blp == null ? null : blp.getDataLayerData(pos);
            if (bl != null) {
                bl = bl.copy();
            }

            var sl = slp == null ? null : slp.getDataLayerData(pos);
            if (sl != null) {
                sl = sl.copy();
            }

            //If its null for either, assume failure to obtain lighting and ignore section
            //if (blNone && slNone) {
            //    continue;
            //}
            engine.acquireRef();//This is not great but dont really have a better solution as all the others have there own problem
            // Chunk sections remain mutable on the tick thread. Snapshot both palettes before handoff.
            LevelChunkSection sectionSnapshot = snapshotSection(section);
            IngestSection task = new IngestSection(chunk.getPos().x, i, chunk.getPos().z, engine, sectionSnapshot, bl, sl, batch);
            this.ingestQueue.add(task);
            try {
                this.service.execute();
                anyAccepted = true;
            } catch (Exception e) {
                Logger.error("Executing had an error: assume shutting down, aborting",e);
                if (this.ingestQueue.remove(task)) {
                    engine.releaseRef();
                    batch.finish(false);
                } else {
                    // A worker already owns the task and will complete/release it.
                    anyAccepted = true;
                }
            }
        }
        return new IngestHandle(anyAccepted, batch.completion);
    }

    @SuppressWarnings("unchecked")
    private static LevelChunkSection snapshotSection(LevelChunkSection section) {
        if (!(section.getBiomes() instanceof PalettedContainer<?> biomeContainer)) {
            throw new IllegalStateException("unsupported readonly biome container implementation");
        }
        PalettedContainer<Holder<Biome>> biomes =
                ((PalettedContainer<Holder<Biome>>) biomeContainer).copy();
        return new LevelChunkSection(section.getStates().copy(), biomes);
    }

    public int getTaskCount() {
        return this.service.numJobs();
    }

    public void shutdown() {
        this.service.shutdown();
        while (!this.ingestQueue.isEmpty()) {
            //We need to manually release all our world locks
            IngestSection task = this.ingestQueue.pop();
            task.world.releaseRef();
            task.batch.finish(false);
        }

    }

    //Utility method to ingest a chunk into the given WorldIdentifier or world
    public static boolean tryIngestChunk(WorldIdentifier worldId, LevelChunk chunk) {
        if (worldId == null) return false;
        var instance = VoxyCommon.getInstance();
        if (instance == null) return false;
        if (!instance.isIngestEnabled(worldId)) return false;
        var engine = instance.getOrCreate(worldId);
        if (engine == null) return false;
        return instance.getIngestService().enqueueIngest(engine, chunk);
    }

    //Try to automatically ingest the chunk into the correct world
    public static boolean tryAutoIngestChunk(LevelChunk chunk) {
        return tryIngestChunk(WorldIdentifier.of(chunk.getLevel()), chunk);
    }

    private boolean rawIngest0(WorldEngine engine, LevelChunkSection section, int x, int y, int z, DataLayer bl, DataLayer sl) {
        engine.acquireRef();
        IngestBatch batch = new IngestBatch(1);
        IngestSection task = new IngestSection(x, y, z, engine, section, bl, sl, batch);
        this.ingestQueue.add(task);
        try {
            this.service.execute();
            return true;
        } catch (Exception e) {
            Logger.error("Executing had an error: assume shutting down, aborting",e);
            if (this.ingestQueue.remove(task)) {
                engine.releaseRef();//we must manually release
                batch.finish(false);
                return false;
            }
            return true;
        }
    }

    public static boolean rawIngest(WorldIdentifier id, LevelChunkSection section, int x, int y, int z, DataLayer bl, DataLayer sl) {
        if (id == null) return false;
        var engine = id.getOrCreateEngine();
        if (engine == null) return false;
        return rawIngest(engine, section, x, y, z, bl, sl);
    }

    public static boolean rawIngest(WorldEngine engine, LevelChunkSection section, int x, int y, int z, DataLayer bl, DataLayer sl) {
        if (!shouldIngestSection(section, x, y, z)) return false;
        if (engine.instanceIn == null) return false;
        if (!engine.instanceIn.isIngestEnabled(null)) return false;//TODO: dont pass in null
        return engine.instanceIn.getIngestService().rawIngest0(engine, section, x, y, z, bl, sl);
    }
}
