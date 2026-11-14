package com.pantrypilot.shared;

/** Provided by the core module (Prachiti). Used by the AI module. */
public interface PendingChangeService {

    /**
     * Puts an AI proposal on the user's review screen. Nothing is applied until the user confirms.
     *
     * @return the id of the pending change
     */
    long submit(ProposedChange change);
}
