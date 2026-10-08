package com.goldenhour.dispatch.hospital;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A hospital and its live (simulated) bed census. Implements {@link HospitalView} so the same
 * time-to-treatment logic runs here and in the offline simulation.
 */
@Entity
@Table(name = "hospital")
public class Hospital implements HospitalView {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private double lat;
    private double lon;

    @Column(name = "road_node")
    private int roadNode;

    @Column(name = "table_index")
    private int tableIndex;

    /** Comma-separated {@link Capability} names. */
    private String capabilities;

    @Column(name = "icu_total") private int icuTotal;
    @Column(name = "icu_free") private int icuFree;
    @Column(name = "cardiac_total") private int cardiacTotal;
    @Column(name = "cardiac_free") private int cardiacFree;
    @Column(name = "trauma_total") private int traumaTotal;
    @Column(name = "trauma_free") private int traumaFree;
    @Column(name = "general_total") private int generalTotal;
    @Column(name = "general_free") private int generalFree;

    @Column(name = "er_queue")
    private int erQueue;

    @Column(name = "er_bays")
    private int erBays;

    @Column(name = "specialists_24x7")
    private boolean specialists24x7;

    /** Optimistic lock: two trips reserving the last bed at once -> one gets OptimisticLockException. */
    @Version
    private long version;

    @Transient
    private Set<Capability> capabilitySet;

    protected Hospital() {
    }

    public static Hospital fromSite(HospitalSite s, double initialOccupancy, java.util.Random rnd) {
        Hospital h = new Hospital();
        h.id = (long) s.index() + 1;
        h.name = s.name();
        h.lat = s.lat();
        h.lon = s.lon();
        h.roadNode = s.node();
        h.tableIndex = s.index();
        h.capabilities = s.capabilities().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        h.icuTotal = s.beds().get(BedType.ICU);
        h.cardiacTotal = s.beds().get(BedType.CARDIAC);
        h.traumaTotal = s.beds().get(BedType.TRAUMA);
        h.generalTotal = s.beds().get(BedType.GENERAL);
        for (BedType t : BedType.values()) {
            int total = h.totalBeds(t);
            int occupied = (int) Math.round(total * initialOccupancy + (rnd.nextDouble() - 0.5) * total * 0.3);
            h.setFree(t, Math.max(0, Math.min(total, total - occupied)));
        }
        h.erBays = s.erBays();
        h.erQueue = rnd.nextInt(s.erBays() + 1);
        h.specialists24x7 = s.specialists24x7();
        return h;
    }

    // ---------------------------------------------------------------- HospitalView

    @Override
    public long id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int tableIndex() {
        return tableIndex;
    }

    @Override
    public boolean has(Capability capability) {
        return capabilitySet().contains(capability);
    }

    @Override
    public int freeBeds(BedType bed) {
        return switch (bed) {
            case ICU -> icuFree;
            case CARDIAC -> cardiacFree;
            case TRAUMA -> traumaFree;
            case GENERAL -> generalFree;
        };
    }

    @Override
    public int erQueue() {
        return erQueue;
    }

    @Override
    public int erBays() {
        return erBays;
    }

    @Override
    public boolean onDuty(Specialist specialist, LocalDateTime at) {
        return DutyRoster.onDuty(id, specialist, at, specialists24x7);
    }

    // ---------------------------------------------------------------- beds

    public int totalBeds(BedType bed) {
        return switch (bed) {
            case ICU -> icuTotal;
            case CARDIAC -> cardiacTotal;
            case TRAUMA -> traumaTotal;
            case GENERAL -> generalTotal;
        };
    }

    public void setFree(BedType bed, int free) {
        int v = Math.max(0, Math.min(totalBeds(bed), free));
        switch (bed) {
            case ICU -> icuFree = v;
            case CARDIAC -> cardiacFree = v;
            case TRAUMA -> traumaFree = v;
            case GENERAL -> generalFree = v;
        }
    }

    /** Take one bed for an incoming patient. Returns false if none is free (patient admitted to a trolley). */
    public boolean reserveBed(BedType bed) {
        if (freeBeds(bed) <= 0) {
            return false;
        }
        setFree(bed, freeBeds(bed) - 1);
        return true;
    }

    public void releaseBed(BedType bed) {
        setFree(bed, freeBeds(bed) + 1);
    }

    public Set<Capability> capabilitySet() {
        if (capabilitySet == null) {
            capabilitySet = capabilities == null || capabilities.isBlank() ? EnumSet.noneOf(Capability.class)
                    : Arrays.stream(capabilities.split(",")).map(Capability::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Capability.class)));
        }
        return capabilitySet;
    }

    public void setErQueue(int erQueue) {
        this.erQueue = Math.max(0, erQueue);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getRoadNode() {
        return roadNode;
    }

    public boolean isSpecialists24x7() {
        return specialists24x7;
    }

    public long getVersion() {
        return version;
    }
}
