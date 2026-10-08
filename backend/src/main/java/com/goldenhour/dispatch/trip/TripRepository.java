package com.goldenhour.dispatch.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByStatusIn(Collection<TripStatus> statuses);

    List<Trip> findByIncidentIdOrderByAssignedAtAsc(Long incidentId);

    List<Trip> findByAssignedAtAfter(LocalDateTime after);

    List<Trip> findByStatusAndClosedAtAfter(TripStatus status, LocalDateTime after);
}
