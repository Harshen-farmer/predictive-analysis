package com.healthcare.controller;

import com.healthcare.dto.AppointmentRequest;
import com.healthcare.dto.AppointmentResponse;
import com.healthcare.dto.AppointmentStatusRequest;
import com.healthcare.model.AppointmentStatus;
import com.healthcare.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/staff/doctors/{id}/slots")
    public List<LocalTime> freeSlots(@PathVariable Long id, @RequestParam LocalDate date) {
        return appointmentService.freeSlots(id, date);
    }

    @PostMapping("/staff/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(
            @Valid @RequestBody AppointmentRequest request, Authentication authentication) {
        return appointmentService.book(request, authentication);
    }

    @PutMapping("/staff/appointments/{id}/status")
    public AppointmentResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody AppointmentStatusRequest request) {
        return appointmentService.updateStatus(id, request.status());
    }

    @GetMapping("/staff/appointments")
    public List<AppointmentResponse> search(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.search(date, departmentId, status);
    }

    @DeleteMapping("/admin/appointments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        appointmentService.delete(id);
    }
}
