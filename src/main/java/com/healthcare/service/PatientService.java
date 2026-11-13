package com.healthcare.service;

import com.healthcare.dto.PatientRequest;
import com.healthcare.dto.PatientResponse;
import com.healthcare.model.Patient;
import com.healthcare.repository.PatientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PatientResponse register(PatientRequest request, Authentication authentication) {
        Patient patient = new Patient(
                request.name().trim(), request.age(), request.gender().trim(), request.phone());
        patient.setCreatedBy(authentication.getName());

        Patient saved = patientRepository.save(patient);
        auditService.record("CREATE", "Patient", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> search(String search) {
        String query = search == null ? "" : search.trim();
        return patientRepository.findByNameContainingIgnoreCaseOrPhoneContaining(query, query).stream()
                .map(this::toResponse)
                .toList();
    }

    private PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(), patient.getName(), patient.getAge(), patient.getGender(), patient.getPhone());
    }
}
