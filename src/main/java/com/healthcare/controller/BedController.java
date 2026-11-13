package com.healthcare.controller;

import com.healthcare.dto.BedAllocationRequest;
import com.healthcare.dto.BedAllocationResponse;
import com.healthcare.dto.BedResponse;
import com.healthcare.dto.BedStatusRequest;
import com.healthcare.dto.DepartmentOccupancyResponse;
import com.healthcare.service.BedService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BedController {

    private final BedService bedService;

    public BedController(BedService bedService) {
        this.bedService = bedService;
    }

    @GetMapping("/staff/beds/available")
    public List<BedResponse> availableBeds(@RequestParam Long departmentId) {
        return bedService.availableBeds(departmentId);
    }

    @PostMapping("/staff/beds/allocate")
    @ResponseStatus(HttpStatus.CREATED)
    public BedAllocationResponse allocate(
            @Valid @RequestBody BedAllocationRequest request, Authentication authentication) {
        return bedService.allocate(request, authentication);
    }

    @PutMapping("/staff/beds/{allocationId}/discharge")
    public BedAllocationResponse discharge(@PathVariable Long allocationId) {
        return bedService.discharge(allocationId);
    }

    @GetMapping("/staff/beds/occupancy")
    public List<DepartmentOccupancyResponse> occupancy() {
        return bedService.occupancy();
    }

    @GetMapping("/staff/beds/allocations/active")
    public List<BedAllocationResponse> activeAllocations() {
        return bedService.activeAllocations();
    }

    @PutMapping("/admin/beds/{id}/status")
    public BedResponse updateStatus(@PathVariable Long id, @Valid @RequestBody BedStatusRequest request) {
        return bedService.updateStatus(id, request.status());
    }
}
