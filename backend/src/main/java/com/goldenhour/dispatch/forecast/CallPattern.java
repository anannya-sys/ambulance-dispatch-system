package com.goldenhour.dispatch.forecast;

import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.routing.RoadGraph;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The "ground truth" synthetic city: where and when emergencies happen in Mumbai.
 *
 * <p>We don't have real 108 call records, so we model the patterns that are widely reported:
 * road accidents on arterial junctions at rush hour, cardiac events in office districts on weekdays,
 * elderly cardiac/stroke calls in the early morning, late-night accidents near nightlife on weekends,
 * crowds at the sea-front on weekend evenings, and a background of calls everywhere.
 * The {@link DemandModel} never sees these rules: it only sees the generated calls and has to learn
 * the pattern from them, which is what a real deployment would do with historical data.</p>
 */
public final class CallPattern {

    /** Daily-rhythm templates, 24 hourly weights each. */
    enum Profile {
        COMMUTE(new double[]{.3, .2, .2, .2, .3, .6, 1.2, 2.0, 3.0, 3.2, 2.4, 1.5, 1.3, 1.3, 1.4, 1.6, 2.0, 2.8, 3.2, 3.0, 2.2, 1.4, .9, .5},
                1.25, .65, new double[]{.06, .02, .62, .04, .02, .24}),
        OFFICE(new double[]{.1, .1, .1, .1, .1, .1, .3, .8, 1.6, 2.4, 2.8, 2.8, 2.6, 2.6, 2.6, 2.6, 2.4, 2.0, 1.2, .6, .3, .2, .1, .1},
                1.3, .3, new double[]{.34, .10, .10, .12, .02, .32}),
        RESIDENTIAL(new double[]{.8, .6, .5, .5, .6, .9, 1.4, 1.6, 1.4, 1.1, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.1, 1.3, 1.6, 1.9, 2.0, 1.8, 1.4, 1.1},
                1.0, 1.1, new double[]{.12, .06, .12, .22, .20, .28}),
        ELDERLY(new double[]{.6, .5, .5, .8, 1.6, 2.6, 3.0, 2.8, 2.2, 1.6, 1.2, 1.0, .9, .9, .9, .9, 1.0, 1.1, 1.2, 1.2, 1.1, 1.0, .8, .7},
                1.0, 1.0, new double[]{.40, .26, .06, .14, .00, .14}),
        NIGHTLIFE(new double[]{2.6, 2.4, 1.8, 1.0, .5, .3, .2, .2, .2, .2, .2, .3, .3, .3, .3, .3, .4, .5, .7, 1.0, 1.4, 1.8, 2.4, 2.8},
                .6, 2.2, new double[]{.06, .03, .55, .06, .00, .30}),
        LEISURE(new double[]{.4, .2, .1, .1, .1, .3, .7, .9, .6, .5, .5, .5, .6, .7, .9, 1.3, 2.0, 2.8, 3.2, 3.0, 2.4, 1.6, 1.0, .6},
                .5, 2.4, new double[]{.12, .04, .30, .14, .02, .38}),
        BACKGROUND(new double[]{.5, .4, .4, .4, .5, .7, 1.0, 1.2, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.3, 1.2, 1.0, .8, .6},
                1.0, 1.0, new double[]{.16, .08, .20, .16, .10, .30});

        final double[] hourly;
        final double weekday;
        final double weekend;
        final double[] patientMix;

        Profile(double[] hourly, double weekday, double weekend, double[] patientMix) {
            this.hourly = hourly;
            this.weekday = weekday;
            this.weekend = weekend;
            this.patientMix = patientMix;
        }

        double weight(LocalDateTime t) {
            boolean we = t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY;
            return hourly[t.getHour()] * (we ? weekend : weekday);
        }
    }

    record Hotspot(String name, double lat, double lon, double radiusMeters, double weight, Profile profile) {
    }

