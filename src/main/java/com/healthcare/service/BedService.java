package com.healthcare.service;

import com.healthcare.dto.BedAllocationRequest;
import com.healthcare.dto.BedAllocationResponse;
import com.healthcare.dto.BedResponse;
import com.healthcare.dto.DepartmentOccupancyResponse;
import com.healthcare.model.Bed;
import com.healthcare.model.BedAllocation;
import com.healthcare.model.BedStatus;
import com.healthcare.model.DailyLoad;
import com.healthcare.model.Department;
import com.healthcare.model.Patient;
import com.healthcare.repository.BedAllocationRepository;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.PatientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BedService {

    private final BedRepository bedRepository;
    private final BedAllocationRepository bedAllocationRepository;
    private final DepartmentRepository departmentRepository;
    private final PatientRepository patientRepository;
    private final DailyLoadRepository dailyLoadRepository;
    private final AuditService auditService;

    public BedService(
            BedRepository bedRepository,
            BedAllocationRepository bedAllocationRepository,
            DepartmentRepository departmentRepository,
            PatientRepository patientRepository,
            DailyLoadRepository dailyLoadRepository,
            AuditService auditService) {
        this.bedRepository = bedRepository;
        this.bedAllocationRepository = bedAllocationRepository;
        this.departmentRepository = departmentRepository;
        this.patientRepository = patientRepository;
        this.dailyLoadRepository = dailyLoadRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> availableBeds(Long departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department not found: " + departmentId);
        }
        return bedRepository.findByDepartment_IdAndStatusOrderById(departmentId, BedStatus.AVAILABLE).stream()
                .map(this::toBedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BedAllocationResponse> activeAllocations() {
        return bedAllocationRepository.findByDischargeDateIsNullOrderByAdmitDateDesc().stream()
                .map(this::toAllocationResponse)
                .toList();
    }

    @Transactional
    public BedAllocationResponse allocate(BedAllocationRequest request, Authentication authentication) {
        Department department = departmentRepository.findByIdForUpdate(request.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found: " + request.departmentId()));
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.patientId()));

        if (bedAllocationRepository.existsByPatient_IdAndDischargeDateIsNull(patient.getId())) {
            throw new BedRuleException("Patient already has an active bed allocation.");
        }

        Bed bed = bedRepository.findAvailableBedsForUpdate(department.getId(), BedStatus.AVAILABLE).stream()
                .findFirst()
                .orElseThrow(() -> new BedRuleException(
                        "No available beds in department " + department.getName() + "."));

        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        LocalDate today = LocalDate.now();
        BedAllocation allocation = new BedAllocation(patient, bed, today, authentication.getName());
        allocation.setCreatedBy(authentication.getName());
        BedAllocation saved = bedAllocationRepository.save(allocation);

        incrementDailyLoad(department, today);
        auditService.record("CREATE", "BedAllocation", saved.getId());
        return toAllocationResponse(saved);
    }

    @Transactional
    public BedAllocationResponse discharge(Long allocationId) {
        BedAllocation allocation = bedAllocationRepository.findByIdForUpdate(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bed allocation not found: " + allocationId));
        if (allocation.getDischargeDate() != null) {
            throw new BedRuleException("Bed allocation has already been discharged.");
        }

        Bed bed = bedRepository.findByIdForUpdate(allocation.getBed().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bed not found: " + allocation.getBed().getId()));
        allocation.setDischargeDate(LocalDate.now());
        bed.setStatus(BedStatus.AVAILABLE);
        bedRepository.save(bed);
        BedAllocation saved = bedAllocationRepository.save(allocation);
        auditService.record("DISCHARGE", "BedAllocation", saved.getId());
        return toAllocationResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DepartmentOccupancyResponse> occupancy() {
        Map<Long, long[]> totals = new LinkedHashMap<>();
        departmentRepository.findAll().forEach(department -> totals.put(department.getId(), new long[3]));
        bedRepository.findAll().forEach(bed -> {
            long[] counts = totals.get(bed.getDepartment().getId());
            if (counts == null) {
                return;
            }
            switch (bed.getStatus()) {
                case OCCUPIED -> counts[0]++;
                case AVAILABLE -> counts[1]++;
                case MAINTENANCE -> counts[2]++;
            }
        });

        List<DepartmentOccupancyResponse> occupancy = new ArrayList<>();
        departmentRepository.findAll().forEach(department -> {
            long[] counts = totals.get(department.getId());
            occupancy.add(new DepartmentOccupancyResponse(
                    department.getId(),
                    department.getName(),
                    counts[0],
                    counts[1],
                    counts[2]));
        });
        return occupancy;
    }

    @Transactional
    public BedResponse updateStatus(Long bedId, BedStatus requestedStatus) {
        if (requestedStatus == BedStatus.OCCUPIED) {
            throw new BedRuleException("Bed status OCCUPIED is managed by bed allocation.");
        }

        Bed bed = bedRepository.findByIdForUpdate(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));
        if (bed.getStatus() == BedStatus.OCCUPIED) {
            throw new BedRuleException("An occupied bed cannot be placed in maintenance or made available.");
        }
        if (bed.getStatus() != requestedStatus) {
            bed.setStatus(requestedStatus);
            bedRepository.save(bed);
            auditService.record("UPDATE", "Bed", bedId);
        }
        return toBedResponse(bed);
    }

    private void incrementDailyLoad(Department department, LocalDate date) {
        DailyLoad dailyLoad = dailyLoadRepository.findByDepartment_IdAndDate(department.getId(), date)
                .orElseGet(() -> new DailyLoad(department, date, 0));
        dailyLoad.setPatientCount(dailyLoad.getPatientCount() + 1);
        dailyLoadRepository.save(dailyLoad);
    }

    private BedResponse toBedResponse(Bed bed) {
        return new BedResponse(
                bed.getId(),
                bed.getBedNumber(),
                bed.getDepartment().getId(),
                bed.getDepartment().getName(),
                bed.getStatus());
    }

    private BedAllocationResponse toAllocationResponse(BedAllocation allocation) {
        Bed bed = allocation.getBed();
        return new BedAllocationResponse(
                allocation.getId(),
                allocation.getPatient().getId(),
                allocation.getPatient().getName(),
                bed.getId(),
                bed.getBedNumber(),
                bed.getDepartment().getId(),
                bed.getDepartment().getName(),
                allocation.getAdmitDate(),
                allocation.getDischargeDate());
    }
}
