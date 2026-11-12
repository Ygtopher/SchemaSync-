package com.dbtool.core.concurrency;

import com.dbtool.core.query.StatementCancellationMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Shared coordinator for background database/tool tasks with named task tracking and
 * deterministic cancellation wired to JDBC {@link StatementCancellationMonitor} instances.
 */
public class TaskCoordinator implements AutoCloseable {

    private static final TaskCoordinator SHARED = new TaskCoordinator();

    public static TaskCoordinator shared() {
        return SHARED;
    }

    public static final class ManagedTask<T> {
        private final String taskId;
        private final String name;
        private final CompletableFuture<T> future;
        private final StatementCancellationMonitor cancellationMonitor;

        ManagedTask(String taskId, String name, CompletableFuture<T> future, StatementCancellationMonitor cancellationMonitor) {
            this.taskId = taskId;
            this.name = name;
            this.future = future;
            this.cancellationMonitor = cancellationMonitor;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getName() {
            return name;
        }

        public CompletableFuture<T> getFuture() {
            return future;
        }
    }

    private final ExecutorService executor;
    private final Map<String, ActiveHandle> activeTasks = new ConcurrentHashMap<>();

    private static final class ActiveHandle {
        final String name;
        final StatementCancellationMonitor monitor;
        volatile Future<?> rawFuture;
        final CompletableFuture<?> completableFuture;

        ActiveHandle(String name, StatementCancellationMonitor monitor, CompletableFuture<?> completableFuture) {
            this.name = name;
            this.monitor = monitor;
            this.completableFuture = completableFuture;
        }
    }

    public TaskCoordinator() {
        // Java 21 Project Loom virtual threads allow high-concurrency blocking JDBC/HTTP tasks without thread-pool bloat
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public <T> ManagedTask<T> submit(String taskName, Callable<T> work) {
        return submit(taskName, null, work);
    }

    public <T> ManagedTask<T> submit(String taskName, StatementCancellationMonitor monitor, Callable<T> work) {
        String id = UUID.randomUUID().toString();
        String effectiveName = (taskName == null || taskName.trim().isEmpty()) ? "background-task" : taskName.trim();
        CompletableFuture<T> cf = new CompletableFuture<>();
        ActiveHandle handle = new ActiveHandle(effectiveName, monitor, cf);
        activeTasks.put(id, handle);

        Future<?> raw = executor.submit(() -> {
            try {
                if (Thread.currentThread().isInterrupted() || cf.isCancelled()) {
                    cf.cancel(false);
                    return;
                }
                T result = work.call();
                cf.complete(result);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
            } finally {
                activeTasks.remove(id);
            }
        });
        handle.rawFuture = raw;
        return new ManagedTask<>(id, effectiveName, cf, monitor);
    }

    public ManagedTask<Void> submitRunnable(String taskName, Runnable work) {
        return submit(taskName, null, () -> {
            work.run();
            return null;
        });
    }

    public ManagedTask<Void> submitRunnable(String taskName, StatementCancellationMonitor monitor, Runnable work) {
        return submit(taskName, monitor, () -> {
            work.run();
            return null;
        });
    }

    public boolean cancel(String taskId) {
        if (taskId == null) return false;
        ActiveHandle handle = activeTasks.remove(taskId);
        if (handle == null) return false;
        if (handle.monitor != null) {
            handle.monitor.cancel();
        }
        boolean cancelled = handle.completableFuture.cancel(true);
        if (handle.rawFuture != null) {
            cancelled = handle.rawFuture.cancel(true) || cancelled;
        }
        return cancelled;
    }

    public int cancelAll() {
        int count = 0;
        for (String id : new ArrayList<>(activeTasks.keySet())) {
            if (cancel(id)) count++;
        }
        return count;
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public List<String> getActiveTaskNames() {
        List<String> names = new ArrayList<>();
        for (ActiveHandle handle : activeTasks.values()) {
            names.add(handle.name);
        }
        return names;
    }

    @Override
    public void close() {
        cancelAll();
        executor.shutdownNow();
    }
}
