package com.goldenhour.dispatch.forecast;

import com.goldenhour.dispatch.geo.GridCell;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Learns where and when emergencies happen from past calls, and forecasts the next hour.
 *
 * <p><b>Model:</b> for each ~1 km grid cell and each of the 168 hours of the week we estimate a Poisson
 * call rate. Raw counts per (cell, hour-of-week) are noisy (a few weeks of history = a handful of calls
 * per bucket), so we shrink them towards a smoother estimate that pools all 7 days for the same hour of
 * day (an empirical-Bayes style blend):</p>
 *
 * <pre>
 *   rate(cell, how) = (count(cell, how) + K * pooledRate(cell, hourOfDay)) / (weeks + K)
 * </pre>
 *
 * <p>With little data the pooled estimate dominates; with lots of data the specific hour-of-week wins.
 * This is simple enough to explain on a whiteboard, fast enough to retrain every few minutes, and
 * captures both "rush hour on weekdays" and "Sunday evening at the beach".</p>
 */
public final class DemandModel {

    /** Pseudo-weeks of prior; higher = smoother forecasts. */
    static final double K = 2.0;

    private final Map<GridCell, CellStats> cells = new HashMap<>();
    private double weeks = 1;

    private static final class CellStats {
        final double[] howCounts = new double[168];
        final double[] hodCounts = new double[24];
        double sumLat;
        double sumLon;
        long n;
    }

    /** Forecast for one cell. {@code lat/lon} is the centre of mass of its past calls. */
    public record CellForecast(GridCell cell, double lat, double lon, double expectedCalls) {
    }

    public static DemandModel train(List<SyntheticCall> history) {
        DemandModel m = new DemandModel();
        if (history.isEmpty()) {
            return m;
        }
        LocalDateTime first = history.get(0).time();
        LocalDateTime last = history.get(history.size() - 1).time();
        m.weeks = Math.max(1.0, ChronoUnit.HOURS.between(first, last) / 168.0);
        for (SyntheticCall c : history) {
            m.add(c.time(), c.lat(), c.lon());
        }
        return m;
    }

    /** Online update: the live system feeds every new call back into the model. */
    public synchronized void add(LocalDateTime time, double lat, double lon) {
        CellStats s = cells.computeIfAbsent(GridCell.of(lat, lon), k -> new CellStats());
        s.howCounts[hourOfWeek(time)]++;
        s.hodCounts[time.getHour()]++;
        s.sumLat += lat;
        s.sumLon += lon;
        s.n++;
    }

    public double weeksOfHistory() {
        return weeks;
    }

    /** Expected calls per cell during [{@code from}, {@code from + minutes}). */
    public synchronized List<CellForecast> forecast(LocalDateTime from, int minutes) {
        int hours = Math.max(1, (int) Math.ceil(minutes / 60.0));
        double fraction = minutes / (hours * 60.0);
        return cells.entrySet().stream().map(e -> {
            CellStats s = e.getValue();
            double expected = 0;
            for (int h = 0; h < hours; h++) {
                LocalDateTime t = from.plusHours(h);
                expected += rate(s, hourOfWeek(t), t.getHour());
            }
            return new CellForecast(e.getKey(), s.sumLat / s.n, s.sumLon / s.n, expected * fraction);
        }).filter(f -> f.expectedCalls() > 0).toList();
    }

    private double rate(CellStats s, int how, int hod) {
        // pooled: calls at this hour of day on any weekday, per hour
        double pooledPerHour = s.hodCounts[hod] / (7 * weeks);
        // K pseudo-weeks of the pooled rate are added to the observed count for this exact hour of week
        return (s.howCounts[how] + K * pooledPerHour) / (weeks + K);
    }

    static int hourOfWeek(LocalDateTime t) {
        return (t.getDayOfWeek().getValue() - 1) * 24 + t.getHour();
    }
}
