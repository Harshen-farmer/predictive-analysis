package com.healthcare.repository;

import com.healthcare.model.DoctorAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DoctorAttendanceRepository extends JpaRepository<DoctorAttendance, Long> {
    Optional<DoctorAttendance> findByDoctor_IdAndDate(Long doctorId, LocalDate date);

    List<DoctorAttendance> findByDate(LocalDate date);

    boolean existsByDoctor_Id(Long doctorId);
}
