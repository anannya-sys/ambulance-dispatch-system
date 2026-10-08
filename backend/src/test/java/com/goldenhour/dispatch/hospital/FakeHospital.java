package com.goldenhour.dispatch.hospital;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Simple in-memory {@link HospitalView} for tests. */
class FakeHospital implements HospitalView {

    final long id;
    final Set<Capability> caps;
    final Map<BedType, Integer> beds = new EnumMap<>(BedType.class);
    int erQueue;
    int erBays = 2;
    boolean specialistsOnDuty = true;

    FakeHospital(long id, Capability... caps) {
        this.id = id;
        this.caps = caps.length == 0 ? EnumSet.noneOf(Capability.class) : EnumSet.of(caps[0], caps);
        for (BedType b : BedType.values()) {
            beds.put(b, 5);
        }
    }

    @Override public long id() { return id; }
    @Override public String name() { return "H" + id; }
    @Override public int tableIndex() { return (int) id; }
    @Override public boolean has(Capability c) { return caps.contains(c); }
    @Override public int freeBeds(BedType b) { return beds.get(b); }
    @Override public int erQueue() { return erQueue; }
    @Override public int erBays() { return erBays; }
    @Override public boolean onDuty(Specialist s, LocalDateTime at) { return specialistsOnDuty; }
}
