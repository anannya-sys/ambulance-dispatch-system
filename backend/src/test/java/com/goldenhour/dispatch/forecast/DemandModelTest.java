package com.goldenhour.dispatch.forecast;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DemandModelTest {

    static final LocalDateTime MONDAY = LocalDateTime.of(2026, 1, 5, 0, 0);

    @Test
    void learnsWhereAndWhenCallsHappen() {
        List<SyntheticCall> history = new ArrayList<>();
        // 4 weeks: 3 calls every Monday 9-10am at Dadar, 1 call every Monday 3am at Colaba
        for (int w = 0; w < 4; w++) {
            LocalDateTime mon = MONDAY.plusWeeks(w);
            for (int i = 0; i < 3; i++) {
                history.add(call(mon.plusHours(9).plusMinutes(10 * i), 19.0190, 72.8430));
            }
            history.add(call(mon.plusHours(3), 18.9067, 72.8147));
        }
        history.add(call(MONDAY.plusWeeks(4), 19.0190, 72.8430)); // spans exactly 4 weeks
        DemandModel m = DemandModel.train(history);

        var morning = m.forecast(MONDAY.plusWeeks(5).plusHours(9), 60);
        double dadar = morning.stream().filter(f -> f.lat() > 19).mapToDouble(DemandModel.CellForecast::expectedCalls).sum();
        double colaba = morning.stream().filter(f -> f.lat() < 19).mapToDouble(DemandModel.CellForecast::expectedCalls).sum();
        assertThat(dadar).isGreaterThan(2.0);
        assertThat(colaba).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void forecastScalesWithWindowLength() {
        List<SyntheticCall> history = new ArrayList<>();
        for (int d = 0; d < 28; d++) {
            for (int h = 0; h < 24; h++) {
                history.add(call(MONDAY.plusDays(d).plusHours(h), 19.0760, 72.8777));
            }
        }
        DemandModel m = DemandModel.train(history);
        double hour = m.forecast(MONDAY.plusWeeks(5), 60).get(0).expectedCalls();
        double half = m.forecast(MONDAY.plusWeeks(5), 30).get(0).expectedCalls();
        assertThat(hour).isCloseTo(1.0, within(0.1));
        assertThat(half).isCloseTo(hour / 2, within(0.05));
    }

    private static SyntheticCall call(LocalDateTime t, double lat, double lon) {
        return new SyntheticCall(t, lat, lon, 0, com.goldenhour.dispatch.incident.Severity.SERIOUS,
                com.goldenhour.dispatch.incident.PatientType.GENERAL, "test");
    }
}
