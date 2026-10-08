package com.goldenhour.dispatch.evaluation;

import java.util.List;

/**
 * Results of an evaluation: averaged rows per configuration plus the raw per-seed runs.
 *
 * @param headline one-line summary, e.g. "critical response time -23.4% (9.8 -> 7.5 min)"
 */
public record EvaluationReport(String headline, int seeds, int days, double callsPerDay, int fleetSize,
                               List<SimResult> averages, List<SimResult> runs, double criticalResponseImprovementPct,
                               double goldenHourGainPts, double turnAwayReductionPct, String generatedAt) {
}
