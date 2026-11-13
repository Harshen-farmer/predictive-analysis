package com.healthcare.controller;

import com.healthcare.dto.DoctorAttendanceRequest;
import com.healthcare.dto.DoctorAttendanceResponse;
import com.healthcare.dto.DoctorRequest;
import com.healthcare.dto.DoctorResponse;
import com.healthcare.dto.DoctorWorkloadResponse;
import com.healthcare.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping("/staff/doctors")
    public List<DoctorResponse> onDutyDoctors(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) LocalDate date) {
        return doctorService.onDuty(departmentId, date == null ? LocalDate.now() : date);
    }

    @GetMapping("/staff/doctors/general-ward")
    public List<DoctorWorkloadResponse> generalWardWorkload(
            @RequestParam(required = false) LocalDate date) {
        return doctorService.generalWardWorkload(date == null ? LocalDate.now() : date);
    }

    @GetMapping("/admin/doctors")
    public List<DoctorResponse> allDoctors() {
        return doctorService.listAll();
    }

    @GetMapping("/admin/doctor-attendance")
    public List<DoctorAttendanceResponse> attendance(@RequestParam LocalDate date) {
        return doctorService.attendance(date);
    }

    @PutMapping("/admin/doctors/{id}/attendance")
    public DoctorAttendanceResponse updateAttendance(
            @PathVariable Long id,
            @Valid @RequestBody DoctorAttendanceRequest request,
            Authentication authentication) {
        return doctorService.updateAttendance(id, request, authentication);
    }

    @PostMapping("/admin/doctors")
    public DoctorResponse create(@Valid @RequestBody DoctorRequest request, Authentication authentication) {
        return doctorService.create(request, authentication);
    }

    @PutMapping("/admin/doctors/{id}")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return doctorService.update(id, request);
    }

    @DeleteMapping("/admin/doctors/{id}")
    public void delete(@PathVariable Long id) {
        doctorService.delete(id);
    }
}
