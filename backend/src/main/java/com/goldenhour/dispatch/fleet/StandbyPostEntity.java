package com.goldenhour.dispatch.fleet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persisted copy of a {@link com.goldenhour.dispatch.forecast.StandbyPost} (for joins and the API). */
@Entity
@Table(name = "standby_post")
public class StandbyPostEntity {

    @Id
    private Integer id;
    private String name;
    private double lat;
    private double lon;
    @Column(name = "road_node")
    private int roadNode;

    protected StandbyPostEntity() {
    }

    public StandbyPostEntity(Integer id, String name, double lat, double lon, int roadNode) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.roadNode = roadNode;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
