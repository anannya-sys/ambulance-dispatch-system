package com.goldenhour.dispatch.routing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RouterTest {

    static RoadGraph graph;
    static Router router;

    @BeforeAll
    static void load() {
        graph = RoadGraphLoader.fromClasspath("data/mumbai-roads.bin.gz");
        router = new Router(graph);
    }

    @Test
    void loadsTheRealMumbaiGraph() {
        assertThat(graph.nodeCount()).isGreaterThan(50_000);
        assertThat(graph.signalNodes()).hasSizeGreaterThan(1_000);
    }

    @Test
    void colabaToAndheriIsAPlausibleCityTrip() {
        int colaba = graph.nearestNode(18.9067, 72.8147);
        int andheri = graph.nearestNode(19.1197, 72.8468);
        Route night = router.route(colaba, andheri, TrafficBand.NIGHT, false);
        Route peak = router.route(colaba, andheri, TrafficBand.PEAK, false);

        // ~25 km apart by road; straight line is ~24 km
        assertThat(night.totalMeters()).isBetween(22_000.0, 35_000.0);
        // at night it should take roughly 30-60 minutes, and peak traffic must be slower
        assertThat(night.totalSeconds()).isBetween(25 * 60.0, 70 * 60.0);
        assertThat(peak.totalSeconds()).isGreaterThan(night.totalSeconds() * 1.3);
    }

    @Test
    void routeEndsWhereItShouldAndInterpolatesPositions() {
        int a = graph.nearestNode(19.0190, 72.8430); // Dadar
        int b = graph.nearestNode(19.0600, 72.8360); // Bandra
        Route r = router.route(a, b, TrafficBand.OFF_PEAK, false);
        double[] start = r.positionAt(0);
        double[] end = r.positionAt(r.totalSeconds() + 10);
        assertThat(start[0]).isCloseTo(graph.lat(a), within(1e-9));
        assertThat(end[0]).isCloseTo(graph.lat(b), within(1e-9));
        assertThat(r.remainingSeconds(r.totalSeconds() / 2)).isCloseTo(r.totalSeconds() / 2, within(1e-6));
    }

    @Test
    void greenCorridorRemovesSignalWaits() {
        int a = graph.nearestNode(19.0430, 72.8620); // Sion
        int b = graph.nearestNode(19.1360, 72.8300); // Andheri West
        Route normal = router.route(a, b, TrafficBand.OFF_PEAK, false);
        Route corridor = router.route(a, b, TrafficBand.OFF_PEAK, true);
        assertThat(normal.signals()).isNotEmpty();
        assertThat(corridor.totalSeconds()).isLessThan(normal.totalSeconds());
    }

    @Test
    void oneToManyMatchesAStar() {
        int incident = graph.nearestNode(19.0760, 72.8777);
        int[] ambulances = {
                graph.nearestNode(19.0600, 72.8360),
                graph.nearestNode(19.1000, 72.8450),
                graph.nearestNode(19.0430, 72.8620)};
        double[] t = router.timesFrom(incident, ambulances, TrafficBand.OFF_PEAK, false, 3);
        for (int i = 0; i < ambulances.length; i++) {
            double aStar = router.route(incident, ambulances[i], TrafficBand.OFF_PEAK, false).totalSeconds();
            assertThat(t[i]).isCloseTo(aStar, within(0.5));
        }
    }

    @Test
    void travelTimeTableAgreesWithSearch() {
        int hospital = graph.nearestNode(19.0030, 72.8410);
        TravelTimeTable table = new TravelTimeTable(router, new int[]{hospital});
        int patient = graph.nearestNode(19.0720, 72.8830);
        double direct = router.route(hospital, patient, TrafficBand.PEAK, false).totalSeconds();
        assertThat(table.seconds(0, patient, TrafficBand.PEAK)).isCloseTo(direct, within(1.0));
    }
}
