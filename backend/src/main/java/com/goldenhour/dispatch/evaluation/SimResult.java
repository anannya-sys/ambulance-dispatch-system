package com.goldenhour.dispatch.evaluation;

/**
 * Outcome metrics of one simulation run. Times are in minutes.
 *
 * @param criticalWithin15Pct   critical calls reached within 15 minutes of the call
 * @param goldenHourPct         critical patients whose treatment started within 60 minutes of the call
 * @param turnAwaysOrTransfers  patients refused (no bed) or transferred (wrong facility) at least once
 */
public record SimResult(String config, long seed, int calls,
                        double meanResponseMin, double meanCriticalResponseMin, double p90CriticalResponseMin,
                        double criticalWithin15Pct, double meanCriticalTreatmentMin, double goldenHourPct,
                        int turnAwaysOrTransfers, double meanCriticalQueueMin, double utilisationPct) {
}
