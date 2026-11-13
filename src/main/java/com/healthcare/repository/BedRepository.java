package com.healthcare.repository;

import com.healthcare.model.Bed;
import com.healthcare.model.BedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {
    List<Bed> findByDepartment_IdAndStatusOrderById(Long departmentId, BedStatus status);

    long countByDepartment_IdAndStatus(Long departmentId, BedStatus status);

    long countByDepartment_IdAndStatusNot(Long departmentId, BedStatus status);

    List<Bed> findByDepartment_Id(Long departmentId);

    long countByStatus(BedStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bed from Bed bed
            where bed.department.id = :departmentId and bed.status = :status
            order by bed.id
            """)
    List<Bed> findAvailableBedsForUpdate(
            @Param("departmentId") Long departmentId, @Param("status") BedStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bed from Bed bed where bed.id = :id")
    Optional<Bed> findByIdForUpdate(@Param("id") Long id);
}
