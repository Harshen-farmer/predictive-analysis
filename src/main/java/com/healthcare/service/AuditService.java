package com.healthcare.service;

import com.healthcare.model.AuditLog;
import com.healthcare.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String action, String entity, Long entityId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null || !authentication.isAuthenticated()
                ? "system"
                : authentication.getName();

        auditLogRepository.save(new AuditLog(username, action, entity, entityId.toString(), LocalDateTime.now()));
    }
}
