package com.goldenhour.dispatch.trip;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.LocalDateTime;

/** Append-only audit log of everything that happened on a trip. */
@Entity
@Table(name = "trip_event")
public class TripEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id")
    private Long tripId;

    private LocalDateTime at;

    private String type;

    private String detail;

    protected TripEvent() {
    }

    public TripEvent(Long tripId, LocalDateTime at, String type, String detail) {
        this.tripId = tripId;
        this.at = at;
        this.type = type;
        this.detail = detail;
    }

    public Long getTripId() {
        return tripId;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public String getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }
}
