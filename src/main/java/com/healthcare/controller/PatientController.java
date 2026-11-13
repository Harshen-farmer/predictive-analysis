package com.healthcare.controller;

import com.healthcare.dto.PatientRequest;
import com.healthcare.dto.PatientResponse;
import com.healthcare.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    public PatientResponse register(@Valid @RequestBody PatientRequest request, Authentication authentication) {
        return patientService.register(request, authentication);
    }

    @GetMapping
    public List<PatientResponse> search(@RequestParam(required = false) String search) {
        return patientService.search(search);
    }
}
