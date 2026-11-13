package com.healthcare.repository;

import com.healthcare.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("""
            select audit from AuditLog audit
            where (:username is null or lower(audit.username) like lower(concat('%', :username, '%')))
              and (:action is null or lower(audit.action) = lower(:action))
              and (:startTime is null or audit.timestamp >= :startTime)
              and (:endTime is null or audit.timestamp <= :endTime)
            """)
    Page<AuditLog> search(
            @Param("username") String username,
            @Param("action") String action,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
