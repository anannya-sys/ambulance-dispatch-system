package com.goldenhour.dispatch.incident;

public enum IncidentStatus {
    /** Waiting in the priority queue for a free ambulance. */
    QUEUED,
    /** An ambulance is on it. */
    ASSIGNED,
    /** Patient handed over at a hospital. */
    RESOLVED,
    CANCELLED
}
