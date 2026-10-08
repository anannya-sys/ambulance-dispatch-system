package com.goldenhour.dispatch.incident;

import com.goldenhour.dispatch.common.NotFoundException;
import com.goldenhour.dispatch.dispatch.DispatchService;
import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.geo.GeoUtils;
import com.goldenhour.dispatch.geo.Localities;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.messaging.DispatchEvent;
import com.goldenhour.dispatch.messaging.EventPublisher;
import com.goldenhour.dispatch.routing.CityModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Emergency intake: validates the location, snaps it to a road, queues the call and dispatches at once. */
@Service
public class IncidentService {

    /** A caller further than this from any road we know is outside the service area. */
    static final double MAX_SNAP_METERS = 1500;

    private final IncidentRepository incidents;
    private final DispatchService dispatch;
    private final CityModel city;
    private final SimClock clock;
    private final DemandModel demand;
    private final EventPublisher events;
    private final Random rnd = new Random(11);

    public IncidentService(IncidentRepository incidents, DispatchService dispatch, CityModel city, SimClock clock,
                           DemandModel demand, EventPublisher events) {
        this.incidents = incidents;
        this.dispatch = dispatch;
        this.city = city;
        this.clock = clock;
        this.demand = demand;
        this.events = events;
    }

    public record Created(Incident incident, Optional<DispatchService.Assignment> assignment) {
    }

    /** New call: save it and try to dispatch immediately (the scheduler retries if no ambulance is free). */
    public Created create(double lat, double lon, Severity severity, PatientType type, IncidentSource source,
                          String description) {
        Incident inc = save(lat, lon, severity, type, source, description, null);
        Optional<DispatchService.Assignment> assignment = dispatch.tryAssign(inc);
        // the claim was a direct UPDATE, so re-read to return the current status
        return new Created(incidents.findById(inc.getId()).orElse(inc), assignment);
    }

    private Incident save(double lat, double lon, Severity severity, PatientType type, IncidentSource source,
                          String description, String surgeGroup) {
        int node = city.graph().nearestNode(lat, lon);
        double snap = GeoUtils.distanceMeters(lat, lon, city.graph().lat(node), city.graph().lon(node));
        if (snap > MAX_SNAP_METERS) {
            throw new IllegalArgumentException("Location is outside the Mumbai service area");
        }
        LocalDateTime now = clock.now();
        String area = Localities.nearest(lat, lon);
        Incident inc = incidents.save(new Incident(now, lat, lon, node, severity, type, source, area,
                description, surgeGroup));
        demand.add(now, lat, lon); // the forecast keeps learning from live calls
        events.dispatch(new DispatchEvent("CALL", null, inc.getId(), null, null, null, severity.name(),
                String.format(Locale.ROOT, "New %s %s call in %s", severity.name().toLowerCase(Locale.ROOT),
                        type.name().toLowerCase(Locale.ROOT), area), now));
        return inc;
    }

    /**
     * Mass-casualty incident (building collapse, bus crash): {@code casualties} patients at one place.
     * Each becomes its own call, so several ambulances go at once and the hospital selector spreads
     * patients across hospitals as beds get reserved.
     */
    public List<Incident> createSurge(double lat, double lon, int casualties, String description) {
        String group = "SURGE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        List<Incident> out = new ArrayList<>();
        for (int i = 0; i < casualties; i++) {
            Severity s;
            PatientType t;
            double jLat;
            double jLon;
            synchronized (rnd) {
                double u = rnd.nextDouble();
                s = u < 0.3 ? Severity.CRITICAL : u < 0.7 ? Severity.SERIOUS : Severity.MINOR;
                t = rnd.nextDouble() < 0.8 ? PatientType.TRAUMA : PatientType.RESPIRATORY;
                jLat = lat + rnd.nextGaussian() * 0.0006;
                jLon = lon + rnd.nextGaussian() * 0.0006;
            }
            out.add(save(jLat, jLon, s, t, IncidentSource.SURGE, description + " (patient " + (i + 1) + ")", group));
        }
        dispatch.dispatchQueue();
        return out;
    }

    public Incident get(long id) {
        return incidents.findById(id).orElseThrow(() -> new NotFoundException("Incident", id));
    }

    /** Waiting calls, highest priority first. */
    public List<Incident> queue() {
        LocalDateTime now = clock.now();
        return incidents.findByStatus(IncidentStatus.QUEUED).stream()
                .sorted((a, b) -> Double.compare(
                        DispatchPriority.score(b.getSeverity(), SimClock.secondsBetween(b.getCreatedAt(), now)),
                        DispatchPriority.score(a.getSeverity(), SimClock.secondsBetween(a.getCreatedAt(), now))))
                .toList();
    }
}
