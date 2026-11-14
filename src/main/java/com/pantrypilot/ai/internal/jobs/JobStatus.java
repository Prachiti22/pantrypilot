package com.pantrypilot.ai.internal.jobs;

public enum JobStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED;
    }
}
