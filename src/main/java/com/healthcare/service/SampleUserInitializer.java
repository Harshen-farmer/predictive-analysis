package com.healthcare.service;

import com.healthcare.model.AppUser;
import com.healthcare.model.UserRole;
import com.healthcare.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
public class SampleUserInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public SampleUserInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createUserIfMissing("admin", "admin123", UserRole.ADMIN);
        createUserIfMissing("staff", "staff123", UserRole.STAFF);
    }

    private void createUserIfMissing(String username, String password, UserRole role) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            return;
        }

        AppUser user = new AppUser(username, passwordEncoder.encode(password), role, true);
        user.setFailedAttempts(0);
        user.setCreatedBy("system");
        appUserRepository.save(user);
    }
}
