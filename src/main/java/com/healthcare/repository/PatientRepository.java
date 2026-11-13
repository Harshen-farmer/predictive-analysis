package com.healthcare.repository;

import com.healthcare.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    java.util.List<Patient> findByNameContainingIgnoreCaseOrPhoneContaining(String name, String phone);
}
