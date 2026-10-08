package com.goldenhour.dispatch.evaluation;

import com.goldenhour.dispatch.forecast.CallPattern;
import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.forecast.PrePositioningPlanner;
import com.goldenhour.dispatch.forecast.StandbyPost;
import com.goldenhour.dispatch.forecast.SyntheticCall;
import com.goldenhour.dispatch.routing.CityModel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Runs the baseline, the single-feature ablations and full GoldenHour on the same synthetic calls for
 * several random seeds, and averages the results.
 *
 * <p>Per seed: 8 weeks of call history train the {@link DemandModel}; the next {@code days} days are the
 * test period (calls the model has never seen).</p>
 */
public final class EvaluationRunner {

    public static final LocalDateTime TEST_START = LocalDateTime.of(2026, 3, 2, 0, 0); // a Monday
    static final int HISTORY_WEEKS = 8;
    public static final int BASE_STATIONS = 25;

    private final CityModel city;

    public EvaluationRunner(CityModel city) {
        this.city = city;
    }

    public EvaluationReport run(int seeds, int days, double callsPerDay, int fleetSize, boolean ablations) {
        CallPattern pattern = new CallPattern(city.graph(), callsPerDay);
        List<SimConfig> configs = new ArrayList<>();
        configs.add(SimConfig.BASELINE);
        if (ablations) {
            configs.addAll(SimConfig.ablations());
        }
        configs.add(SimConfig.GOLDENHOUR);

        record Job(long seed, SimConfig config) {
        }
        List<Job> jobs = new ArrayList<>();
        for (long s = 1; s <= seeds; s++) {
            for (SimConfig c : configs) {
                jobs.add(new Job(s, c));
            }
        }
        // Data per seed is generated once and shared by every config of that seed.
        java.util.Map<Long, SeedData> data = new java.util.concurrent.ConcurrentHashMap<>();
        List<SimResult> runs = jobs.parallelStream().map(j -> {
            SeedData d = data.computeIfAbsent(j.seed(), s -> prepare(pattern, s, days));
            return new DispatchSimulation(city, j.config(), d.calls, d.model, d.homes, fleetSize, TEST_START, j.seed())
                    .run();
        }).collect(Collectors.toList());

        List<SimResult> averages = configs.stream().map(c -> average(c.name(),
                runs.stream().filter(r -> r.config().equals(c.name())).toList())).toList();
        SimResult base = averages.get(0);
        SimResult gh = averages.get(averages.size() - 1);
        double respPct = 100 * (base.meanCriticalResponseMin() - gh.meanCriticalResponseMin()) / base.meanCriticalResponseMin();
        double goldenPts = gh.goldenHourPct() - base.goldenHourPct();
        double turnAwayPct = base.turnAwaysOrTransfers() == 0 ? 0
                : 100.0 * (base.turnAwaysOrTransfers() - gh.turnAwaysOrTransfers()) / base.turnAwaysOrTransfers();
        String headline = String.format(Locale.ROOT,
                "Critical response %.1f -> %.1f min (-%.1f%%); golden-hour treatment %.1f%% -> %.1f%% (+%.1f pts); "
                        + "hospital turn-aways/transfers -%.0f%%",
                base.meanCriticalResponseMin(), gh.meanCriticalResponseMin(), respPct,
                base.goldenHourPct(), gh.goldenHourPct(), goldenPts, turnAwayPct);
        runs.sort(Comparator.comparingLong(SimResult::seed).thenComparing(r -> configs.indexOf(
                configs.stream().filter(c -> c.name().equals(r.config())).findFirst().orElseThrow())));
        return new EvaluationReport(headline, seeds, days, callsPerDay, fleetSize, averages, runs,
                respPct, goldenPts, turnAwayPct, LocalDateTime.now().withNano(0).toString());
    }

    private record SeedData(List<SyntheticCall> calls, DemandModel model, List<StandbyPost> homes) {
    }

