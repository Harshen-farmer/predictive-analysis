package com.healthcare.service;

import com.healthcare.dto.AuditLogResponse;
import com.healthcare.model.AuditLog;
import com.healthcare.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(
            String username, String action, LocalDate date, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return auditLogRepository.search(
                        blankToNull(username),
                        blankToNull(action),
                        date == null ? null : date.atStartOfDay(),
                        date == null ? null : date.atTime(LocalTime.MAX),
                        pageable)
                .map(this::toResponse);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AuditLogResponse toResponse(AuditLog audit) {
        return new AuditLogResponse(
                audit.getId(),
                audit.getUsername(),
                audit.getAction(),
                audit.getEntity(),
                audit.getEntityId(),
                audit.getTimestamp());
    }
}
