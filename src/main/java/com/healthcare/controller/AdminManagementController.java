package com.healthcare.controller;

import com.healthcare.dto.AllocationThresholdsRequest;
import com.healthcare.dto.AllocationThresholdsResponse;
import com.healthcare.dto.DepartmentRequest;
import com.healthcare.dto.DepartmentResponse;
import com.healthcare.dto.StaffAccountResponse;
import com.healthcare.dto.StaffCreateRequest;
import com.healthcare.dto.StaffUpdateRequest;
import com.healthcare.service.AllocationSettingsService;
import com.healthcare.service.DepartmentManagementService;
import com.healthcare.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminManagementController {

    private final StaffAccountService staffAccountService;
    private final DepartmentManagementService departmentManagementService;
    private final AllocationSettingsService allocationSettingsService;

    public AdminManagementController(
            StaffAccountService staffAccountService,
            DepartmentManagementService departmentManagementService,
            AllocationSettingsService allocationSettingsService) {
        this.staffAccountService = staffAccountService;
        this.departmentManagementService = departmentManagementService;
        this.allocationSettingsService = allocationSettingsService;
    }

    @GetMapping("/staff")
    public List<StaffAccountResponse> staffAccounts() {
        return staffAccountService.list();
    }

    @PostMapping("/staff")
    public StaffAccountResponse createStaff(
            @Valid @RequestBody StaffCreateRequest request, Authentication authentication) {
        return staffAccountService.create(request, authentication);
    }

    @PutMapping("/staff/{id}")
    public StaffAccountResponse updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffUpdateRequest request,
            Authentication authentication) {
        return staffAccountService.update(id, request, authentication);
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> departments() {
        return departmentManagementService.list();
    }

    @PostMapping("/departments")
    public DepartmentResponse createDepartment(
            @Valid @RequestBody DepartmentRequest request, Authentication authentication) {
        return departmentManagementService.create(request, authentication);
    }

    @PutMapping("/departments/{id}")
    public DepartmentResponse updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request,
            Authentication authentication) {
        return departmentManagementService.update(id, request, authentication);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/departments/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteDepartment(@PathVariable Long id) {
        departmentManagementService.delete(id);
    }

    @GetMapping("/settings/allocation-thresholds")
    public AllocationThresholdsResponse allocationThresholds() {
        return allocationSettingsService.get();
    }

    @PutMapping("/settings/allocation-thresholds")
    public AllocationThresholdsResponse updateAllocationThresholds(
            @Valid @RequestBody AllocationThresholdsRequest request, Authentication authentication) {
        return allocationSettingsService.update(request, authentication);
    }
}
