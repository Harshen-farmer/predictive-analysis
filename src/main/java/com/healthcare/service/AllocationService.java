package com.healthcare.service;

import com.healthcare.dto.AllocationSummary;
import com.healthcare.dto.AllocationReport;
import com.healthcare.dto.DepartmentAllocation;
import com.healthcare.dto.DepartmentForecast;
import com.healthcare.dto.TransferSuggestion;
import com.healthcare.model.BedStatus;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.TransferRequest;
import com.healthcare.model.TransferResourceType;
import com.healthcare.model.TransferStatus;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.AllocationSettingsRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.TransferRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AllocationService {

    private final PredictionService predictionService;
    private final DepartmentRepository departmentRepository;
    private final BedRepository bedRepository;
    private final DoctorRepository doctorRepository;
    private final DailyLoadRepository dailyLoadRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final AllocationSettingsRepository allocationSettingsRepository;

    public AllocationService(
            PredictionService predictionService,
            DepartmentRepository departmentRepository,
            BedRepository bedRepository,
            DoctorRepository doctorRepository,
            DailyLoadRepository dailyLoadRepository,
            TransferRequestRepository transferRequestRepository,
            AllocationSettingsRepository allocationSettingsRepository) {
        this.predictionService = predictionService;
        this.departmentRepository = departmentRepository;
        this.bedRepository = bedRepository;
        this.doctorRepository = doctorRepository;
        this.dailyLoadRepository = dailyLoadRepository;
        this.transferRequestRepository = transferRequestRepository;
        this.allocationSettingsRepository = allocationSettingsRepository;
    }

    @Transactional
    public AllocationReport allocation() {
        List<DepartmentAllocation> allocations = calculateAllocations();
        ensureSuggestions(allocations);
        List<TransferSuggestion> suggestions = transferRequestRepository
                .findAllByOrderByIdDesc().stream()
                .filter(transfer -> transfer.getStatus() == TransferStatus.SUGGESTED)
                .map(this::toSuggestion)
                .toList();
        return new AllocationReport(allocations, suggestions);
    }

    @Transactional(readOnly = true)
    public AllocationSummary summary() {
        List<DepartmentAllocation> allocations = calculateAllocations();
        return new AllocationSummary(
                dailyLoadRepository.sumPatientCountByDate(LocalDate.now()),
                bedRepository.countByStatus(BedStatus.OCCUPIED),
                doctorRepository.countByOnDutyTrue(),
                allocations.stream().filter(allocation -> allocation.status().equals("SHORTAGE")).count());
    }

    @Transactional
    public List<TransferSuggestion> transfers() {
        ensureSuggestions(calculateAllocations());
        return transferRequestRepository.findAllByOrderByIdDesc().stream()
                .map(this::toSuggestion)
                .toList();
    }

    private List<DepartmentAllocation> calculateAllocations() {
        Map<Long, DepartmentForecast> forecasts = predictionService.forecastAll().stream()
                .collect(Collectors.toMap(DepartmentForecast::departmentId, Function.identity()));

        return departmentRepository.findAll().stream()
                .map(department -> calculateDepartment(department, forecasts.get(department.getId())))
                .toList();
    }

    private DepartmentAllocation calculateDepartment(Department department, DepartmentForecast forecast) {
        // Use the rounded mean of the next seven daily forecasts as expected daily patient demand.
        int predictedPatients = forecast == null || forecast.forecast().isEmpty()
                ? 0
                : (int) Math.round(forecast.forecast().stream()
                        .mapToInt(day -> day.predictedPatients())
                        .average()
                        .orElse(0.0));

        double bedsNeeded = predictedPatients * department.getAvgStayDays();
        long bedsAvailable = bedRepository.countByDepartment_IdAndStatus(
                department.getId(), BedStatus.AVAILABLE);
        long doctorsAvailable = doctorRepository.countByDepartment_IdAndOnDutyTrue(department.getId());
        int doctorsNeeded = department.getPatientsPerDoctor() <= 0
                ? (predictedPatients == 0 ? 0 : Integer.MAX_VALUE)
                : (int) Math.ceil((double) predictedPatients / department.getPatientsPerDoctor());
        // Utilization compares projected need with beds free now and doctors currently on duty.
        double bedsUtilization = utilization(bedsNeeded, bedsAvailable);
        double doctorsUtilization = utilization(doctorsNeeded, doctorsAvailable);
        var settings = allocationSettingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Allocation settings are not initialized"));
        String status = worseStatus(
                statusFor(bedsUtilization * 100.0, settings.getWarningPercent(), settings.getShortagePercent()),
                statusFor(doctorsUtilization * 100.0, settings.getWarningPercent(), settings.getShortagePercent()));

        return new DepartmentAllocation(
                department.getId(),
                department.getName(),
                predictedPatients,
                bedsNeeded,
                bedsAvailable,
                bedsUtilization,
                doctorsNeeded,
                doctorsAvailable,
                doctorsUtilization,
                status);
    }

    private void ensureSuggestions(List<DepartmentAllocation> allocations) {
        if (transferRequestRepository.existsByStatus(TransferStatus.SUGGESTED)) {
            return;
        }

        Map<Long, Department> departments = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
        for (TransferResourceType resourceType : TransferResourceType.values()) {
            List<ResourceBalance> surpluses = allocations.stream()
                    .map(allocation -> resourceBalance(allocation, resourceType))
                    .filter(balance -> balance.surplus() > 0)
                    .sorted(Comparator.comparingInt(ResourceBalance::surplus).reversed()
                            .thenComparingLong(ResourceBalance::departmentId))
                    .collect(Collectors.toCollection(ArrayList::new));
            List<ResourceBalance> shortages = allocations.stream()
                    .map(allocation -> resourceBalance(allocation, resourceType))
                    .filter(balance -> balance.shortage() > 0)
                    .sorted(Comparator.comparingInt(ResourceBalance::shortage).reversed()
                            .thenComparingLong(ResourceBalance::departmentId))
                    .collect(Collectors.toCollection(ArrayList::new));

            int surplusIndex = 0;
            int shortageIndex = 0;
            while (surplusIndex < surpluses.size() && shortageIndex < shortages.size()) {
                ResourceBalance surplus = surpluses.get(surplusIndex);
                ResourceBalance shortage = shortages.get(shortageIndex);
                int quantity = Math.min(surplus.surplus(), shortage.shortage());
                if (quantity > 0 && !surplus.departmentId().equals(shortage.departmentId())) {
                    Department from = departments.get(surplus.departmentId());
                    Department to = departments.get(shortage.departmentId());
                    String reason = "%s surplus in %s can help %s cover its projected shortage."
                            .formatted(resourceType.name().toLowerCase(), from.getName(), to.getName());
                    transferRequestRepository.save(
                            new TransferRequest(from, to, resourceType, quantity, reason));
                }

                surpluses.set(surplusIndex, surplus.withSurplus(surplus.surplus() - quantity));
                shortages.set(shortageIndex, shortage.withShortage(shortage.shortage() - quantity));
                if (surpluses.get(surplusIndex).surplus() == 0) {
                    surplusIndex++;
                }
                if (shortages.get(shortageIndex).shortage() == 0) {
                    shortageIndex++;
                }
            }
        }
    }

    private ResourceBalance resourceBalance(DepartmentAllocation allocation, TransferResourceType resourceType) {
        if (resourceType == TransferResourceType.BED) {
            int available = (int) Math.min(Integer.MAX_VALUE, allocation.bedsAvailable());
            int needed = (int) Math.min(Integer.MAX_VALUE, Math.ceil(allocation.bedsNeeded()));
            return new ResourceBalance(allocation.departmentId(), Math.max(0, available - needed),
                    Math.max(0, needed - available));
        }

        int available = (int) Math.min(Integer.MAX_VALUE, allocation.doctorsAvailable());
        int needed = allocation.doctorsNeeded();
        return new ResourceBalance(allocation.departmentId(), Math.max(0, available - needed),
                Math.max(0, needed - available));
    }

    private double utilization(double needed, long available) {
        if (needed <= 0) {
            return 0.0;
        }
        return available <= 0 ? Double.POSITIVE_INFINITY : needed / available;
    }

    private String statusFor(double utilizationPercent, double warningPercent, double shortagePercent) {
        if (utilizationPercent < warningPercent) {
            return "OK";
        }
        if (utilizationPercent <= shortagePercent) {
            return "WARNING";
        }
        return "SHORTAGE";
    }

    private String worseStatus(String first, String second) {
        if (first.equals("SHORTAGE") || second.equals("SHORTAGE")) {
            return "SHORTAGE";
        }
        if (first.equals("WARNING") || second.equals("WARNING")) {
            return "WARNING";
        }
        return "OK";
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

    private record ResourceBalance(Long departmentId, int surplus, int shortage) {
        ResourceBalance withSurplus(int remaining) {
            return new ResourceBalance(departmentId, remaining, shortage);
        }

        ResourceBalance withShortage(int remaining) {
            return new ResourceBalance(departmentId, surplus, remaining);
        }
    }
}
