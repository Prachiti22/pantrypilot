package com.pantrypilot.ai.internal.jobs;

import com.pantrypilot.ai.internal.AiProcessingException;
import com.pantrypilot.ai.internal.AiProperties;
import com.pantrypilot.ai.internal.ProposalResult;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * The threaded AI job pipeline. Recognising a photo takes many seconds, so a
 * request only records a job and returns its id at once; the work itself runs
 * on a fixed pool of worker threads and the client polls for the result.
 *
 * <p>The pool has a bounded queue. When it is full new work is refused with
 * {@link QueueFullException} instead of piling up without limit.
 */
@Service
public class AiJobService {

    private static final Logger log = LoggerFactory.getLogger(AiJobService.class);

    private final JobStore store;
    private final ExecutorService workers;
    private final Duration timeout;

    public AiJobService(JobStore store, AiProperties properties) {
        AiProperties.Jobs config = properties.jobs();
        this.store = store;
        this.timeout = config.timeout();
        this.workers = new ThreadPoolExecutor(
                config.workers(),
                config.workers(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.queueCapacity()),
                namedThreads("ai-job-"));
    }

    /**
     * Records a job and starts {@code work} in the background.
     *
     * @param work produces the proposal; runs on a worker thread
     * @return the job, already saved, in state QUEUED
     * @throws QueueFullException when the queue is full
     */
    public AiJob submit(JobType type, long householdId, long userId, String inputSummary, Supplier<ProposalResult> work) {
        AiJob job = store.create(type, householdId, userId, inputSummary);
        long id = job.getId();
        try {
            CompletableFuture
                    .supplyAsync(() -> run(id, work), workers)
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((result, failure) -> finish(id, result, failure));
        } catch (RejectedExecutionException e) {
            store.fail(id, new QueueFullException().getMessage());
            throw new QueueFullException();
        }
        return job;
    }

    private ProposalResult run(long id, Supplier<ProposalResult> work) {
        if (!store.markRunning(id)) {
            return null;
        }
        ProposalResult result = work.get();
        // Stored here, on the worker thread, so the database write never runs on the timeout timer thread.
        store.complete(id, result);
        return result;
    }

    private void finish(long id, ProposalResult result, Throwable failure) {
        if (failure == null) {
            return;
        }
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        try {
            if (cause instanceof TimeoutException) {
                store.fail(id, "This took too long and was stopped. Please try again.");
            } else if (cause instanceof AiProcessingException) {
                store.fail(id, cause.getMessage());
            } else {
                log.error("AI job {} failed unexpectedly", id, cause);
                store.fail(id, "Something went wrong while processing this. Please try again.");
            }
        } catch (RuntimeException e) {
            log.error("Could not record the failure of AI job {}", id, e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void failJobsInterruptedByRestart() {
        int count = store.failInterrupted();
        if (count > 0) {
            log.info("Marked {} unfinished AI job(s) from a previous run as failed", count);
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
