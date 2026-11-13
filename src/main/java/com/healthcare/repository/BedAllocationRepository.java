package com.healthcare.repository;

import com.healthcare.model.BedAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface BedAllocationRepository extends JpaRepository<BedAllocation, Long> {
    boolean existsByPatient_IdAndDischargeDateIsNull(Long patientId);

    boolean existsByBed_Id(Long bedId);

    boolean existsByBed_IdAndDischargeDateIsNull(Long bedId);

    boolean existsByBed_Department_IdAndDischargeDateIsNull(Long departmentId);

    boolean existsByBed_Department_Id(Long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select allocation from BedAllocation allocation where allocation.id = :id")
    Optional<BedAllocation> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select allocation from BedAllocation allocation
            where allocation.patient.id = :patientId and allocation.dischargeDate is null
            """)
    List<BedAllocation> findActiveByPatientId(@Param("patientId") Long patientId);

    List<BedAllocation> findByDischargeDateIsNullOrderByAdmitDateDesc();

    @Query("""
            select allocation from BedAllocation allocation
            where allocation.admitDate <= :endDate
              and (allocation.dischargeDate is null or allocation.dischargeDate >= :startDate)
            """)
    List<BedAllocation> findAllocationsDuring(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
