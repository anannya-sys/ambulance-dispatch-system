package com.goldenhour.dispatch.api;

import com.goldenhour.dispatch.api.dto.Requests;
import com.goldenhour.dispatch.api.dto.Views;
import com.goldenhour.dispatch.incident.Incident;
import com.goldenhour.dispatch.incident.IncidentSource;
import com.goldenhour.dispatch.incident.IncidentService;
import com.goldenhour.dispatch.trip.TripEventRepository;
import com.goldenhour.dispatch.trip.TripRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Emergency intake and the dispatch queue")
public class IncidentController {

    private final IncidentService incidents;
    private final TripRepository trips;
    private final TripEventRepository events;

    public IncidentController(IncidentService incidents, TripRepository trips, TripEventRepository events) {
        this.incidents = incidents;
        this.trips = trips;
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report an emergency; the nearest free ambulance is assigned immediately if there is one")
    public Views.CreatedIncident create(@Valid @RequestBody Requests.CreateIncident req) {
        IncidentService.Created c = incidents.create(req.lat(), req.lon(), req.severity(), req.patientType(),
                IncidentSource.MANUAL, req.description());
        Views.AssignmentView a = c.assignment().map(x -> new Views.AssignmentView(x.trip().getId(),
                x.ambulance().getId(), x.ambulance().getCallSign(), Math.round(x.etaSeconds() / 6.0) / 10.0)).orElse(null);
        String msg = a == null ? "All ambulances busy: call queued by priority"
                : String.format(Locale.ROOT, "%s assigned, about %.0f min away", a.callSign(), a.etaMinutes());
        return new Views.CreatedIncident(Views.IncidentView.of(c.incident()), a, msg);
    }

    @PostMapping("/surge")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Mass-casualty incident: several patients at one place, several ambulances at once")
    public Map<String, Object> surge(@Valid @RequestBody Requests.Surge req) {
        List<Incident> created = incidents.createSurge(req.lat(), req.lon(), req.casualties(),
                req.description() == null ? "Mass-casualty incident" : req.description());
        return Map.of("surgeGroup", created.get(0).getSurgeGroup(), "patients", created.size(),
                "incidents", created.stream().map(Views.IncidentView::of).toList());
    }

    @GetMapping("/queue")
    @Operation(summary = "Calls waiting for an ambulance, highest priority first")
    public List<Views.IncidentView> queue() {
        return incidents.queue().stream().map(Views.IncidentView::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One incident with all its trips and the full event timeline")
    public Views.IncidentDetail get(@PathVariable long id) {
        Incident i = incidents.get(id);
        var ts = trips.findByIncidentIdOrderByAssignedAtAsc(id);
        return new Views.IncidentDetail(Views.IncidentView.of(i), ts.stream().map(Views.TripView::of).toList(),
                ts.stream().flatMap(t -> events.findByTripIdOrderByAtAsc(t.getId()).stream())
                        .map(Views.EventView::of).toList());
    }
}
