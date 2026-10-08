package com.goldenhour.dispatch.hospital;

import java.time.LocalDateTime;

/**
 * Read-only view of a hospital's live state. Implemented by the JPA entity (live system) and by the
 * in-memory hospital of the offline simulation, so both use the exact same selection logic.
 */
public interface HospitalView {

    long id();

    String name();

    /** Index into the hospital {@link com.goldenhour.dispatch.routing.TravelTimeTable}. */
    int tableIndex();

    boolean has(Capability capability);

    int freeBeds(BedType bed);

    /** Patients currently waiting in the emergency room. */
    int erQueue();

    /** Treatment bays working in parallel. */
    int erBays();

    boolean onDuty(Specialist specialist, LocalDateTime at);
}
