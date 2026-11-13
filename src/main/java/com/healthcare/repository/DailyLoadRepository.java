package com.healthcare.repository;

import com.healthcare.model.DailyLoad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyLoadRepository extends JpaRepository<DailyLoad, Long> {
    List<DailyLoad> findByDepartmentIdAndDateBetweenOrderByDate(Long departmentId, LocalDate start, LocalDate end);

    Optional<DailyLoad> findByDepartment_IdAndDate(Long departmentId, LocalDate date);

    List<DailyLoad> findByDepartment_IdAndDateBetweenOrderByDate(
            Long departmentId, LocalDate start, LocalDate end);

    List<DailyLoad> findByDateBetweenOrderByDateAscDepartment_NameAsc(LocalDate start, LocalDate end);

    @Query("select coalesce(sum(load.patientCount), 0) from DailyLoad load where load.date = :date")
    long sumPatientCountByDate(@Param("date") LocalDate date);

    boolean existsByDepartment_Id(Long departmentId);
}
