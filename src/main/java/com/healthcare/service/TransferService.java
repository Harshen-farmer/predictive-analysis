package com.healthcare.service;

import com.healthcare.dto.TransferSuggestion;
import com.healthcare.model.Bed;
import com.healthcare.model.BedStatus;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.TransferRequest;
import com.healthcare.model.TransferResourceType;
import com.healthcare.model.TransferStatus;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.TransferRequestRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class TransferService {

    private final TransferRequestRepository transferRequestRepository;
    private final DepartmentRepository departmentRepository;
    private final BedRepository bedRepository;
    private final DoctorRepository doctorRepository;
    private final AuditService auditService;

    public TransferService(
            TransferRequestRepository transferRequestRepository,
            DepartmentRepository departmentRepository,
            BedRepository bedRepository,
            DoctorRepository doctorRepository,
            AuditService auditService) {
        this.transferRequestRepository = transferRequestRepository;
        this.departmentRepository = departmentRepository;
        this.bedRepository = bedRepository;
        this.doctorRepository = doctorRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TransferSuggestion approve(Long id, Authentication authentication) {
        TransferRequest transfer = findPendingTransfer(id);
        Department from = lockDepartment(transfer.getFromDepartment().getId());
        Department to = lockDepartment(transfer.getToDepartment().getId());

        if (transfer.getResourceType() == TransferResourceType.BED) {
            moveBeds(transfer, from, to);
        } else {
            moveDoctors(transfer, from, to);
        }

        transfer.setStatus(TransferStatus.APPROVED);
        transfer.setDecidedBy(authentication.getName());
        TransferRequest saved = transferRequestRepository.save(transfer);
        auditService.record("APPROVE", "TransferRequest", saved.getId());
        return toSuggestion(saved);
    }

    @Transactional
    public TransferSuggestion reject(Long id, Authentication authentication) {
        TransferRequest transfer = findPendingTransfer(id);
        transfer.setStatus(TransferStatus.REJECTED);
        transfer.setDecidedBy(authentication.getName());
        TransferRequest saved = transferRequestRepository.save(transfer);
        auditService.record("REJECT", "TransferRequest", saved.getId());
        return toSuggestion(saved);
    }

    private TransferRequest findPendingTransfer(Long id) {
        TransferRequest transfer = transferRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer request not found: " + id));
        if (transfer.getStatus() != TransferStatus.SUGGESTED) {
            throw new TransferRuleException("Transfer request has already been decided.");
        }
        return transfer;
    }

    private Department lockDepartment(Long id) {
        return departmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
    }

    private void moveBeds(TransferRequest transfer, Department from, Department to) {
        List<Bed> beds = bedRepository.findAvailableBedsForUpdate(from.getId(), BedStatus.AVAILABLE).stream()
                .sorted(Comparator.comparing(Bed::getId))
                .limit(transfer.getQuantity())
                .toList();
        if (beds.size() != transfer.getQuantity()) {
            throw new TransferRuleException(
                    "Transfer cannot be approved because the source department no longer has enough available beds.");
        }

        beds.forEach(bed -> bed.setDepartment(to));
        bedRepository.saveAll(beds);
        from.setTotalBeds(from.getTotalBeds() - transfer.getQuantity());
        to.setTotalBeds(to.getTotalBeds() + transfer.getQuantity());
        departmentRepository.save(from);
        departmentRepository.save(to);
    }

    private void moveDoctors(TransferRequest transfer, Department from, Department to) {
        List<Doctor> doctors = doctorRepository.findOnDutyByDepartmentForUpdate(from.getId()).stream()
                .sorted(Comparator.comparing(Doctor::getId))
                .limit(transfer.getQuantity())
                .toList();
        if (doctors.size() != transfer.getQuantity()) {
            throw new TransferRuleException(
                    "Transfer cannot be approved because the source department no longer has enough on-duty doctors.");
        }

        doctors.forEach(doctor -> doctor.setDepartment(to));
        doctorRepository.saveAll(doctors);
    }

    private TransferSuggestion toSuggestion(TransferRequest transfer) {
        return new TransferSuggestion(
                transfer.getId(),
                transfer.getFromDepartment().getId(),
                transfer.getFromDepartment().getName(),
                transfer.getToDepartment().getId(),
                transfer.getToDepartment().getName(),
                transfer.getResourceType(),
                transfer.getQuantity(),
                transfer.getReason(),
                transfer.getStatus(),
                transfer.getDecidedBy());
    }
}
