package com.pantrypilot.ai.internal.jobs;

/** Too many jobs are already waiting. The client should try again shortly. */
public class QueueFullException extends RuntimeException {

    public QueueFullException() {
        super("The server is busy processing other photos and links. Please try again in a minute.");
    }
}