    private SeedData prepare(CallPattern pattern, long seed, int days) {
        List<SyntheticCall> history = pattern.generate(TEST_START.minusWeeks(HISTORY_WEEKS), TEST_START, seed * 7919);
        DemandModel model = DemandModel.train(history);
        List<SyntheticCall> calls = pattern.generate(TEST_START, TEST_START.plusDays(days), seed * 7919 + 1);
        return new SeedData(calls, model, staticStations(model));
    }

    /**
     * The baseline's fixed stations: the best static placement for <i>average</i> weekly demand (p-median
     * over the whole week). This makes the baseline strong; GoldenHour's edge comes only from moving with
     * the time of day.
     */
    List<StandbyPost> staticStations(DemandModel model) {
        return PrePositioningPlanner.staticStations(model, BASE_STATIONS, city, TEST_START.minusWeeks(1));
    }

    private static SimResult average(String name, List<SimResult> rs) {
        return new SimResult(name, 0, (int) avg(rs, SimResult::calls),
                avg(rs, SimResult::meanResponseMin), avg(rs, SimResult::meanCriticalResponseMin),
                avg(rs, SimResult::p90CriticalResponseMin), avg(rs, SimResult::criticalWithin15Pct),
                avg(rs, SimResult::meanCriticalTreatmentMin), avg(rs, SimResult::goldenHourPct),
                (int) Math.round(avg(rs, SimResult::turnAwaysOrTransfers)), avg(rs, SimResult::meanCriticalQueueMin),
                avg(rs, SimResult::utilisationPct));
    }

    private static double avg(List<SimResult> rs, ToDoubleFunction<SimResult> f) {
        return rs.stream().mapToDouble(f).average().orElse(0);
    }

    /** Plain-text table for the README and docs/simulation-results.txt. */
    public static String format(EvaluationReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "GoldenHour evaluation: %d seeds x %d days, %.0f calls/day, %d ambulances%n",
                r.seeds(), r.days(), r.callsPerDay(), r.fleetSize()));
        sb.append(String.format(Locale.ROOT, "%-56s %7s %8s %8s %8s %9s %8s %8s %7s%n", "Configuration",
                "Resp", "CritResp", "CritP90", "Crit<15", "CritTreat", "Golden", "TurnAway", "Util"));
        for (SimResult a : r.averages()) {
            sb.append(String.format(Locale.ROOT, "%-56s %6.1fm %7.1fm %7.1fm %7.1f%% %8.1fm %7.1f%% %8d %6.1f%%%n",
                    a.config(), a.meanResponseMin(), a.meanCriticalResponseMin(), a.p90CriticalResponseMin(),
                    a.criticalWithin15Pct(), a.meanCriticalTreatmentMin(), a.goldenHourPct(),
                    a.turnAwaysOrTransfers(), a.utilisationPct()));
        }
        sb.append(System.lineSeparator()).append("Per seed (baseline vs GoldenHour, critical response / golden hour):")
                .append(System.lineSeparator());
        for (long s = 1; s <= r.seeds(); s++) {
            final long seed = s;
            SimResult b = r.runs().stream().filter(x -> x.seed() == seed && x.config().equals(SimConfig.BASELINE.name())).findFirst().orElseThrow();
            SimResult g = r.runs().stream().filter(x -> x.seed() == seed && x.config().equals(SimConfig.GOLDENHOUR.name())).findFirst().orElseThrow();
            sb.append(String.format(Locale.ROOT, "  seed %d: %.1f -> %.1f min | %.1f%% -> %.1f%%%n", seed,
                    b.meanCriticalResponseMin(), g.meanCriticalResponseMin(), b.goldenHourPct(), g.goldenHourPct()));
        }
        sb.append(System.lineSeparator()).append("Headline: ").append(r.headline()).append(System.lineSeparator());
        return sb.toString();
    }
}
