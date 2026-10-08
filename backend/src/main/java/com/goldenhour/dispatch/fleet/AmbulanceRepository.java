package com.goldenhour.dispatch.fleet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AmbulanceRepository extends JpaRepository<Ambulance, Long> {

    /**
     * K nearest free ambulances by straight-line distance, using the PostGIS KNN operator {@code <->}.
     * With the partial GiST index on free units this is an index scan that stops after {@code limit}
     * rows, instead of computing the distance to every ambulance. Road ETA is then computed only for
     * these few candidates.
     */
    @Query(value = """
            SELECT * FROM ambulance
            WHERE status IN ('AVAILABLE', 'REPOSITIONING')
            ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            LIMIT :limit
            """, nativeQuery = true)
    List<Ambulance> findNearestFree(@Param("lat") double lat, @Param("lon") double lon, @Param("limit") int limit);

    /**
     * Atomic compare-and-set: claim the ambulance only if it is still free. Returns 1 if we won the race,
     * 0 if another dispatcher got it first. This is the database-level guarantee against double booking.
     */
    @Modifying
    @Query(value = """
            UPDATE ambulance SET status = 'DISPATCHED', current_trip_id = NULL, target_post_id = NULL,
                   version = version + 1
            WHERE id = :id AND status IN ('AVAILABLE', 'REPOSITIONING')
            """, nativeQuery = true)
    int claim(@Param("id") long id);

    List<Ambulance> findByStatusIn(Collection<AmbulanceStatus> statuses);

    long countByStatus(AmbulanceStatus status);

    /** Fast position update from the GPS stream, without loading the entity. */
    @Modifying
    @Query(value = """
            UPDATE ambulance SET lat = :lat, lon = :lon, road_node = :node, heading = :heading, last_ping_at = :at
            WHERE id = :id
            """, nativeQuery = true)
    int updatePosition(@Param("id") long id, @Param("lat") double lat, @Param("lon") double lon,
                       @Param("node") int node, @Param("heading") double heading,
                       @Param("at") java.time.LocalDateTime at);
}
