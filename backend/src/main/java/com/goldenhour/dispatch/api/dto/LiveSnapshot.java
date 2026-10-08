package com.goldenhour.dispatch.api.dto;

import java.util.List;

/** Everything the live map needs, pushed once per second on {@code /topic/live}. */
public record LiveSnapshot(String clock, double speed, Kpis kpis, List<FleetUnit> fleet, List<ActiveTrip> trips,
                           List<QueuedCall> queue) {

    public record Kpis(int activeTrips, int queued, int available, int busy, int outOfService, double utilisationPct) {
    }

    public record FleetUnit(long id, String callSign, String kind, String status, double lat, double lon,
                            double heading, Long tripId, Integer etaSeconds, Integer targetPostId) {
    }

    /** A corridor junction: CLEARED (passed), GREEN (held green now), PENDING (will turn green). */
    public record CorridorSignal(double lat, double lon, String state) {
    }

    public record ActiveTrip(long tripId, long incidentId, long ambulanceId, String callSign, String severity,
                             String patientType, String area, String phase, double incidentLat, double incidentLon,
                             Long hospitalId, String hospitalName, String hospitalReason, Integer etaSeconds,
                             int sinceCallSeconds, boolean corridor, List<double[]> route,
                             List<CorridorSignal> signals, Double treatmentEtaMinutes) {
    }

    public record QueuedCall(long id, String severity, String patientType, String area, double lat, double lon,
                             int waitingSeconds, double priority) {
    }
}