    static final List<Hotspot> HOTSPOTS = List.of(
            // arterial junctions: rush-hour accidents
            new Hotspot("WEH Andheri flyover", 19.1170, 72.8530, 900, 1.2, Profile.COMMUTE),
            new Hotspot("Sion Circle", 19.0430, 72.8640, 800, 1.1, Profile.COMMUTE),
            new Hotspot("Dadar TT", 19.0180, 72.8460, 700, 1.0, Profile.COMMUTE),
            new Hotspot("Saki Naka junction", 19.1030, 72.8880, 800, 1.0, Profile.COMMUTE),
            new Hotspot("EEH Vikhroli", 19.1050, 72.9250, 1000, .9, Profile.COMMUTE),
            new Hotspot("Kalanagar junction", 19.0590, 72.8460, 700, .9, Profile.COMMUTE),
            new Hotspot("SCLR Kurla", 19.0700, 72.8900, 900, .8, Profile.COMMUTE),
            new Hotspot("Haji Ali", 18.9820, 72.8160, 600, .7, Profile.COMMUTE),
            new Hotspot("WEH Goregaon", 19.1600, 72.8560, 1000, .8, Profile.COMMUTE),
            new Hotspot("Mulund check naka", 19.1720, 72.9500, 900, .6, Profile.COMMUTE),
            // business districts: weekday cardiac events
            new Hotspot("BKC", 19.0660, 72.8680, 900, 1.0, Profile.OFFICE),
            new Hotspot("Lower Parel offices", 18.9960, 72.8300, 800, .9, Profile.OFFICE),
            new Hotspot("Nariman Point / Fort", 18.9300, 72.8260, 1000, .9, Profile.OFFICE),
            new Hotspot("Andheri East MIDC", 19.1200, 72.8700, 1100, .9, Profile.OFFICE),
            new Hotspot("Powai Hiranandani", 19.1180, 72.9080, 900, .6, Profile.OFFICE),
            // dense residential
            new Hotspot("Dharavi", 19.0410, 72.8540, 900, 1.1, Profile.RESIDENTIAL),
            new Hotspot("Govandi-Mankhurd", 19.0550, 72.9200, 1200, 1.0, Profile.RESIDENTIAL),
            new Hotspot("Kurla West", 19.0720, 72.8790, 900, .9, Profile.RESIDENTIAL),
            new Hotspot("Malvani", 19.1850, 72.8300, 1100, .9, Profile.RESIDENTIAL),
            new Hotspot("Bhandup", 19.1450, 72.9380, 1100, .8, Profile.RESIDENTIAL),
            new Hotspot("Byculla", 18.9790, 72.8330, 800, .7, Profile.RESIDENTIAL),
            new Hotspot("Jogeshwari", 19.1400, 72.8550, 900, .7, Profile.RESIDENTIAL),
            // older neighbourhoods: early-morning cardiac and stroke
            new Hotspot("Shivaji Park", 19.0270, 72.8380, 800, .7, Profile.ELDERLY),
            new Hotspot("Matunga", 19.0270, 72.8550, 700, .6, Profile.ELDERLY),
            new Hotspot("Vile Parle East", 19.0990, 72.8490, 800, .6, Profile.ELDERLY),
            new Hotspot("Girgaon", 18.9550, 72.8150, 700, .5, Profile.ELDERLY),
            new Hotspot("Colaba", 18.9100, 72.8160, 800, .4, Profile.ELDERLY),
            // late-night
            new Hotspot("Linking Road Bandra", 19.0640, 72.8340, 700, .6, Profile.NIGHTLIFE),
            new Hotspot("Lokhandwala", 19.1430, 72.8250, 800, .6, Profile.NIGHTLIFE),
            new Hotspot("Kamala Mills", 18.9990, 72.8270, 600, .5, Profile.NIGHTLIFE),
            // sea-front crowds
            new Hotspot("Juhu beach", 19.0990, 72.8270, 900, .5, Profile.LEISURE),
            new Hotspot("Girgaon Chowpatty", 18.9540, 72.8140, 600, .4, Profile.LEISURE),
            new Hotspot("Marine Drive", 18.9440, 72.8230, 800, .4, Profile.LEISURE),
            new Hotspot("Bandstand", 19.0480, 72.8230, 600, .3, Profile.LEISURE));

    /** Share of calls that come from the background (anywhere in the city) instead of a hotspot. */
    static final double BACKGROUND_SHARE = 0.40;
    /** Hotspots east of this longitude are across the creek and outside our hospitals' area. */
    static final double MAX_LON = 72.975;

    private final RoadGraph graph;
    private final double callsPerDay;
    private final double hotspotScale;
    private final double backgroundScale;
    private final int[] backgroundNodes;

    public CallPattern(RoadGraph graph, double callsPerDay) {
        this.graph = graph;
        this.callsPerDay = callsPerDay;
        // Normalise so that the expected number of calls over an average week is callsPerDay * 7.
        double hotspotWeek = 0;
        double backgroundWeek = 0;
        LocalDateTime monday = LocalDateTime.of(2026, 1, 5, 0, 0);
        for (int h = 0; h < 168; h++) {
            LocalDateTime t = monday.plusHours(h);
            for (Hotspot s : HOTSPOTS) {
                hotspotWeek += s.weight() * s.profile().weight(t);
            }
            backgroundWeek += Profile.BACKGROUND.weight(t);
        }
        this.hotspotScale = callsPerDay * 7 * (1 - BACKGROUND_SHARE) / hotspotWeek;
        this.backgroundScale = callsPerDay * 7 * BACKGROUND_SHARE / backgroundWeek;
        List<Integer> nodes = new ArrayList<>();
        for (int n = 0; n < graph.nodeCount(); n++) {
            if (graph.lon(n) <= MAX_LON) {
                nodes.add(n);
            }
        }
        this.backgroundNodes = nodes.stream().mapToInt(Integer::intValue).toArray();
    }

    public double callsPerDay() {
        return callsPerDay;
    }

