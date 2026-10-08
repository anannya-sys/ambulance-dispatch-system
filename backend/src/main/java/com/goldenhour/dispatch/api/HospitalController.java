package com.goldenhour.dispatch.api;

import com.goldenhour.dispatch.api.dto.Views;
import com.goldenhour.dispatch.hospital.HospitalAlertRepository;
import com.goldenhour.dispatch.hospital.HospitalRepository;
import com.goldenhour.dispatch.hospital.HospitalService;
import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.routing.CityModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
@Tag(name = "Hospitals", description = "Bed board, pre-alerts and time-to-treatment ranking")
public class HospitalController {

    private final HospitalRepository hospitals;
    private final HospitalAlertRepository alerts;
    private final HospitalService service;
    private final CityModel city;
    private final SimClock clock;

    public HospitalController(HospitalRepository hospitals, HospitalAlertRepository alerts, HospitalService service,
                              CityModel city, SimClock clock) {
        this.hospitals = hospitals;
        this.alerts = alerts;
        this.service = service;
        this.city = city;
        this.clock = clock;
    }

    @GetMapping
    public List<Views.HospitalView> list() {
        return hospitals.findAll().stream().sorted(Comparator.comparing(h -> h.getName()))
                .map(Views.HospitalView::of).toList();
    }

    @GetMapping("/alerts")
    @Operation(summary = "Latest ER pre-alerts")
    public List<Views.AlertView> alerts() {
        return alerts.findTop50ByOrderByCreatedAtDesc().stream().map(Views.AlertView::of).toList();
    }

    @GetMapping("/recommendation")
    @Operation(summary = "Rank all hospitals by time to treatment for a patient at this location")
    public List<Views.RankedHospital> recommend(@RequestParam double lat, @RequestParam double lon,
                                                @RequestParam PatientType patientType, @RequestParam Severity severity) {
        int node = city.graph().nearestNode(lat, lon);
        return service.rank(node, patientType, severity, clock.now()).stream().map(Views.RankedHospital::of).toList();
    }
}
