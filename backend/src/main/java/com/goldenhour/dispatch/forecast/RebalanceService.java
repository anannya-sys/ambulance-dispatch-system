package com.goldenhour.dispatch.forecast;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.AmbulanceStatus;
import com.goldenhour.dispatch.live.LiveEngine;
import com.goldenhour.dispatch.live.LiveSettings;
import com.goldenhour.dispatch.live.LiveState;
import com.goldenhour.dispatch.live.SimClock;
import com.goldenhour.dispatch.realtime.LivePublisher;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.TrafficBand;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Every 10 simulated minutes: forecast the next hour, choose standby posts for the idle ambulances and
 * send them there. Publishes the plan (forecast heat + chosen posts) for the dashboard.
 */
@Service
public class RebalanceService {

    static final Duration EVERY = Duration.ofMinutes(10);
    /** Don't bother moving a unit that is already within this many seconds of its post. */
    static final double CLOSE_ENOUGH_SECONDS = 60;

    private final GoldenHourProperties props;
    private final LiveSettings settings;
    private final SimClock clock;
    private final DemandModel demand;
    private final CityModel city;
    private final AmbulanceRepository ambulances;
    private final LiveState state;
    private final LiveEngine engine;
    private final LivePublisher live;
    private volatile Plan lastPlan;
    private LocalDateTime nextDue;

    public RebalanceService(GoldenHourProperties props, LiveSettings settings, SimClock clock, DemandModel demand,
                            CityModel city, AmbulanceRepository ambulances, LiveState state, LiveEngine engine,
                            LivePublisher live) {
        this.props = props;
        this.settings = settings;
        this.clock = clock;
        this.demand = demand;
        this.city = city;
        this.ambulances = ambulances;
        this.state = state;
        this.engine = engine;
        this.live = live;
    }

    public record HeatCell(double lat, double lon, double expectedCalls) {
    }

    public record PostView(int id, String name, double lat, double lon) {
    }

    /** What the dashboard shows: the forecast and where idle units were sent. */
    public record Plan(LocalDateTime at, boolean active, int idleUnits, int moved, List<HeatCell> heat,
                       List<PostView> posts, double coverage8MinPct, double expectedCallsNextHour) {
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 8000)
    public void scheduled() {
        if (!props.live().enabled()) {
            return;
        }
        LocalDateTime now = clock.now();
        if (nextDue == null || !now.isBefore(nextDue)) {
            nextDue = now.plus(EVERY);
            rebalance(now);
        }
    }

    public Plan rebalance(LocalDateTime now) {
        TrafficBand band = TrafficBand.at(now);
        List<DemandModel.CellForecast> forecast = demand.forecast(now, 60);
        List<Ambulance> idle = ambulances.findByStatusIn(List.of(AmbulanceStatus.AVAILABLE, AmbulanceStatus.REPOSITIONING));
        int moved = 0;
        List<StandbyPost> posts = List.of();
        if (settings.prePositioning() && !idle.isEmpty()) {
            posts = PrePositioningPlanner.choosePosts(forecast, idle.size(), city.posts(), city.postTable(),
                    city.graph(), band);
            List<PrePositioningPlanner.IdleUnit> units = idle.stream().map(a -> {
                LiveState.Position p = state.currentPosition(a.getId(), now);
                return new PrePositioningPlanner.IdleUnit(a.getId(), p != null ? p.node() : a.getRoadNode());
            }).toList();
            Map<Long, StandbyPost> plan = PrePositioningPlanner.assign(units, posts, city.postTable(), band);
            for (PrePositioningPlanner.IdleUnit u : units) {
                StandbyPost post = plan.get(u.id());
                if (post == null) {
                    continue;
                }
                Ambulance a = idle.stream().filter(x -> x.getId() == u.id()).findFirst().orElseThrow();
                boolean alreadyGoing = a.getStatus() == AmbulanceStatus.REPOSITIONING
                        && Integer.valueOf(post.index()).equals(a.getTargetPostId());
                if (alreadyGoing || city.postTable().seconds(post.index(), u.node(), band) <= CLOSE_ENOUGH_SECONDS) {
                    continue;
                }
                if (engine.startReposition(u.id(), post, now)) {
                    moved++;
                }
            }
        }
        List<HeatCell> heat = forecast.stream()
                .sorted(Comparator.comparingDouble(DemandModel.CellForecast::expectedCalls).reversed())
                .limit(120).map(f -> new HeatCell(round(f.lat()), round(f.lon()), f.expectedCalls())).toList();
        double coverage = posts.isEmpty() ? 0 : 100 * PrePositioningPlanner.coverage(forecast, posts,
                city.postTable(), city.graph(), band, 8 * 60);
        double total = forecast.stream().mapToDouble(DemandModel.CellForecast::expectedCalls).sum()
                * settings.callsPerDay() / Math.max(1, props.live().callsPerDay());
        Plan p = new Plan(now, settings.prePositioning(), idle.size(), moved, heat,
                posts.stream().map(s -> new PostView(s.index(), s.name(), s.lat(), s.lon())).toList(), coverage, total);
        lastPlan = p;
        live.positioning(p);
        return p;
    }

    public Plan lastPlan() {
        return lastPlan;
    }

    private static double round(double v) {
        return Math.round(v * 1e5) / 1e5;
    }
}
