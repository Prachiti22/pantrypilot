package com.pantrypilot.ai.internal.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pantrypilot.ai.internal.ProposalResult;
import com.pantrypilot.shared.PendingChangeService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change of a job's state, each in its own database transaction.
 *
 * <p>A job can be finished from two threads: the worker that produced the
 * result, and the timer that gives up on it. Each finishing method locks the
 * job's row first and does nothing if the job is already finished, so exactly
 * one of them wins and a late result is never submitted for review.
 */
@Component
public class JobStore {

    static final String NOTHING_FOUND = "Nothing was recognised. Try a clearer photo or a different link.";

    private final AiJobRepository repository;
    private final PendingChangeService pendingChanges;
    private final ObjectMapper mapper;

    public JobStore(AiJobRepository repository, PendingChangeService pendingChanges, ObjectMapper mapper) {
        this.repository = repository;
        this.pendingChanges = pendingChanges;
        this.mapper = mapper;
    }

    @Transactional
    public AiJob create(JobType type, long householdId, long userId, String inputSummary) {
        String summary = inputSummary == null ? null
                : inputSummary.length() > 500 ? inputSummary.substring(0, 500) : inputSummary;
        return repository.save(new AiJob(type, householdId, userId, summary));
    }

    /** @return false when the job is no longer waiting, in which case the worker must not run it */
    @Transactional
    public boolean markRunning(long id) {
        Optional<AiJob> job = repository.findForUpdate(id);
        if (job.isEmpty() || job.get().getStatus() != JobStatus.QUEUED) {
            return false;
        }
        job.get().start();
        return true;
    }

    /**
     * Stores the result and puts the proposal on the review screen, unless the
     * job was already finished by the timeout. A proposal with no lines is a failure.
     */
    @Transactional
    public void complete(long id, ProposalResult result) {
        Optional<AiJob> found = repository.findForUpdate(id);
        if (found.isEmpty() || found.get().getStatus().isFinished()) {
            return;
        }
        AiJob job = found.get();
        String json = toJson(result);
        if (result.proposal().lines().isEmpty()) {
            job.fail(NOTHING_FOUND, json);
            return;
        }
        long pendingChangeId = pendingChanges.submit(result.proposal());
        job.succeed(json, pendingChangeId);
    }

    @Transactional
    public void fail(long id, String message) {
        repository.findForUpdate(id)
                .filter(job -> !job.getStatus().isFinished())
                .ifPresent(job -> job.fail(message, null));
    }

    /** Jobs left unfinished by a previous run can never complete, because their work lived in memory. */
    @Transactional
    public int failInterrupted() {
        List<AiJob> stale = repository.findByStatusIn(List.of(JobStatus.QUEUED, JobStatus.RUNNING));
        stale.forEach(job -> job.fail("The server restarted before this finished. Please try again.", null));
        return stale.size();
    }

    @Transactional(readOnly = true)
    public Optional<AiJob> find(long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<AiJob> recent(long householdId) {
        return repository.findTop20ByHouseholdIdOrderByIdDesc(householdId);
    }

    private String toJson(ProposalResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the proposal", e);
        }
    }
}