    /** Expected calls in the hour starting at {@code hourStart}, summed over the city. */
    public double expectedCallsInHour(LocalDateTime hourStart) {
        double rate = backgroundScale * Profile.BACKGROUND.weight(hourStart);
        for (Hotspot s : HOTSPOTS) {
            rate += hotspotScale * s.weight() * s.profile().weight(hourStart);
        }
        return rate;
    }

    /** All calls in [from, to), sorted by time. Same seed, same calls. */
    public List<SyntheticCall> generate(LocalDateTime from, LocalDateTime to, long seed) {
        Random rnd = new Random(seed);
        List<SyntheticCall> calls = new ArrayList<>();
        for (LocalDateTime hour = from; hour.isBefore(to); hour = hour.plusHours(1)) {
            for (Hotspot s : HOTSPOTS) {
                int n = poisson(rnd, hotspotScale * s.weight() * s.profile().weight(hour));
                for (int i = 0; i < n; i++) {
                    calls.add(hotspotCall(rnd, hour, s));
                }
            }
            int n = poisson(rnd, backgroundScale * Profile.BACKGROUND.weight(hour));
            for (int i = 0; i < n; i++) {
                int node = backgroundNodes[rnd.nextInt(backgroundNodes.length)];
                calls.add(build(rnd, hour, graph.lat(node), graph.lon(node), node, Profile.BACKGROUND, "background"));
            }
        }
        calls.removeIf(c -> !c.time().isBefore(to));
        calls.sort(Comparator.comparing(SyntheticCall::time));
        return calls;
    }

    /** One call at a random spot (weighted like the real pattern) at time {@code t}; used by the live demo. */
    public SyntheticCall sampleOne(Random rnd, LocalDateTime t) {
        double total = expectedCallsInHour(t.withMinute(0).withSecond(0).withNano(0));
        double u = rnd.nextDouble() * total;
        LocalDateTime hour = t.withMinute(0).withSecond(0).withNano(0);
        for (Hotspot s : HOTSPOTS) {
            u -= hotspotScale * s.weight() * s.profile().weight(hour);
            if (u <= 0) {
                SyntheticCall c = hotspotCall(rnd, hour, s);
                return new SyntheticCall(t, c.lat(), c.lon(), c.node(), c.severity(), c.patientType(), c.hotspot());
            }
        }
        int node = backgroundNodes[rnd.nextInt(backgroundNodes.length)];
        SyntheticCall c = build(rnd, hour, graph.lat(node), graph.lon(node), node, Profile.BACKGROUND, "background");
        return new SyntheticCall(t, c.lat(), c.lon(), c.node(), c.severity(), c.patientType(), c.hotspot());
    }

    private SyntheticCall hotspotCall(Random rnd, LocalDateTime hour, Hotspot s) {
        // Gaussian scatter around the hotspot centre, then snap to the nearest road.
        double sigma = s.radiusMeters() / 2;
        double lat = s.lat() + rnd.nextGaussian() * sigma / 111_000;
        double lon = s.lon() + rnd.nextGaussian() * sigma / (111_000 * Math.cos(Math.toRadians(s.lat())));
        int node = graph.nearestNode(lat, lon);
        return build(rnd, hour, graph.lat(node), graph.lon(node), node, s.profile(), s.name());
    }

    private static SyntheticCall build(Random rnd, LocalDateTime hour, double lat, double lon, int node,
                                       Profile p, String name) {
        LocalDateTime t = hour.plusSeconds(rnd.nextInt(3600));
        PatientType type = pick(rnd, p.patientMix);
        return new SyntheticCall(t, lat, lon, node, severityFor(rnd, type), type, name);
    }

    private static PatientType pick(Random rnd, double[] mix) {
        double u = rnd.nextDouble();
        PatientType[] types = PatientType.values();
        for (int i = 0; i < mix.length; i++) {
            u -= mix[i];
            if (u <= 0) {
                return types[i];
            }
        }
        return PatientType.GENERAL;
    }

    /** Share of critical / serious calls per patient type; the rest are minor. */
    static Severity severityFor(Random rnd, PatientType type) {
        double[] cs = switch (type) {
            case CARDIAC -> new double[]{.45, .35};
            case STROKE -> new double[]{.50, .35};
            case TRAUMA -> new double[]{.25, .35};
            case RESPIRATORY -> new double[]{.20, .40};
            case MATERNITY -> new double[]{.10, .45};
            case GENERAL -> new double[]{.08, .30};
        };
        double u = rnd.nextDouble();
        if (u < cs[0]) {
            return Severity.CRITICAL;
        }
        return u < cs[0] + cs[1] ? Severity.SERIOUS : Severity.MINOR;
    }

    static int poisson(Random rnd, double mean) {
        // Knuth's method; fine for the small hourly means we use.
        double l = Math.exp(-mean);
        int k = 0;
        double p = 1;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > l);
        return k - 1;
    }
}
