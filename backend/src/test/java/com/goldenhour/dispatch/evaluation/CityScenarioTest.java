package com.goldenhour.dispatch.evaluation;

import com.goldenhour.dispatch.corridor.GreenCorridorPlanner;
import com.goldenhour.dispatch.corridor.SignalWindow;
import com.goldenhour.dispatch.forecast.CallPattern;
import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.forecast.PrePositioningPlanner;
import com.goldenhour.dispatch.forecast.StandbyPost;
import com.goldenhour.dispatch.forecast.SyntheticCall;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.Route;
import com.goldenhour.dispatch.routing.TrafficBand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that need the real Mumbai road network: green corridor timing, synthetic demand, pre-positioning
 * and a short end-to-end simulation. The city model loads once (~2 s).
 */
class CityScenarioTest {

    static CityModel city;
    static CallPattern pattern;
    static DemandModel model;
    static final LocalDateTime WEEK = EvaluationRunner.TEST_START;

    @BeforeAll
    static void load() {
        city = CityModel.load();
        pattern = new CallPattern(city.graph(), 500);
        model = DemandModel.train(pattern.generate(WEEK.minusWeeks(4), WEEK, 7));
    }

    @Test
    void greenCorridorWindowsOpenBeforeTheAmbulanceArrives() {
        int a = city.graph().nearestNode(19.0430, 72.8620);
        int b = city.graph().nearestNode(19.1360, 72.8300);
        Route route = city.router().route(a, b, TrafficBand.PEAK, true);
        LocalDateTime depart = LocalDateTime.of(2026, 3, 2, 9, 0);

        List<SignalWindow> windows = GreenCorridorPlanner.plan(route, depart);

        assertThat(GreenCorridorPlanner.eligible(Severity.CRITICAL)).isTrue();
        assertThat(GreenCorridorPlanner.eligible(Severity.SERIOUS)).isFalse();
        assertThat(windows).hasSameSizeAs(route.signals()).isNotEmpty();
        for (int i = 0; i < windows.size(); i++) {
            LocalDateTime eta = depart.plusSeconds((long) route.signals().get(i).secondsFromStart());
            assertThat(windows.get(i).greenFrom()).isBefore(eta);
            assertThat(windows.get(i).greenUntil()).isAfter(eta);
        }
        assertThat(GreenCorridorPlanner.secondsSaved(route)).isPositive();
    }

    @Test
    void syntheticCallsAreReproducibleAndPeakInTheEvening() {
        List<SyntheticCall> a = pattern.generate(WEEK, WEEK.plusDays(1), 3);
        List<SyntheticCall> b = pattern.generate(WEEK, WEEK.plusDays(1), 3);
        assertThat(a).isEqualTo(b);
        assertThat(a.size()).isBetween(400, 600);
        assertThat(pattern.expectedCallsInHour(WEEK.plusHours(19)))
                .isGreaterThan(pattern.expectedCallsInHour(WEEK.plusHours(4)));
    }

    @Test
    void forecastDrivenPostsCoverMoreUpcomingDemandThanStaticStations() {
        LocalDateTime evening = WEEK.plusHours(19);
        var demand = model.forecast(evening, 60);
        List<StandbyPost> stat = PrePositioningPlanner.staticStations(model, 25, city, WEEK);
        List<StandbyPost> dynamic = PrePositioningPlanner.choosePosts(demand, 25, city.posts(), city.postTable(),
                city.graph(), TrafficBand.PEAK);

        double staticCov = PrePositioningPlanner.coverage(demand, stat, city.postTable(), city.graph(), TrafficBand.PEAK, 480);
        double dynamicCov = PrePositioningPlanner.coverage(demand, dynamic, city.postTable(), city.graph(), TrafficBand.PEAK, 480);
        assertThat(dynamicCov).isGreaterThanOrEqualTo(staticCov);
    }

    @Test
    void eachPostGetsAtMostOneAmbulance() {
        List<StandbyPost> posts = city.posts().subList(0, 10);
        List<PrePositioningPlanner.IdleUnit> idle = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            idle.add(new PrePositioningPlanner.IdleUnit(i, city.posts().get(i).node()));
        }
        Map<Long, StandbyPost> plan = PrePositioningPlanner.assign(idle, posts, city.postTable(), TrafficBand.OFF_PEAK);
        assertThat(plan.size()).isLessThanOrEqualTo(posts.size());
        assertThat(new HashSet<>(plan.values())).hasSameSizeAs(plan.values());
    }

    @Test
    void goldenHourBeatsTheBaselineInAShortSimulation() {
        EvaluationReport r = new EvaluationRunner(city).run(1, 2, 500, 50, false);
        SimResult base = r.averages().get(0);
        SimResult gh = r.averages().get(r.averages().size() - 1);
        assertThat(gh.meanCriticalResponseMin()).isLessThan(base.meanCriticalResponseMin());
        assertThat(gh.goldenHourPct()).isGreaterThan(base.goldenHourPct());
        assertThat(gh.turnAwaysOrTransfers()).isLessThanOrEqualTo(base.turnAwaysOrTransfers());
    }
}
