package com.healthcare.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String username,
        String action,
        String entity,
        String entityId,
        LocalDateTime timestamp) {
}
