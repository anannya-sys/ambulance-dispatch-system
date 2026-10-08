package com.goldenhour.dispatch.hospital;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalAlertRepository extends JpaRepository<HospitalAlert, Long> {

    List<HospitalAlert> findTop50ByOrderByCreatedAtDesc();
}
