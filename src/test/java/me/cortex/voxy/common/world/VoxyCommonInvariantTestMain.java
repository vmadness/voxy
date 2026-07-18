package me.cortex.voxy.common.world;

import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.thread.ThreadingInvariantTestMain;
import me.cortex.voxy.client.core.TextureUnitRestorePolicyTestMain;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class VoxyCommonInvariantTestMain {
    public static void main(String[] args) throws Exception {
        completionMaskRoundTripsAndOldRecordsAreIncomplete();
        snapshotsAreDefensiveAndExposeCompleteness();
        ingestBatchCompletesOnlyAfterEveryChild();
        loaderFailureIsTerminalAndDoesNotLeak();
        ThreadingInvariantTestMain.runAll();
        TextureUnitRestorePolicyTestMain.runAll();
        System.out.println("Voxy common invariant tests passed");
    }

    private static void loaderFailureIsTerminalAndDoesNotLeak() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        ActiveSectionTracker tracker = new ActiveSectionTracker(1, section -> {
            int attempt = loads.incrementAndGet();
            if (attempt == 1) {
                loaderEntered.countDown();
                try {
                    check(releaseLoader.await(5, TimeUnit.SECONDS), "loader test timed out");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                throw new AssertionError("intentional loader failure");
            }
            return 1;
        }, 4);

        long key = WorldEngine.getWorldSectionId(0, 7, 8, 9);
        AtomicInteger terminalFailures = new AtomicInteger();
        Thread loader = new Thread(() -> expectLoadFailure(tracker, key, terminalFailures), "test section loader");
        Thread waiter = new Thread(() -> expectLoadFailure(tracker, key, terminalFailures), "test section waiter");
        loader.start();
        check(loaderEntered.await(5, TimeUnit.SECONDS), "loader did not start");
        waiter.start();
        Thread.sleep(50);
        releaseLoader.countDown();
        loader.join(5_000);
        waiter.join(5_000);

        check(!loader.isAlive() && !waiter.isAlive(), "loader failure stranded an acquiring thread");
        check(terminalFailures.get() == 2, "all observers must receive the terminal load failure");
        check(tracker.getLoadedCacheCount() == 0, "failed holder leaked loadedSections");

        WorldSection retry = tracker.acquire(key, false);
        check(loads.get() == 2, "a later acquisition did not retry loading");
        retry.release();
        check(tracker.getLoadedCacheCount() == 0, "successful retry leaked a section reference");
    }

    private static void expectLoadFailure(ActiveSectionTracker tracker, long key, AtomicInteger failures) {
        try {
            tracker.acquire(key, false);
        } catch (RuntimeException expected) {
            failures.incrementAndGet();
        }
    }

    private static void completionMaskRoundTripsAndOldRecordsAreIncomplete() {
        WorldSection partial = WorldSection._createRawUntrackedUnsafeSection(0, 2, 3, 4);
        synchronized (partial) {
            check(partial.markLvl0ChildComplete(0, 1, 0), "first completion bit must change");
            partial.commitMutation();
        }
        byte expected = (byte) (1 << WorldSection.getChildIndex(0, 1, 0));
        check(partial.getCompletionMask() == expected, "partial mask mismatch");

        MemoryBuffer encoded = SaveLoadSystem3.serialize(partial);
        WorldSection decoded = WorldSection._createRawUntrackedUnsafeSection(0, 2, 3, 4);
        check(SaveLoadSystem3.deserialize(decoded, encoded), "roundtrip deserialize failed");
        check(decoded.getCompletionMask() == expected, "completion mask was not persisted");
        check(!decoded.isComplete(), "partial section reported complete");

        encoded = SaveLoadSystem3.serialize(partial);
        long metadata = MemoryUtil.memGetLong(encoded.address + Long.BYTES);
        MemoryUtil.memPutLong(encoded.address + Long.BYTES, metadata & 0xFFFFFFL);
        WorldSection legacy = WorldSection._createRawUntrackedUnsafeSection(0, 2, 3, 4);
        check(SaveLoadSystem3.deserialize(legacy, encoded), "legacy deserialize failed");
        check(legacy.getCompletionMask() == 0, "unmarked legacy record must be incomplete");

        long[] fullData = new long[WorldSection.SECTION_VOLUME];
        partial.replaceData(fullData, 0);
        encoded = SaveLoadSystem3.serialize(partial);
        WorldSection full = WorldSection._createRawUntrackedUnsafeSection(0, 2, 3, 4);
        check(SaveLoadSystem3.deserialize(full, encoded), "full deserialize failed");
        check(full.isComplete(), "full replacement did not persist completeness");
    }

    private static void snapshotsAreDefensiveAndExposeCompleteness() {
        WorldSection section = WorldSection._createRawUntrackedUnsafeSection(0, 0, 0, 0);
        long[] data = new long[WorldSection.SECTION_VOLUME];
        data[0] = 42;
        section.replaceData(data, 1);
        WorldSection.Snapshot snapshot = section.snapshot();
        check(snapshot.isComplete(), "full snapshot reported incomplete");
        snapshot.data()[0] = 99;
        check(section.snapshot().data()[0] == 42, "snapshot leaked mutable section storage");
    }

    @SuppressWarnings("unchecked")
    private static void ingestBatchCompletesOnlyAfterEveryChild() throws Exception {
        Class<?> type = Class.forName(VoxelIngestService.class.getName() + "$IngestBatch");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Method finish = type.getDeclaredMethod("finish", boolean.class);
        finish.setAccessible(true);
        Field completionField = type.getDeclaredField("completion");
        completionField.setAccessible(true);

        Object batch = constructor.newInstance(3);
        CompletableFuture<Boolean> completion = (CompletableFuture<Boolean>) completionField.get(batch);
        finish.invoke(batch, true);
        finish.invoke(batch, false);
        check(!completion.isDone(), "batch completed before every child finished");
        finish.invoke(batch, true);
        check(completion.isDone() && !completion.join(), "child failure was not propagated");

        Object successfulBatch = constructor.newInstance(2);
        CompletableFuture<Boolean> successful =
                (CompletableFuture<Boolean>) completionField.get(successfulBatch);
        finish.invoke(successfulBatch, true);
        check(!successful.isDone(), "successful batch completed early");
        finish.invoke(successfulBatch, true);
        check(successful.join(), "successful batch reported failure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
