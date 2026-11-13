package com.healthcare.repository;

import com.healthcare.model.TransferRequest;
import com.healthcare.model.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {
    List<TransferRequest> findAllByOrderByIdDesc();

    boolean existsByStatus(TransferStatus status);

    boolean existsByFromDepartment_IdOrToDepartment_Id(Long fromDepartmentId, Long toDepartmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from TransferRequest transfer where transfer.id = :id")
    Optional<TransferRequest> findByIdForUpdate(@Param("id") Long id);
}
