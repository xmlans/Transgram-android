package org.transgram.ocr;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs OCR jobs one at a time (memory!), answers repeats from the result store, and refuses to start the same job
 * twice. Callbacks go through the Poster (the main thread in the app). No Android classes, so it is unit-tested with
 * fake engines.
 */
public final class RecognitionService {
    public interface Engines {
        String ocr(String path, AtomicBoolean cancelled) throws RecognitionException;
        /** Called after the service has been idle for a while: free model memory. */
        void releaseIdle();
    }

    public interface Poster {
        void post(Runnable r);
    }

    public interface Callback {
        /** The job left the queue and is running now. */
        void onStarted();
        /** @param fromCache true when the answer came from the result store without running an engine */
        void onResult(String text, boolean fromCache);
        void onError(RecognitionError error, String packId);
    }

    public enum Submitted { STARTED, ALREADY_RUNNING, CACHED }

    private final Engines engines;
    private final RecognitionResultStore store;
    private final Poster poster;
    private final long idleReleaseMs;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final Map<String, AtomicBoolean> running = new HashMap<>();
    /** Only touched on the executor thread. */
    private ScheduledFuture<?> idleRelease;

    public RecognitionService(Engines engines, RecognitionResultStore store, Poster poster, long idleReleaseMs) {
        this.engines = engines;
        this.store = store;
        this.poster = poster;
        this.idleReleaseMs = idleReleaseMs;
        executor.setRemoveOnCancelPolicy(true);
    }

    public RecognitionResultStore store() {
        return store;
    }

    /** Run model maintenance between OCR jobs, after releasing native sessions that hold model files open. */
    public void maintainModels(Runnable task) {
        executor.execute(() -> {
            if (idleRelease != null) idleRelease.cancel(false);
            engines.releaseIdle();
            task.run();
        });
    }

    public Submitted submit(final RecognitionRequest request, final Callback callback) {
        final String cached = store.get(request);
        if (cached != null) {
            poster.post(new Runnable() {
                @Override public void run() { callback.onResult(cached, true); }
            });
            return Submitted.CACHED;
        }
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        synchronized (running) {
            if (running.containsKey(request.key())) return Submitted.ALREADY_RUNNING;
            running.put(request.key(), cancelled);
        }
        executor.execute(new Runnable() {
            @Override public void run() { runJob(request, callback, cancelled); }
        });
        return Submitted.STARTED;
    }

    public boolean isRunning(RecognitionRequest request) {
        synchronized (running) {
            return running.containsKey(request.key());
        }
    }

    /** Asks a queued or running job to stop; it ends without any callback. */
    public void cancel(RecognitionRequest request) {
        synchronized (running) {
            final AtomicBoolean c = running.get(request.key());
            if (c != null) c.set(true);
        }
    }

    private void runJob(final RecognitionRequest request, final Callback callback, AtomicBoolean cancelled) {
        if (idleRelease != null) idleRelease.cancel(false);
        try {
            if (cancelled.get()) return;
            poster.post(new Runnable() {
                @Override public void run() { callback.onStarted(); }
            });
            final String text;
            switch (request.kind) {
                case OCR:
                default:
                    text = engines.ocr(request.mediaPath, cancelled);
                    break;
            }
            if (cancelled.get()) return;
            if (text == null || text.trim().isEmpty()) {
                error(callback, RecognitionError.EMPTY, null);
            } else {
                store.put(request, text);
                poster.post(new Runnable() {
                    @Override public void run() { callback.onResult(text, false); }
                });
            }
        } catch (CancellationException e) {
            // the caller cancelled: no callback
        } catch (RecognitionException e) {
            error(callback, e.error, e.packId);
        } catch (OutOfMemoryError e) {
            error(callback, RecognitionError.OUT_OF_MEMORY, null);
        } catch (Throwable t) {
            error(callback, RecognitionError.ENGINE_FAILED, null);
        } finally {
            synchronized (running) {
                running.remove(request.key());
            }
            try {
                idleRelease = executor.schedule(new Runnable() {
                    @Override public void run() { engines.releaseIdle(); }
                }, idleReleaseMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // shut down
            }
        }
    }

    private void error(final Callback callback, final RecognitionError error, final String packId) {
        poster.post(new Runnable() {
            @Override public void run() { callback.onError(error, packId); }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
