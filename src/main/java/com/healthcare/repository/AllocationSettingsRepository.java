package com.healthcare.repository;

import com.healthcare.model.AllocationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationSettingsRepository extends JpaRepository<AllocationSettings, Long> {
}
