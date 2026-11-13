package com.healthcare.service;

import com.healthcare.model.AppUser;
import com.healthcare.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final AppUserRepository appUserRepository;

    public LoginAttemptService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public void recordFailure(String username) {
        appUserRepository.findByUsernameForUpdate(username).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                return;
            }

            if (user.getLockedUntil() != null) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
            }

            int failedAttempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(failedAttempts);
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plusMinutes(15));
            }
            appUserRepository.save(user);
        });
    }

    @Transactional
    public void recordSuccess(String username) {
        appUserRepository.findByUsernameForUpdate(username).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            appUserRepository.save(user);
        });
    }
}
