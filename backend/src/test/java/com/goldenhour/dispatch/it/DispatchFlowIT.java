package com.goldenhour.dispatch.it;

import com.goldenhour.dispatch.fleet.Ambulance;
import com.goldenhour.dispatch.fleet.AmbulanceRepository;
import com.goldenhour.dispatch.fleet.AmbulanceStatus;
import com.goldenhour.dispatch.incident.IncidentService;
import com.goldenhour.dispatch.incident.IncidentSource;
import com.goldenhour.dispatch.incident.PatientType;
import com.goldenhour.dispatch.incident.Severity;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.trip.InvalidTransitionException;
import com.goldenhour.dispatch.trip.TripRepository;
import com.goldenhour.dispatch.trip.TripService;
import com.goldenhour.dispatch.trip.TripStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end checks against real PostgreSQL/PostGIS, Redis and Kafka in throwaway containers. The live
 * simulation is switched off so the tests drive every step themselves.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"goldenhour.live.enabled=false", "spring.kafka.consumer.auto-offset-reset=earliest"})
class DispatchFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired TestRestTemplate http;
    @Autowired IncidentService incidents;
    @Autowired AmbulanceRepository ambulances;
    @Autowired TripRepository trips;
    @Autowired TripService tripService;
    @Autowired CityModel city;

    @Test
    void seedsTheSimulatedFleetAndRealHospitals() {
        assertThat(ambulances.count()).isEqualTo(50);
        List<?> hospitals = http.getForObject("/api/hospitals", List.class);
        assertThat(hospitals).hasSize(city.hospitals().size());
    }

    @Test
    void postgisFindsTheClosestFreeAmbulancesFirst() {
        double lat = 19.0760, lon = 72.8777;
        List<Ambulance> nearest = ambulances.findNearestFree(lat, lon, 5);
        assertThat(nearest).hasSize(5);
        for (int i = 1; i < nearest.size(); i++) {
            assertThat(dist(nearest.get(i - 1), lat, lon)).isLessThanOrEqualTo(dist(nearest.get(i), lat, lon) + 10);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportingAnEmergencyAssignsAnAmbulanceThroughTheApi() {
        ResponseEntity<Map> res = http.postForEntity("/api/incidents", Map.of(
                "lat", 19.0596, "lon", 72.8295, "severity", "CRITICAL", "patientType", "CARDIAC",
                "description", "Chest pain"), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> incident = (Map<String, Object>) res.getBody().get("incident");
        Map<String, Object> assignment = (Map<String, Object>) res.getBody().get("assignment");
        assertThat(incident.get("status")).isEqualTo("ASSIGNED");
        assertThat(assignment.get("callSign").toString()).startsWith("GH-");
    }

    @Test
    void rejectsLocationsOutsideMumbai() {
        ResponseEntity<Map> res = http.postForEntity("/api/incidents", Map.of(
                "lat", 28.61, "lon", 77.20, "severity", "MINOR", "patientType", "GENERAL"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void concurrentCallsNeverDoubleBookAnAmbulance() throws Exception {
        // 20 callers from the same street at the same moment all want the same nearest ambulance
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Callable<IncidentService.Created>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> incidents.create(18.9220, 72.8347, Severity.SERIOUS, PatientType.TRAUMA,
                    IncidentSource.MANUAL, "pile-up"));
        }
        Set<Long> used = new HashSet<>();
        int assigned = 0;
        for (Future<IncidentService.Created> f : pool.invokeAll(jobs)) {
            var a = f.get().assignment();
            if (a.isPresent()) {
                assigned++;
                assertThat(used.add(a.get().ambulance().getId()))
                        .as("ambulance %s booked twice", a.get().ambulance().getCallSign()).isTrue();
            }
        }
        pool.shutdown();
        assertThat(assigned).isGreaterThanOrEqualTo(15);
        for (Long id : used) {
            assertThat(ambulances.findById(id).orElseThrow().getStatus()).isEqualTo(AmbulanceStatus.DISPATCHED);
        }
    }

    @Test
    void tripFollowsTheLifecycleAndRejectsIllegalJumps() {
        var created = incidents.create(19.1197, 72.8468, Severity.MINOR, PatientType.GENERAL, IncidentSource.MANUAL, "fall");
        long tripId = created.assignment().orElseThrow().trip().getId();
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> tripService.arriveAtHospital(tripId, now))
                .isInstanceOf(InvalidTransitionException.class);

        tripService.acknowledge(tripId, now, now.plusMinutes(6));
        tripService.arriveAtScene(tripId, now.plusMinutes(6));
        assertThat(trips.findById(tripId).orElseThrow().getStatus()).isEqualTo(TripStatus.AT_SCENE);
        assertThat(trips.findById(tripId).orElseThrow().getAtSceneAt()).isNotNull();
    }

    @Test
    void gpsPingsFlowThroughKafkaIntoPostgresAndRedis() throws Exception {
        long id = ambulances.findAll().get(49).getId();
        ResponseEntity<Map> res = http.postForEntity("/api/ambulances/" + id + "/location",
                Map.of("lat", 19.0330, "lon", 72.8550, "heading", 90, "speedKmph", 35), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // consumers are asynchronous; poll for up to 20 s
        boolean seen = false;
        for (int i = 0; i < 80 && !seen; i++) {
            Ambulance a = ambulances.findById(id).orElseThrow();
            seen = Math.abs(a.getLat() - 19.0330) < 1e-6;
            if (!seen) {
                Thread.sleep(250);
            }
        }
        assertThat(seen).as("position stored in PostgreSQL").isTrue();
        List<?> nearby = http.getForObject("/api/ambulances/nearby?lat=19.0330&lon=72.8550&radiusKm=0.5", List.class);
        assertThat(nearby).isNotEmpty();
    }

    private static double dist(Ambulance a, double lat, double lon) {
        return com.goldenhour.dispatch.geo.GeoUtils.distanceMeters(a.getLat(), a.getLon(), lat, lon);
    }
}
