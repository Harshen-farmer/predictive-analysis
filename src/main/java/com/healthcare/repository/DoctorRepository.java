package com.healthcare.repository;

import com.healthcare.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    List<Doctor> findAllByOrderByNameAsc();

    List<Doctor> findByOnDutyTrue();

    long countByDepartment_IdAndOnDutyTrue(Long departmentId);

    boolean existsByDepartment_Id(Long departmentId);

    long countByOnDutyTrue();

    List<Doctor> findByDepartment_IdAndOnDutyTrue(Long departmentId);

    List<Doctor> findByDepartment_IdAndOnDutyTrueOrderByNameAsc(Long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select doctor from Doctor doctor where doctor.department.id = :departmentId and doctor.onDuty = true")
    List<Doctor> findOnDutyByDepartmentForUpdate(@Param("departmentId") Long departmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select doctor from Doctor doctor where doctor.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
