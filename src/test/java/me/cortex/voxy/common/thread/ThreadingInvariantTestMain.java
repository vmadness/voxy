package me.cortex.voxy.common.thread;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadingInvariantTestMain {
    private ThreadingInvariantTestMain() {}

    public static void runAll() throws Exception {
        workerSurvivesThrowableAndShutdownCompletes();
    }

    private static void workerSurvivesThrowableAndShutdownCompletes() throws Exception {
        UnifiedServiceThreadPool pool = new UnifiedServiceThreadPool();
        AtomicInteger executions = new AtomicInteger();
        Service service = pool.serviceManager.createServiceNoCleanup(() -> () -> {
            if (executions.getAndIncrement() == 0) throw new AssertionError("intentional test failure");
        }, 1, "throwable invariant test");

        pool.setNumThreads(1);
        service.execute();
        service.execute();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executions.get() != 2 && System.nanoTime() < deadline) Thread.onSpinWait();
        check(executions.get() == 2, "worker stopped after a job threw an Error");

        service.shutdown();
        pool.shutdown();
        check(pool.setNumThreads(0) == false, "shutdown left a worker registered");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
