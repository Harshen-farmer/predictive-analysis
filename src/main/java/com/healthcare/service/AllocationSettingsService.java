package com.healthcare.service;

import com.healthcare.dto.AllocationThresholdsRequest;
import com.healthcare.dto.AllocationThresholdsResponse;
import com.healthcare.model.AllocationSettings;
import com.healthcare.repository.AllocationSettingsRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllocationSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final AllocationSettingsRepository settingsRepository;
    private final AuditService auditService;

    public AllocationSettingsService(
            AllocationSettingsRepository settingsRepository,
            AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AllocationThresholdsResponse get() {
        AllocationSettings settings = settingsRepository.findById(SETTINGS_ID)
                .orElseThrow(() -> new IllegalStateException("Allocation settings are not initialized"));
        return toResponse(settings);
    }

    @Transactional
    public AllocationThresholdsResponse update(
            AllocationThresholdsRequest request, Authentication authentication) {
        if (request.warningPercent() >= request.shortagePercent()) {
            throw new AdminOperationException("Warning threshold must be lower than shortage threshold.");
        }

        AllocationSettings settings = settingsRepository.findById(SETTINGS_ID)
                .orElseThrow(() -> new IllegalStateException("Allocation settings are not initialized"));
        settings.setWarningPercent(request.warningPercent());
        settings.setShortagePercent(request.shortagePercent());
        AllocationSettings saved = settingsRepository.save(settings);
        auditService.record("UPDATE", "AllocationSettings", SETTINGS_ID);
        return toResponse(saved);
    }

    private AllocationThresholdsResponse toResponse(AllocationSettings settings) {
        return new AllocationThresholdsResponse(settings.getWarningPercent(), settings.getShortagePercent());
    }
}
