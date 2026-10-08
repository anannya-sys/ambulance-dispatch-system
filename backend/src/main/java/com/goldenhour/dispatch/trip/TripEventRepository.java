package com.goldenhour.dispatch.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    List<TripEvent> findByTripIdOrderByAtAsc(Long tripId);
}
