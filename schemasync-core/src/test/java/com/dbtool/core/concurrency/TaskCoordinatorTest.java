package com.dbtool.core.concurrency;

import com.dbtool.core.query.StatementCancellationMonitor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TaskCoordinatorTest {

    @Test
    void testSubmitCompletesAndCleansActiveTrackingOnVirtualThread() throws Exception {
        try (TaskCoordinator coordinator = new TaskCoordinator()) {
            AtomicBoolean ranOnVirtualThread = new AtomicBoolean(false);
            TaskCoordinator.ManagedTask<Integer> task = coordinator.submit("compute-sum", () -> {
                ranOnVirtualThread.set(Thread.currentThread().isVirtual());
                return 40 + 2;
            });
            assertEquals(42, task.getFuture().get(5, TimeUnit.SECONDS));
            assertTrue(ranOnVirtualThread.get(), "TaskCoordinator must execute on Java 21 virtual threads");
            // Give finally block a brief tick to remove active entry
            long deadline = System.currentTimeMillis() + 1000;
            while (coordinator.getActiveTaskCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(0, coordinator.getActiveTaskCount());
        }
    }

    @Test
    void testCancelInvokesStatementCancellationMonitor() throws Exception {
        try (TaskCoordinator coordinator = new TaskCoordinator();
             Connection conn = DriverManager.getConnection("jdbc:h2:mem:cancel_test_" + System.nanoTime(), "sa", "")) {
            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean cancelTriggered = new AtomicBoolean(false);
            StatementCancellationMonitor monitor = new StatementCancellationMonitor() {
                @Override
                public boolean cancel() {
                    cancelTriggered.set(true);
                    return super.cancel();
                }
            };

            try (Statement st = conn.createStatement()) {
                TaskCoordinator.ManagedTask<Void> task = coordinator.submitRunnable("long-query", monitor, () -> {
                    monitor.register(st);
                    started.countDown();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        monitor.unregister();
                    }
                });

                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertEquals(1, coordinator.getActiveTaskCount());
                assertTrue(coordinator.cancel(task.getTaskId()));
                assertTrue(cancelTriggered.get(), "Cancel should trigger StatementCancellationMonitor.cancel()");
                assertEquals(0, coordinator.getActiveTaskCount());
            }
        }
    }
}
