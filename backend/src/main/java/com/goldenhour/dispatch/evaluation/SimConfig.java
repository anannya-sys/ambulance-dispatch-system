package com.goldenhour.dispatch.evaluation;

import java.util.List;

/**
 * Which GoldenHour features are switched on for one simulation run.
 * Running the baseline, each feature alone, and everything together (an "ablation study") shows how
 * much each USP is worth on its own.
 */
public record SimConfig(String name, boolean prePositioning, boolean priorityQueue,
                        boolean timeToTreatment, boolean greenCorridor) {

    public static final SimConfig BASELINE =
            new SimConfig("Baseline (nearest ambulance, FIFO, nearest capable hospital)", false, false, false, false);
    public static final SimConfig GOLDENHOUR = new SimConfig("GoldenHour (all features)", true, true, true, true);

    public static List<SimConfig> ablations() {
        return List.of(
                new SimConfig("+ Predictive pre-positioning only", true, false, false, false),
                new SimConfig("+ Severity priority queue only", false, true, false, false),
                new SimConfig("+ Time-to-treatment routing only", false, false, true, false),
                new SimConfig("+ Green corridor only", false, false, false, true));
    }
}
