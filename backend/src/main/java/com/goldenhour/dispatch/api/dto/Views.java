package com.goldenhour.dispatch.api.dto;

import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.hospital.BedType;
import com.goldenhour.dispatch.hospital.Hospital;
import com.goldenhour.dispatch.hospital.HospitalAlert;
import com.goldenhour.dispatch.hospital.TreatmentEstimate;
import com.goldenhour.dispatch.incident.Incident;
import com.goldenhour.dispatch.trip.Trip;
import com.goldenhour.dispatch.trip.TripEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Response bodies. Entities never leave the service layer; these records do. */
public final class Views {

    private Views() {
    }

    public record IncidentView(long id, LocalDateTime createdAt, double lat, double lon, String severity,
                               String patientType, String status, String source, String area, String description,
                               String surgeGroup) {
        public static IncidentView of(Incident i) {
            return new IncidentView(i.getId(), i.getCreatedAt(), i.getLat(), i.getLon(), i.getSeverity().name(),
                    i.getPatientType().name(), i.getStatus().name(), i.getSource().name(), i.getAreaName(),
                    i.getDescription(), i.getSurgeGroup());
        }
    }

    public record AssignmentView(long tripId, long ambulanceId, String callSign, double etaMinutes) {
    }

    public record CreatedIncident(IncidentView incident, AssignmentView assignment, String message) {
    }

    public record TripView(long id, long ambulanceId, Long hospitalId, String status, LocalDateTime assignedAt,
                           LocalDateTime atSceneAt, LocalDateTime atHospitalAt, LocalDateTime closedAt,
                           Integer responseSeconds, boolean greenCorridor, int signalsCleared, String hospitalReason,
                           String cancelReason, String routeVia) {
        public static TripView of(Trip t) {
            return new TripView(t.getId(), t.getAmbulanceId(), t.getHospitalId(), t.getStatus().name(),
                    t.getAssignedAt(), t.getAtSceneAt(), t.getAtHospitalAt(), t.getClosedAt(), t.getResponseSeconds(),
                    t.isGreenCorridor(), t.getSignalsCleared(), t.getHospitalReason(), t.getCancelReason(),
                    t.getRouteVia());
        }
    }

    public record EventView(long tripId, LocalDateTime at, String type, String detail) {
        public static EventView of(TripEvent e) {
            return new EventView(e.getTripId(), e.getAt(), e.getType(), e.getDetail());
        }
    }

    public record IncidentDetail(IncidentView incident, List<TripView> trips, List<EventView> timeline) {
    }

    public record AmbulanceView(long id, String callSign, String kind, String status, double lat, double lon,
                                Long currentTripId, Integer targetPostId, LocalDateTime lastPingAt) {
        public static AmbulanceView of(Ambulance a) {
            return new AmbulanceView(a.getId(), a.getCallSign(), a.getKind(), a.getStatus().name(), a.getLat(),
                    a.getLon(), a.getCurrentTripId(), a.getTargetPostId(), a.getLastPingAt());
        }
    }

    public record BedView(int free, int total) {
    }

    public record HospitalView(long id, String name, double lat, double lon, Set<String> capabilities,
                               Map<String, BedView> beds, int erQueue, int erBays, boolean specialists24x7) {
        public static HospitalView of(Hospital h) {
            Map<String, BedView> beds = new TreeMap<>();
            for (BedType t : BedType.values()) {
                beds.put(t.name(), new BedView(h.freeBeds(t), h.totalBeds(t)));
            }
            return new HospitalView(h.getId(), h.getName(), h.getLat(), h.getLon(),
                    h.capabilitySet().stream().map(Enum::name).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)),
                    beds, h.erQueue(), h.erBays(), h.isSpecialists24x7());
        }
    }

    public record AlertView(long id, long hospitalId, long tripId, LocalDateTime createdAt, LocalDateTime etaAt,
                            String severity, String patientType, String bedType, String message) {
        public static AlertView of(HospitalAlert a) {
            return new AlertView(a.getId(), a.getHospitalId(), a.getTripId(), a.getCreatedAt(), a.getEtaAt(),
                    a.getSeverity().name(), a.getPatientType().name(), a.getBedType().name(), a.getMessage());
        }
    }

    public record RankedHospital(long id, String name, boolean feasible, String note, double travelMin,
                                 double erWaitMin, double specialistDelayMin, double timeToTreatmentMin) {
        public static RankedHospital of(TreatmentEstimate e) {
            return new RankedHospital(e.hospital().id(), e.hospital().name(), e.feasible(), e.reason(),
                    r(e.travelSeconds()), r(e.erWaitSeconds()), r(e.specialistDelaySeconds()),
                    e.feasible() ? r(e.timeToTreatmentSeconds()) : -1);
        }

        private static double r(double seconds) {
            return Math.round(seconds / 6.0) / 10.0;
        }
    }
}
