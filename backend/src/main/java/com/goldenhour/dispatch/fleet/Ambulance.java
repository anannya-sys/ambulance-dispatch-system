package com.goldenhour.dispatch.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** One vehicle of the (simulated) fleet. Position columns are refreshed from the GPS stream. */
@Entity
@Table(name = "ambulance")
public class Ambulance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_sign", nullable = false, unique = true)
    private String callSign;

    /** ALS = advanced life support (paramedic + monitor), BLS = basic. */
    @Column(nullable = false)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmbulanceStatus status;

    private double lat;
    private double lon;

    @Column(name = "road_node")
    private int roadNode;

    private double heading;

    @Column(name = "home_post_id")
    private Integer homePostId;

    @Column(name = "target_post_id")
    private Integer targetPostId;

    @Column(name = "current_trip_id")
    private Long currentTripId;

    @Column(name = "last_ping_at")
    private LocalDateTime lastPingAt;

    @Column(name = "available_at")
    private LocalDateTime availableAt;

    @Version
    private long version;

    protected Ambulance() {
    }

    public Ambulance(String callSign, String kind, double lat, double lon, int roadNode, Integer homePostId) {
        this.callSign = callSign;
        this.kind = kind;
        this.status = AmbulanceStatus.AVAILABLE;
        this.lat = lat;
        this.lon = lon;
        this.roadNode = roadNode;
        this.homePostId = homePostId;
    }

    public void moveTo(double newLat, double newLon, int node) {
        this.lat = newLat;
        this.lon = newLon;
        this.roadNode = node;
    }

    public Long getId() {
        return id;
    }

    public String getCallSign() {
        return callSign;
    }

    public String getKind() {
        return kind;
    }

    public AmbulanceStatus getStatus() {
        return status;
    }

    public void setStatus(AmbulanceStatus status) {
        this.status = status;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getRoadNode() {
        return roadNode;
    }

    public double getHeading() {
        return heading;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    public Integer getHomePostId() {
        return homePostId;
    }

    public Integer getTargetPostId() {
        return targetPostId;
    }

    public void setTargetPostId(Integer targetPostId) {
        this.targetPostId = targetPostId;
    }

    public Long getCurrentTripId() {
        return currentTripId;
    }

    public void setCurrentTripId(Long currentTripId) {
        this.currentTripId = currentTripId;
    }

    public LocalDateTime getLastPingAt() {
        return lastPingAt;
    }

    public void setLastPingAt(LocalDateTime lastPingAt) {
        this.lastPingAt = lastPingAt;
    }

    public LocalDateTime getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(LocalDateTime availableAt) {
        this.availableAt = availableAt;
    }

    public long getVersion() {
        return version;
    }
}
