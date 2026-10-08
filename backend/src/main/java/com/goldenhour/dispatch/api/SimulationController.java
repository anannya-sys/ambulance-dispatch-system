package com.goldenhour.dispatch.api;

import com.goldenhour.dispatch.api.dto.LiveSnapshot;
import com.goldenhour.dispatch.api.dto.Requests;
import com.goldenhour.dispatch.forecast.RebalanceService;
import com.goldenhour.dispatch.live.LiveSettings;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.realtime.LiveBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sim")
@Tag(name = "Simulation controls", description = "Speed, call volume and feature switches of the live demo")
public class SimulationController {

    private final LiveSettings settings;
    private final SimClock clock;
    private final RebalanceService rebalance;
    private final LiveBroadcaster broadcaster;

    public SimulationController(LiveSettings settings, SimClock clock, RebalanceService rebalance,
                                LiveBroadcaster broadcaster) {
        this.settings = settings;
        this.clock = clock;
        this.rebalance = rebalance;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of("speed", clock.speed(), "autoCalls", settings.autoCalls(), "callsPerDay", settings.callsPerDay(),
                "prePositioning", settings.prePositioning(), "greenCorridor", settings.greenCorridor(),
                "clock", clock.now().withNano(0).toString());
    }

    @PutMapping("/settings")
    public Map<String, Object> update(@Valid @RequestBody Requests.Settings req) {
        if (req.speed() != null) {
            clock.setSpeed(req.speed());
        }
        if (req.autoCalls() != null) {
            settings.setAutoCalls(req.autoCalls());
        }
        if (req.callsPerDay() != null) {
            settings.setCallsPerDay(req.callsPerDay());
        }
        if (req.prePositioning() != null) {
            settings.setPrePositioning(req.prePositioning());
        }
        if (req.greenCorridor() != null) {
            settings.setGreenCorridor(req.greenCorridor());
        }
        return settings();
    }

    @GetMapping("/snapshot")
    @Operation(summary = "Same payload as the /topic/live WebSocket feed")
    public LiveSnapshot snapshot() {
        return broadcaster.latest();
    }

    @GetMapping("/forecast")
    @Operation(summary = "Latest demand forecast and the standby posts chosen for idle ambulances")
    public RebalanceService.Plan forecast() {
        RebalanceService.Plan p = rebalance.lastPlan();
        return p != null ? p : rebalance.rebalance(clock.now());
    }

    @PostMapping("/rebalance")
    @Operation(summary = "Re-run predictive pre-positioning now")
    public RebalanceService.Plan rebalanceNow() {
        return rebalance.rebalance(clock.now());
    }
}
