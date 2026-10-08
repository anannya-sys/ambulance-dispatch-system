package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.AmbulanceStatus;
import com.goldenhour.dispatch.fleet.StandbyPostEntity;
import com.goldenhour.dispatch.fleet.StandbyPostRepository;
import com.goldenhour.dispatch.forecast.DemandModel;
import com.goldenhour.dispatch.forecast.PrePositioningPlanner;
import com.goldenhour.dispatch.forecast.StandbyPost;
import com.goldenhour.dispatch.hospital.Hospital;
import com.goldenhour.dispatch.hospital.HospitalRepository;
import com.goldenhour.dispatch.incident.IncidentRepository;
import com.goldenhour.dispatch.incident.IncidentStatus;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.trip.TripRepository;
import com.goldenhour.dispatch.trip.TripStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Creates hospitals, standby posts and the fleet on first start. On every start it also resets the live
 * demo (open trips cancelled, every ambulance free at its home post), because the moving-vehicle state
 * lives in memory and doesn't survive a restart.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CityModel city;
    private final DemandModel demand;
    private final GoldenHourProperties props;
    private final HospitalRepository hospitals;
    private final StandbyPostRepository posts;
    private final AmbulanceRepository ambulances;
    private final IncidentRepository incidents;
    private final TripRepository trips;
    private final LiveState state;
    private final SimClock clock;

    public DataSeeder(CityModel city, DemandModel demand, GoldenHourProperties props, HospitalRepository hospitals,
                      StandbyPostRepository posts, AmbulanceRepository ambulances, IncidentRepository incidents,
                      TripRepository trips, LiveState state, SimClock clock) {
        this.city = city;
        this.demand = demand;
        this.props = props;
        this.hospitals = hospitals;
        this.posts = posts;
        this.ambulances = ambulances;
        this.incidents = incidents;
        this.trips = trips;
        this.state = state;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Random rnd = new Random(2026);
        if (hospitals.count() == 0) {
            city.hospitals().forEach(s -> hospitals.save(Hospital.fromSite(s, 0.55, rnd)));
            log.info("Seeded {} hospitals (real locations, simulated beds)", city.hospitals().size());
        }
        if (posts.count() == 0) {
            city.posts().forEach(p -> posts.save(new StandbyPostEntity(p.index(), p.name(), p.lat(), p.lon(), p.node())));
        }
        if (ambulances.count() == 0) {
            List<StandbyPost> homes = PrePositioningPlanner.staticStations(demand, 25, city, clock.now().minusWeeks(1));
            for (int i = 0; i < props.fleetSize(); i++) {
                StandbyPost home = homes.get(i % homes.size());
                String kind = i % 5 < 2 ? "ALS" : "BLS";
                ambulances.save(new Ambulance(String.format("GH-%02d", i + 1), kind, home.lat(), home.lon(),
                        home.node(), home.index()));
            }
            log.info("Seeded {} simulated ambulances at {} home stations", props.fleetSize(), homes.size());
        }
        resetLiveState();
    }

    private void resetLiveState() {
        LocalDateTime now = clock.now();
        trips.findByStatusIn(List.of(TripStatus.ASSIGNED, TripStatus.EN_ROUTE, TripStatus.AT_SCENE,
                TripStatus.PICKED_UP, TripStatus.AT_HOSPITAL)).forEach(t -> {
            t.moveToCancelledOnRestart(now);
        });
        incidents.findByStatusIn(List.of(IncidentStatus.QUEUED, IncidentStatus.ASSIGNED))
                .forEach(i -> i.setStatus(IncidentStatus.CANCELLED));
        state.clear();
        for (Ambulance a : ambulances.findAll()) {
            StandbyPost home = city.posts().get(a.getHomePostId());
            a.moveTo(home.lat(), home.lon(), home.node());
            a.setStatus(AmbulanceStatus.AVAILABLE);
            a.setCurrentTripId(null);
            a.setTargetPostId(null);
            a.setAvailableAt(null);
            state.setPosition(a.getId(), new LiveState.Position(home.lat(), home.lon(), home.node(), 0));
        }
    }
}
