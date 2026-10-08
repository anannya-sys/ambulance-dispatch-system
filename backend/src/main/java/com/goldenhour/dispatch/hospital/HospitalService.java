package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.realtime.LivePublisher;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.TrafficBand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Picks the receiving hospital (time-to-treatment), reserves the bed and sends the ER pre-alert. */
@Service
public class HospitalService {

    private static final Logger log = LoggerFactory.getLogger(HospitalService.class);

    private final HospitalRepository hospitals;
    private final HospitalAlertRepository alerts;
    private final CityModel city;
    private final TransactionTemplate tx;
    private final LivePublisher live;

    public HospitalService(HospitalRepository hospitals, HospitalAlertRepository alerts, CityModel city,
                           TransactionTemplate tx, LivePublisher live) {
        this.hospitals = hospitals;
        this.alerts = alerts;
        this.city = city;
        this.tx = tx;
        this.live = live;
    }

    /** The chosen hospital plus a one-line explanation for the dashboard. */
    public record Decision(Hospital hospital, TreatmentEstimate estimate, BedType bed, boolean bedReserved,
                           String reason) {
    }

    /** All hospitals ranked by time to treatment for a patient at {@code node} (used by the API preview too). */
    public List<TreatmentEstimate> rank(int node, PatientType type, Severity severity, LocalDateTime now) {
        TrafficBand band = TrafficBand.at(now);
        return HospitalSelector.rank(hospitals.findAll(), CareRequirement.of(type, severity), severity,
                h -> city.hospitalTable().seconds(h.tableIndex(), node, band), now);
    }

    /**
     * Choose and reserve. Two ambulances may race for the last cardiac bed: the {@code @Version} column
     * makes the second save fail with an optimistic-lock error, and we simply re-rank and try again.
     */
    public Decision chooseAndReserve(int node, PatientType type, Severity severity, LocalDateTime now) {
        CareRequirement need = CareRequirement.of(type, severity);
        TrafficBand band = TrafficBand.at(now);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return tx.execute(status -> {
                    List<Hospital> all = hospitals.findAll();
                    TreatmentEstimate best = HospitalSelector.best(all, need, severity,
                            h -> city.hospitalTable().seconds(h.tableIndex(), node, band), now);
                    HospitalView nearest = HospitalSelector.nearestCapable(all, need,
                            h -> city.hospitalTable().seconds(h.tableIndex(), node, band));
                    Hospital chosen = (Hospital) best.hospital();
                    boolean reserved = chosen.reserveBed(need.bed());
                    chosen.setErQueue(chosen.erQueue() + 1);
                    hospitals.saveAndFlush(chosen);
                    return new Decision(chosen, best, need.bed(), reserved, explain(best, nearest, all, need, severity, node, band, now));
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Bed reservation race on attempt {}, retrying", attempt + 1);
            }
        }
        throw new IllegalStateException("Could not reserve a hospital bed after 3 attempts");
    }

    private String explain(TreatmentEstimate best, HospitalView nearest, List<Hospital> all, CareRequirement need,
                           Severity severity, int node, TrafficBand band, LocalDateTime now) {
        String head = String.format(Locale.ROOT, "%s: treatment in ~%d min", best.hospital().name(),
                Math.round(best.timeToTreatmentSeconds() / 60));
        if (nearest.id() == best.hospital().id()) {
            return head + " (nearest suitable hospital)";
        }
        TreatmentEstimate n = HospitalSelector.estimate(nearest, need, severity,
                city.hospitalTable().seconds(nearest.tableIndex(), node, band), now);
        String why = n.feasible()
                ? String.format(Locale.ROOT, "~%d min there", Math.round(n.timeToTreatmentSeconds() / 60))
                : n.reason();
        return head + "; skipped nearer " + nearest.name() + " (" + why + ")";
    }

    /** ER pre-alert: saved, and pushed to the hospital's screen over WebSocket. */
    public HospitalAlert preAlert(Decision d, long tripId, Severity severity, PatientType type,
                                  LocalDateTime now, LocalDateTime eta) {
        String need = d.bed().name().toLowerCase(Locale.ROOT) + " bed";
        long mins = Math.max(1, Math.round(java.time.Duration.between(now, eta).toSeconds() / 60.0));
        String msg = String.format(Locale.ROOT, "%s %s patient arriving in %d min. Prepare %s%s.",
                capital(severity.name()), type.name().toLowerCase(Locale.ROOT), mins, need,
                d.bedReserved() ? " (reserved)" : " (none free: make room)");
        HospitalAlert a = alerts.save(new HospitalAlert(d.hospital().getId(), tripId, now, eta, severity, type,
                d.bed(), msg));
        live.alert(a, d.hospital().getName());
        return a;
    }

    /** Called when the patient has been handed over: the ER queue entry is cleared. */
    public void handedOver(long hospitalId) {
        tx.executeWithoutResult(s -> hospitals.findById(hospitalId).ifPresent(h -> h.setErQueue(h.erQueue() - 1)));
    }

    private static String capital(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
