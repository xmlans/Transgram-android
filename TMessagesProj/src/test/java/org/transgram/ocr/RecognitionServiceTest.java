package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RecognitionServiceTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private RecognitionService service;

    /** Runs callbacks on the calling (executor) thread, so tests need no main looper. */
    private static final RecognitionService.Poster DIRECT = new RecognitionService.Poster() {
        @Override public void post(Runnable r) { r.run(); }
    };

    private static final class FakeEngines implements RecognitionService.Engines {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        final CountDownLatch released = new CountDownLatch(1);
        volatile String answer = "你好";
        volatile Throwable toThrow;
        volatile CountDownLatch gate;       // when set, the engine blocks until it opens
        volatile CountDownLatch entered = new CountDownLatch(1);

        @Override public String ocr(String path, AtomicBoolean cancelled) throws RecognitionException {
            calls.incrementAndGet();
            int now = concurrent.incrementAndGet();
            maxConcurrent.set(Math.max(maxConcurrent.get(), now));
            try {
                entered.countDown();
                if (gate != null) {
                    try {
                        gate.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        // test is shutting down
                    }
                }
                if (toThrow instanceof RecognitionException) throw (RecognitionException) toThrow;
                if (toThrow instanceof RuntimeException) throw (RuntimeException) toThrow;
                if (toThrow instanceof Error) throw (Error) toThrow;
                return answer;
            } finally {
                concurrent.decrementAndGet();
            }
        }

        @Override public void releaseIdle() { released.countDown(); }
    }

    private static final class Recorder implements RecognitionService.Callback {
        final CountDownLatch done = new CountDownLatch(1);
        volatile boolean started;
        volatile String text;
        volatile boolean fromCache;
        volatile RecognitionError error;
        volatile String packId;

        @Override public void onStarted() { started = true; }
        @Override public void onResult(String t, boolean c) { text = t; fromCache = c; done.countDown(); }
        @Override public void onError(RecognitionError e, String p) { error = e; packId = p; done.countDown(); }

        void await() throws InterruptedException {
            assertTrue("callback did not arrive", done.await(5, TimeUnit.SECONDS));
        }
    }

    private RecognitionService make(FakeEngines e, long idleMs) throws Exception {
        service = new RecognitionService(e, new RecognitionResultStore(tmp.newFolder(), 100), DIRECT, idleMs);
        return service;
    }

    private static RecognitionRequest req(int msg) {
        return req(0, 42L, msg);
    }

    private static RecognitionRequest req(int account, long dialog, int msg) {
        return new RecognitionRequest(RecognitionRequest.Kind.OCR, account, dialog, msg, "/x/" + msg);
    }

    @After public void stop() {
        if (service != null) service.shutdown();
    }

    @Test public void maintenanceWaitsForRecognitionAndReleasesEngineBeforeDeleting() throws Exception {
        FakeEngines e = new FakeEngines();
        e.gate = new CountDownLatch(1);
        make(e, 60000);
        service.submit(req(1), new Recorder());
        assertTrue(e.entered.await(5, TimeUnit.SECONDS));
        CountDownLatch deleted = new CountDownLatch(1);
        AtomicBoolean releasedFirst = new AtomicBoolean();
        service.maintainModels(() -> {
            releasedFirst.set(e.released.getCount() == 0 && e.concurrent.get() == 0);
            deleted.countDown();
        });
        assertEquals(1, deleted.getCount());
        e.gate.countDown();
        assertTrue(deleted.await(5, TimeUnit.SECONDS));
        assertTrue(releasedFirst.get());
    }

    @Test public void runsTheEngineStoresAndAnswersTheSecondTimeFromTheCache() throws Exception {
        FakeEngines e = new FakeEngines();
        make(e, 60000);
        Recorder first = new Recorder();
        assertEquals(RecognitionService.Submitted.STARTED, service.submit(req(1), first));
        first.await();
        assertTrue(first.started);
        assertEquals("你好", first.text);
        assertFalse(first.fromCache);
        Recorder second = new Recorder();
        assertEquals(RecognitionService.Submitted.CACHED, service.submit(req(1), second));
        second.await();
        assertEquals("你好", second.text);
        assertTrue(second.fromCache);
        assertEquals(1, e.calls.get());
    }

    @Test public void theSameJobWhileRunningIsRefused() throws Exception {
        FakeEngines e = new FakeEngines();
        e.gate = new CountDownLatch(1);
        make(e, 60000);
        Recorder r = new Recorder();
        assertEquals(RecognitionService.Submitted.STARTED, service.submit(req(7), r));
        assertTrue(e.entered.await(5, TimeUnit.SECONDS));
        assertTrue(service.isRunning(req(7)));
        assertEquals(RecognitionService.Submitted.ALREADY_RUNNING, service.submit(req(7), new Recorder()));
        e.gate.countDown();
        r.await();
        assertEquals(1, e.calls.get());
        assertFalse(service.isRunning(req(7)));
    }

    @Test public void theSameMessageIdInOtherChatsIsADifferentJob() throws Exception {
        FakeEngines e = new FakeEngines();
        make(e, 60000);
        Recorder a = new Recorder(), b = new Recorder(), c = new Recorder();
        service.submit(req(0, 42L, 3), a);
        service.submit(req(0, 43L, 3), b);
        service.submit(req(1, 42L, 3), c);
        a.await();
        b.await();
        c.await();
        assertEquals(3, e.calls.get());
    }

    @Test public void jobsNeverRunAtTheSameTime() throws Exception {
        final FakeEngines e = new FakeEngines();
        make(e, 60000);
        Recorder[] rs = new Recorder[5];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = new Recorder();
            service.submit(req(100 + i), rs[i]);
        }
        for (Recorder r : rs) r.await();
        assertEquals(1, e.maxConcurrent.get());
        assertEquals(5, e.calls.get());
    }

    @Test public void emptyTextIsReportedAndNotCached() throws Exception {
        FakeEngines e = new FakeEngines();
        e.answer = "   ";
        make(e, 60000);
        Recorder r = new Recorder();
        service.submit(req(9), r);
        r.await();
        assertEquals(RecognitionError.EMPTY, r.error);
        assertNull(r.text);
        Recorder again = new Recorder();
        assertEquals(RecognitionService.Submitted.STARTED, service.submit(req(9), again));
        again.await();
        assertEquals(2, e.calls.get());
    }

    @Test public void engineErrorsAreMappedToReasons() throws Exception {
        FakeEngines e = new FakeEngines();
        make(e, 60000);

        e.toThrow = new RecognitionException(RecognitionError.MODEL_MISSING, RecognitionEnv.OCR_PACK, null);
        Recorder missing = new Recorder();
        service.submit(req(21), missing);
        missing.await();
        assertEquals(RecognitionError.MODEL_MISSING, missing.error);
        assertEquals(RecognitionEnv.OCR_PACK, missing.packId);

        e.toThrow = new OutOfMemoryError("test");
        Recorder oom = new Recorder();
        service.submit(req(22), oom);
        oom.await();
        assertEquals(RecognitionError.OUT_OF_MEMORY, oom.error);

        e.toThrow = new IllegalStateException("boom");
        Recorder other = new Recorder();
        service.submit(req(23), other);
        other.await();
        assertEquals(RecognitionError.ENGINE_FAILED, other.error);
    }

    @Test public void cancelStopsWithoutAnyCallback() throws Exception {
        FakeEngines e = new FakeEngines();
        e.gate = new CountDownLatch(1);
        make(e, 60000);
        Recorder r = new Recorder();
        RecognitionRequest q = req(31);
        service.submit(q, r);
        assertTrue(e.entered.await(5, TimeUnit.SECONDS));
        service.cancel(q);
        e.gate.countDown();
        assertFalse(r.done.await(300, TimeUnit.MILLISECONDS));
        assertNull(r.text);
        assertNull(r.error);
    }

    @Test public void enginesAreReleasedAfterBeingIdle() throws Exception {
        FakeEngines e = new FakeEngines();
        make(e, 50);
        Recorder r = new Recorder();
        service.submit(req(41), r);
        r.await();
        assertTrue(e.released.await(5, TimeUnit.SECONDS));
    }
}
