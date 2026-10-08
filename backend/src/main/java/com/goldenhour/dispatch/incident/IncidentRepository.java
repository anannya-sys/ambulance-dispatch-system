package com.goldenhour.dispatch.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    List<Incident> findByCreatedAtAfter(LocalDateTime after);

    long countByStatus(IncidentStatus status);

    /** Compare-and-set so two dispatcher threads can't both take the same call. */
    @Modifying
    @Query(value = "UPDATE incident SET status = 'ASSIGNED', version = version + 1 WHERE id = :id AND status = 'QUEUED'",
            nativeQuery = true)
    int claim(@Param("id") long id);
}
