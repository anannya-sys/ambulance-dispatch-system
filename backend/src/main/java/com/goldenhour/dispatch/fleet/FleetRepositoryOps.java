package com.goldenhour.dispatch.fleet;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Conditional status changes used by the scheduler. Each one only fires if the vehicle is still in the
 * expected state, so a concurrent dispatch always wins and is never overwritten.
 */
public interface FleetRepositoryOps extends Repository<Ambulance, Long> {

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE ambulance SET status = 'REPOSITIONING', target_post_id = :post, version = version + 1
            WHERE id = :id AND status IN ('AVAILABLE', 'REPOSITIONING')
            """, nativeQuery = true)
    int startRepositioning(@Param("id") long id, @Param("post") int post);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE ambulance SET status = 'AVAILABLE', version = version + 1
            WHERE id = :id AND status = 'REPOSITIONING'
            """, nativeQuery = true)
    int finishRepositioning(@Param("id") long id);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE ambulance SET status = 'AVAILABLE', available_at = NULL, version = version + 1
            WHERE status = 'OUT_OF_SERVICE' AND available_at <= :now
            """, nativeQuery = true)
    int returnToService(@Param("now") LocalDateTime now);
}
