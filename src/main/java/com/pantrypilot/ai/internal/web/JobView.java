package com.pantrypilot.ai.internal.web;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.pantrypilot.ai.internal.jobs.AiJob;
import java.time.Instant;

/**
 * A job as the client sees it.
 *
 * @param result the proposal with its review details once the job is finished, otherwise null
 */
public record JobView(
        long id,
        String type,
        String status,
        long householdId,
        long userId,
        String input,
        Long pendingChangeId,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        @JsonRawValue String result) {

    static JobView of(AiJob job) {
        return new JobView(
                job.getId(),
                job.getType().name(),
                job.getStatus().name(),
                job.getHouseholdId(),
                job.getUserId(),
                job.getInputSummary(),
                job.getPendingChangeId(),
                job.getError(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getResultJson());
    }
}
