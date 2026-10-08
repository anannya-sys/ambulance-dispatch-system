package com.goldenhour.dispatch.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldenhour.dispatch.analytics.AnalyticsService;
import com.goldenhour.dispatch.evaluation.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Analytics", description = "Live KPIs and the baseline-vs-GoldenHour evaluation")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final EvaluationService evaluation;

    public AnalyticsController(AnalyticsService analytics, EvaluationService evaluation) {
        this.analytics = analytics;
        this.evaluation = evaluation;
    }

    @GetMapping("/analytics/summary")
    @Operation(summary = "KPIs over the last 24 simulated hours")
    public AnalyticsService.Summary summary() {
        return analytics.summary();
    }

    @GetMapping("/analytics/zones")
    @Operation(summary = "Average response time per ~1 km zone (slow-zone heatmap)")
    public List<AnalyticsService.Zone> zones() {
        return analytics.zones();
    }

    @GetMapping("/analytics/hourly")
    @Operation(summary = "Calls per hour vs the demand model's forecast")
    public List<AnalyticsService.HourPoint> hourly() {
        return analytics.hourly();
    }

    @GetMapping("/evaluation")
    @Operation(summary = "Published evaluation: 5 seeds x 14 days, normal load and surge week")
    public JsonNode published() throws IOException {
        return evaluation.published();
    }

    @PostMapping("/evaluation/run")
    @Operation(summary = "Run a smaller evaluation now (takes ~10-60 s)")
    public EvaluationService.RunStatus run(@RequestParam(defaultValue = "1") @Min(1) @Max(3) int seeds,
                                           @RequestParam(defaultValue = "3") @Min(1) @Max(7) int days,
                                           @RequestParam(defaultValue = "500") @Min(100) @Max(900) double callsPerDay) {
        return evaluation.start(seeds, days, callsPerDay);
    }

    @GetMapping("/evaluation/latest")
    public EvaluationService.RunStatus latest() {
        return evaluation.latest();
    }
}
