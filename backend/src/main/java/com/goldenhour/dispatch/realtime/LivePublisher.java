package com.goldenhour.dispatch.realtime;

import com.goldenhour.dispatch.hospital.HospitalAlert;
import com.goldenhour.dispatch.messaging.DispatchEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Pushes messages to dashboard subscribers over STOMP. */
@Component
public class LivePublisher {

    private final SimpMessagingTemplate ws;

    public LivePublisher(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    public void snapshot(Object snapshot) {
        ws.convertAndSend("/topic/live", snapshot);
    }

    public void event(DispatchEvent event) {
        ws.convertAndSend("/topic/events", event);
    }

    public void alert(HospitalAlert a, String hospitalName) {
        ws.convertAndSend("/topic/alerts", Map.of(
                "id", a.getId(), "hospitalId", a.getHospitalId(), "hospitalName", hospitalName,
                "tripId", a.getTripId(), "severity", a.getSeverity(), "patientType", a.getPatientType(),
                "message", a.getMessage(), "etaAt", a.getEtaAt().toString(), "createdAt", a.getCreatedAt().toString()));
    }

    public void positioning(Object plan) {
        ws.convertAndSend("/topic/positioning", plan);
    }
}
