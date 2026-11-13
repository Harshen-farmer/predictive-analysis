package com.healthcare.service;

import com.healthcare.dto.DepartmentRequest;
import com.healthcare.dto.DepartmentResponse;
import com.healthcare.model.Bed;
import com.healthcare.model.BedStatus;
import com.healthcare.model.Department;
import com.healthcare.repository.AppointmentRepository;
import com.healthcare.repository.BedAllocationRepository;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.TransferRequestRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DepartmentManagementService {

    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final BedRepository bedRepository;
    private final BedAllocationRepository bedAllocationRepository;
    private final AppointmentRepository appointmentRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final DailyLoadRepository dailyLoadRepository;
    private final AuditService auditService;

    public DepartmentManagementService(
            DepartmentRepository departmentRepository,
            DoctorRepository doctorRepository,
            BedRepository bedRepository,
            BedAllocationRepository bedAllocationRepository,
            AppointmentRepository appointmentRepository,
            TransferRequestRepository transferRequestRepository,
            DailyLoadRepository dailyLoadRepository,
            AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.bedRepository = bedRepository;
        this.bedAllocationRepository = bedAllocationRepository;
        this.appointmentRepository = appointmentRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.dailyLoadRepository = dailyLoadRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departmentRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request, Authentication authentication) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new AdminOperationException("Department name is already in use.");
        }
        Department department = new Department(
                name, request.bedCapacity(), request.averageStay(), request.patientsPerDoctor());
        department.setCreatedBy(authentication.getName());
        Department saved = departmentRepository.save(department);
        addBeds(saved, request.bedCapacity(), authentication.getName());
        auditService.record("CREATE", "Department", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request, Authentication authentication) {
        Department department = findDepartment(id);
        String name = request.name().trim();
        departmentRepository.findByName(name).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new AdminOperationException("Department name is already in use.");
        });

        List<Bed> currentBeds = bedRepository.findByDepartment_Id(id);
        resizeBeds(department, currentBeds, request.bedCapacity(), authentication.getName());
        department.setName(name);
        department.setAvgStayDays(request.averageStay());
        department.setPatientsPerDoctor(request.patientsPerDoctor());
        Department saved = departmentRepository.save(department);
        auditService.record("UPDATE", "Department", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Department department = findDepartment(id);
        if (doctorRepository.existsByDepartment_Id(id)) {
            throw new AdminOperationException("Department cannot be deleted while it has doctors.");
        }
        if (bedAllocationRepository.existsByBed_Department_IdAndDischargeDateIsNull(id)) {
            throw new AdminOperationException("Department cannot be deleted while it has active bed allocations.");
        }
        if (bedAllocationRepository.existsByBed_Department_Id(id)) {
            throw new AdminOperationException("Department cannot be deleted while bed allocation history exists.");
        }
        if (appointmentRepository.existsByDepartment_Id(id)) {
            throw new AdminOperationException("Department cannot be deleted while appointment history exists.");
        }
        if (dailyLoadRepository.existsByDepartment_Id(id)) {
            throw new AdminOperationException("Department cannot be deleted while daily-load history exists.");
        }
        if (transferRequestRepository.existsByFromDepartment_IdOrToDepartment_Id(id, id)) {
            throw new AdminOperationException("Department cannot be deleted while transfer history exists.");
        }

        List<Bed> beds = bedRepository.findByDepartment_Id(id);
        if (beds.stream().anyMatch(bed -> bed.getStatus() == com.healthcare.model.BedStatus.OCCUPIED)) {
            throw new AdminOperationException("Department cannot be deleted while it has occupied beds.");
        }
        bedRepository.deleteAll(beds);
        departmentRepository.delete(department);
        auditService.record("DELETE", "Department", id);
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
    }

    private void resizeBeds(
            Department department, List<Bed> currentBeds, int requestedCapacity, String createdBy) {
        long allocatedBeds = currentBeds.stream()
                .filter(bed -> bedAllocationRepository.existsByBed_Id(bed.getId()))
                .count();
        if (requestedCapacity < allocatedBeds) {
            throw new AdminOperationException(
                    "Bed capacity cannot be reduced below the number of beds with allocation history.");
        }

        if (requestedCapacity < currentBeds.size()) {
            List<Bed> removable = currentBeds.stream()
                    .filter(bed -> !bedAllocationRepository.existsByBed_Id(bed.getId()))
                    .sorted((first, second) -> Boolean.compare(
                            first.getStatus() != BedStatus.AVAILABLE,
                            second.getStatus() != BedStatus.AVAILABLE))
                    .limit(currentBeds.size() - requestedCapacity)
                    .toList();
            if (removable.size() != currentBeds.size() - requestedCapacity) {
                throw new AdminOperationException("Bed capacity cannot be reduced because beds are in use.");
            }
            bedRepository.deleteAll(removable);
        } else if (requestedCapacity > currentBeds.size()) {
            addBeds(department, requestedCapacity - currentBeds.size(), createdBy);
        }
        department.setTotalBeds(requestedCapacity);
    }

    private void addBeds(Department department, int numberOfBeds, String createdBy) {
        List<Bed> beds = new java.util.ArrayList<>();
        for (int index = 0; index < numberOfBeds; index++) {
            String bedNumber = "D" + department.getId() + "-" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 16);
            Bed bed = new Bed(bedNumber, department, BedStatus.AVAILABLE);
            bed.setCreatedBy(createdBy);
            beds.add(bed);
        }
        bedRepository.saveAll(beds);
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getTotalBeds(),
                department.getAvgStayDays(),
                department.getPatientsPerDoctor());
    }
}
