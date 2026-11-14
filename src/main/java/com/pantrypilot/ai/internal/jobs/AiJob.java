package com.pantrypilot.ai.internal.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One photo, receipt or link being turned into a proposal in the background. */
@Entity
@Table(name = "ai_job")
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "household_id", nullable = false)
    private long householdId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    /** File name or link, for display only. */
    @Column(name = "input_summary")
    private String inputSummary;

    @Column(name = "result_json")
    private String resultJson;

    @Column(name = "pending_change_id")
    private Long pendingChangeId;

    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AiJob() {
    }

    AiJob(JobType type, long householdId, long userId, String inputSummary) {
        this.type = type;
        this.status = JobStatus.QUEUED;
        this.householdId = householdId;
        this.userId = userId;
        this.inputSummary = inputSummary;
        this.createdAt = Instant.now();
    }

    void start() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    void succeed(String resultJson, long pendingChangeId) {
        this.status = JobStatus.SUCCEEDED;
        this.resultJson = resultJson;
        this.pendingChangeId = pendingChangeId;
        this.finishedAt = Instant.now();
    }

    void fail(String error, String resultJson) {
        this.status = JobStatus.FAILED;
        this.error = error;
        this.resultJson = resultJson;
        this.finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public JobType getType() {
        return type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public long getHouseholdId() {
        return householdId;
    }

    public long getUserId() {
        return userId;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Long getPendingChangeId() {
        return pendingChangeId;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
