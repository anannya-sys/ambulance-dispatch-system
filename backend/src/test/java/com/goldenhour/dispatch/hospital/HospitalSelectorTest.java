package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HospitalSelectorTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 14, 0);
    static final CareRequirement HEART_ATTACK = CareRequirement.of(PatientType.CARDIAC, Severity.CRITICAL);

    @Test
    void cardiacPatientNeedsACathLab() {
        assertThat(HEART_ATTACK.capability()).isEqualTo(Capability.CATH_LAB);
        assertThat(HEART_ATTACK.bed()).isEqualTo(BedType.CARDIAC);
        assertThat(CareRequirement.of(PatientType.CARDIAC, Severity.MINOR).capability()).isNull();
    }

    @Test
    void skipsNearerHospitalWithoutTheRightFacility() {
        FakeHospital near = new FakeHospital(0);                     // 5 min away, no cath lab
        FakeHospital far = new FakeHospital(1, Capability.CATH_LAB); // 12 min away
        Map<Long, Double> travel = Map.of(0L, 300.0, 1L, 720.0);

        TreatmentEstimate best = HospitalSelector.best(List.of(near, far), HEART_ATTACK, Severity.CRITICAL,
                h -> travel.get(h.id()), NOW);

        assertThat(best.hospital()).isSameAs(far);
    }

    @Test
    void prefersSlightlyFartherHospitalWhenTheNearOneMustCallInItsSpecialist() {
        FakeHospital near = new FakeHospital(0, Capability.CATH_LAB);
        near.specialistsOnDuty = false; // +30 min call-in
        FakeHospital far = new FakeHospital(1, Capability.CATH_LAB);
        Map<Long, Double> travel = Map.of(0L, 300.0, 1L, 900.0);

        TreatmentEstimate best = HospitalSelector.best(List.of(near, far), HEART_ATTACK, Severity.CRITICAL,
                h -> travel.get(h.id()), NOW);

        assertThat(best.hospital()).isSameAs(far);
        // the baseline rule would have picked the nearer one
        assertThat(HospitalSelector.nearestCapable(List.of(near, far), HEART_ATTACK, h -> travel.get(h.id())))
                .isSameAs(near);
    }

    @Test
    void hospitalWithNoFreeBedIsInfeasible() {
        FakeHospital full = new FakeHospital(0, Capability.CATH_LAB);
        full.beds.put(BedType.CARDIAC, 0);
        TreatmentEstimate e = HospitalSelector.estimate(full, HEART_ATTACK, Severity.CRITICAL, 300, NOW);
        assertThat(e.feasible()).isFalse();
        assertThat(e.reason()).contains("no free cardiac bed");
    }

    @Test
    void whenEverythingIsFullFallsBackToNearestCapable() {
        FakeHospital a = new FakeHospital(0, Capability.CATH_LAB);
        FakeHospital b = new FakeHospital(1, Capability.CATH_LAB);
        a.beds.put(BedType.CARDIAC, 0);
        b.beds.put(BedType.CARDIAC, 0);
        Map<Long, Double> travel = Map.of(0L, 600.0, 1L, 400.0);
        TreatmentEstimate best = HospitalSelector.best(List.of(a, b), HEART_ATTACK, Severity.CRITICAL,
                h -> travel.get(h.id()), NOW);
        assertThat(best.hospital()).isSameAs(b);
    }

    @Test
    void criticalPatientsJumpMostOfTheErQueue() {
        FakeHospital h = new FakeHospital(0, Capability.CATH_LAB);
        h.erQueue = 8;
        double critical = HospitalSelector.estimate(h, HEART_ATTACK, Severity.CRITICAL, 0, NOW).erWaitSeconds();
        double serious = HospitalSelector.estimate(h, HEART_ATTACK, Severity.SERIOUS, 0, NOW).erWaitSeconds();
        assertThat(critical).isLessThan(serious);
    }
}
