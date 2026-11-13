package com.healthcare.service;

import com.healthcare.dto.StaffAccountResponse;
import com.healthcare.dto.StaffCreateRequest;
import com.healthcare.dto.StaffUpdateRequest;
import com.healthcare.model.AppUser;
import com.healthcare.model.UserRole;
import com.healthcare.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffAccountService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public StaffAccountService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StaffAccountResponse> list() {
        return appUserRepository.findByRoleOrderByUsername(UserRole.STAFF).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StaffAccountResponse create(StaffCreateRequest request, Authentication authentication) {
        String username = request.username().trim();
        if (appUserRepository.existsByUsername(username)) {
            throw new AdminOperationException("Username is already in use.");
        }

        AppUser staff = new AppUser(username, passwordEncoder.encode(request.password()), UserRole.STAFF, true);
        staff.setCreatedBy(authentication.getName());
        AppUser saved = appUserRepository.save(staff);
        auditService.record("CREATE", "StaffAccount", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public StaffAccountResponse update(Long id, StaffUpdateRequest request, Authentication authentication) {
        AppUser staff = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff account not found: " + id));
        if (authentication.getName().equals(staff.getUsername())
                && Boolean.FALSE.equals(request.enabled())) {
            throw new AdminOperationException("You cannot disable your own account.");
        }
        if (staff.getRole() != UserRole.STAFF) {
            throw new ResourceNotFoundException("Staff account not found: " + id);
        }

        boolean changed = false;
        if (request.enabled() != null && staff.isEnabled() != request.enabled()) {
            staff.setEnabled(request.enabled());
            changed = true;
        }
        if (request.password() != null && !request.password().isBlank()) {
            staff.setPasswordHash(passwordEncoder.encode(request.password()));
            staff.setFailedAttempts(0);
            staff.setLockedUntil(null);
            changed = true;
        }

        AppUser saved = appUserRepository.save(staff);
        if (changed) {
            auditService.record("UPDATE", "StaffAccount", saved.getId());
        }
        return toResponse(saved);
    }

    private StaffAccountResponse toResponse(AppUser staff) {
        return new StaffAccountResponse(staff.getId(), staff.getUsername(), staff.getRole(), staff.isEnabled());
    }
}
