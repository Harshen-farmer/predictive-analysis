package com.healthcare.controller;

import com.healthcare.dto.AllocationReport;
import com.healthcare.dto.AllocationSummary;
import com.healthcare.dto.TransferSuggestion;
import com.healthcare.service.AllocationService;
import com.healthcare.service.TransferService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AllocationController {

    private final AllocationService allocationService;
    private final TransferService transferService;

    public AllocationController(AllocationService allocationService, TransferService transferService) {
        this.allocationService = allocationService;
        this.transferService = transferService;
    }

    @GetMapping("/allocation")
    public AllocationReport allocation() {
        return allocationService.allocation();
    }

    @GetMapping("/summary")
    public AllocationSummary summary() {
        return allocationService.summary();
    }

    @GetMapping("/transfers")
    public List<TransferSuggestion> transfers() {
        return allocationService.transfers();
    }

    @PutMapping("/transfers/{id}/approve")
    public TransferSuggestion approve(@PathVariable Long id, Authentication authentication) {
        return transferService.approve(id, authentication);
    }

    @PutMapping("/transfers/{id}/reject")
    public TransferSuggestion reject(@PathVariable Long id, Authentication authentication) {
        return transferService.reject(id, authentication);
    }
}
