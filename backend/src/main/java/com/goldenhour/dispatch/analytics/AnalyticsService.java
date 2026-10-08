package com.goldenhour.dispatch.analytics;

import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.geo.GridCell;
import com.goldenhour.dispatch.geo.Localities;
import com.goldenhour.dispatch.hospital.HospitalAlertRepository;
import com.goldenhour.dispatch.incident.Incident;
import com.goldenhour.dispatch.incident.IncidentRepository;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.live.LiveSettings;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.routing.TrafficModel;
import com.goldenhour.dispatch.trip.Trip;
import com.goldenhour.dispatch.trip.TripRepository;
import com.goldenhour.dispatch.trip.TripStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Live KPIs over the last 24 simulated hours: response times, golden hour, slow zones, hourly demand. */
@Service
public class AnalyticsService {

    private final TripRepository trips;
    private final IncidentRepository incidents;
    private final HospitalAlertRepository alerts;
    private final DemandModel demand;
    private final LiveSettings settings;
    private final SimClock clock;

    public AnalyticsService(TripRepository trips, IncidentRepository incidents, HospitalAlertRepository alerts,
                            DemandModel demand, LiveSettings settings, SimClock clock) {
        this.trips = trips;
        this.incidents = incidents;
        this.alerts = alerts;
        this.demand = demand;
        this.settings = settings;
        this.clock = clock;
    }

    public record Summary(long calls, long completedTrips, double avgResponseMin, double avgCriticalResponseMin,
                          double criticalWithin15Pct, double goldenHourPct, long corridorTrips, long signalsCleared,
                          double corridorMinutesSaved, long preAlerts, long reassignments, long surgeCalls) {
    }

    public record Zone(String area, double lat, double lon, long trips, double avgResponseMin) {
    }

    public record HourPoint(String hour, long calls, double forecast) {
    }

    public Summary summary() {
        LocalDateTime since = clock.now().minusHours(24);
        List<Incident> calls = incidents.findByCreatedAtAfter(since);
        Map<Long, Incident> byId = calls.stream().collect(Collectors.toMap(Incident::getId, Function.identity()));
        List<Trip> window = trips.findByAssignedAtAfter(since);
        List<Trip> responded = window.stream().filter(t -> t.getResponseSeconds() != null).toList();
        List<Trip> critical = responded.stream()
                .filter(t -> byId.containsKey(t.getIncidentId()) && byId.get(t.getIncidentId()).getSeverity() == Severity.CRITICAL)
                .toList();
        List<Trip> criticalAtHospital = critical.stream().filter(t -> t.getCallToHospitalSeconds() != null).toList();
        long corridor = window.stream().filter(Trip::isGreenCorridor).count();
        long signals = window.stream().mapToLong(Trip::getSignalsCleared).sum();
        long reassigned = window.stream().filter(t -> t.getStatus() == TripStatus.CANCELLED
                && t.getCancelReason() != null && !t.getCancelReason().startsWith("System")).count();
        long preAlerts = alerts.findTop50ByOrderByCreatedAtDesc().stream().filter(a -> a.getCreatedAt().isAfter(since)).count();
        return new Summary(calls.size(), window.stream().filter(t -> t.getStatus() == TripStatus.CLOSED).count(),
                avgMin(responded), avgMin(critical),
                pct(critical, t -> t.getResponseSeconds() <= 15 * 60),
                pct(criticalAtHospital, t -> t.getCallToHospitalSeconds() <= 60 * 60),
                corridor, signals,
                signals * (TrafficModel.SIGNAL_DELAY_SECONDS - TrafficModel.CORRIDOR_SIGNAL_DELAY_SECONDS) / 60.0,
                preAlerts, reassigned, calls.stream().filter(i -> i.getSurgeGroup() != null).count());
    }

    /** Average response time per ~1 km cell: the slow-zone heatmap. */
    public List<Zone> zones() {
        LocalDateTime since = clock.now().minusHours(24);
        Map<Long, Incident> byId = incidents.findByCreatedAtAfter(since.minusHours(2)).stream()
                .collect(Collectors.toMap(Incident::getId, Function.identity()));
        Map<GridCell, List<Trip>> cells = new HashMap<>();
        for (Trip t : trips.findByAssignedAtAfter(since)) {
            Incident i = byId.get(t.getIncidentId());
            if (i != null && t.getResponseSeconds() != null) {
                cells.computeIfAbsent(GridCell.of(i.getLat(), i.getLon()), k -> new ArrayList<>()).add(t);
            }
        }
        return cells.entrySet().stream().map(e -> new Zone(
                        Localities.nearest(e.getKey().centerLat(), e.getKey().centerLon()),
                        e.getKey().centerLat(), e.getKey().centerLon(), e.getValue().size(), avgMin(e.getValue())))
                .sorted(Comparator.comparingDouble(Zone::avgResponseMin).reversed()).toList();
    }

    /** Calls per hour for the last 24 h next to what the demand model expected. */
    public List<HourPoint> hourly() {
        LocalDateTime nowHour = clock.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime from = nowHour.minusHours(23);
        Map<LocalDateTime, Long> counts = incidents.findByCreatedAtAfter(from).stream()
                .collect(Collectors.groupingBy(i -> i.getCreatedAt().withMinute(0).withSecond(0).withNano(0),
                        Collectors.counting()));
        double scale = settings.callsPerDay() / 500.0;
        List<HourPoint> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            LocalDateTime t = from.plusHours(h);
            double expected = demand.forecast(t, 60).stream().mapToDouble(DemandModel.CellForecast::expectedCalls).sum();
            out.add(new HourPoint(t.toString(), counts.getOrDefault(t, 0L), Math.round(expected * scale * 10) / 10.0));
        }
        return out;
    }

    private static double avgMin(List<Trip> ts) {
        return Math.round(ts.stream().mapToInt(Trip::getResponseSeconds).average().orElse(0) / 6.0) / 10.0;
    }

    private static double pct(List<Trip> ts, java.util.function.Predicate<Trip> ok) {
        return ts.isEmpty() ? 0 : Math.round(1000.0 * ts.stream().filter(ok).count() / ts.size()) / 10.0;
    }
}
