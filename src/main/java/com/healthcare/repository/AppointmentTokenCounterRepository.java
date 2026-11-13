package com.healthcare.repository;

import com.healthcare.model.AppointmentTokenCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AppointmentTokenCounterRepository extends JpaRepository<AppointmentTokenCounter, Long> {
    Optional<AppointmentTokenCounter> findByDepartment_IdAndDate(Long departmentId, LocalDate date);
}
