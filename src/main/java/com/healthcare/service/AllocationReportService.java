package com.healthcare.service;

import com.healthcare.dto.AppointmentReportRow;
import com.healthcare.dto.BedOccupancyReportRow;
import com.healthcare.dto.DailyLoadReportRow;
import com.healthcare.model.Appointment;
import com.healthcare.model.Bed;
import com.healthcare.model.BedAllocation;
import com.healthcare.model.BedStatus;
import com.healthcare.model.DailyLoad;
import com.healthcare.model.Department;
import com.healthcare.repository.AppointmentRepository;
import com.healthcare.repository.BedAllocationRepository;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AllocationReportService {

    private final DailyLoadRepository dailyLoadRepository;
    private final BedRepository bedRepository;
    private final BedAllocationRepository bedAllocationRepository;
    private final AppointmentRepository appointmentRepository;
    private final DepartmentRepository departmentRepository;

    public AllocationReportService(
            DailyLoadRepository dailyLoadRepository,
            BedRepository bedRepository,
            BedAllocationRepository bedAllocationRepository,
            AppointmentRepository appointmentRepository,
            DepartmentRepository departmentRepository) {
        this.dailyLoadRepository = dailyLoadRepository;
        this.bedRepository = bedRepository;
        this.bedAllocationRepository = bedAllocationRepository;
        this.appointmentRepository = appointmentRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DailyLoadReportRow> dailyLoad(LocalDate startDate, LocalDate endDate) {
        return dailyLoadRepository.findByDateBetweenOrderByDateAscDepartment_NameAsc(startDate, endDate).stream()
                .map(load -> new DailyLoadReportRow(
                        load.getDate(),
                        load.getDepartment().getId(),
                        load.getDepartment().getName(),
                        load.getPatientCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BedOccupancyReportRow> bedOccupancy(LocalDate startDate, LocalDate endDate) {
        List<Department> departments = departmentRepository.findAll();
        List<BedAllocation> allocations = bedAllocationRepository.findAllocationsDuring(startDate, endDate);
        Map<Long, Map<LocalDate, Long>> occupiedCounts = new HashMap<>();
        for (Department department : departments) {
            Map<LocalDate, Long> counts = new HashMap<>();
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                counts.put(date, 0L);
            }
            occupiedCounts.put(department.getId(), counts);
        }

        for (BedAllocation allocation : allocations) {
            Long departmentId = allocation.getBed().getDepartment().getId();
            LocalDate lastOccupiedDate = allocation.getDischargeDate() == null
                    ? endDate
                    : allocation.getDischargeDate();
            LocalDate firstDate = allocation.getAdmitDate().isBefore(startDate) ? startDate : allocation.getAdmitDate();
            if (firstDate.isAfter(lastOccupiedDate)) {
                continue;
            }
            Map<LocalDate, Long> counts = occupiedCounts.get(departmentId);
            if (counts == null) {
                continue;
            }
            for (LocalDate date = firstDate; !date.isAfter(lastOccupiedDate); date = date.plusDays(1)) {
                counts.computeIfPresent(date, (ignored, count) -> count + 1);
            }
        }

        List<BedOccupancyReportRow> rows = new ArrayList<>();
        for (Department department : departments) {
            List<Bed> beds = bedRepository.findByDepartment_Id(department.getId());
            long maintenance = beds.stream().filter(bed -> bed.getStatus() == BedStatus.MAINTENANCE).count();
            long totalCapacity = beds.size();
            for (Map.Entry<LocalDate, Long> entry : occupiedCounts.get(department.getId()).entrySet()) {
                long occupied = entry.getValue();
                rows.add(new BedOccupancyReportRow(
                        entry.getKey(),
                        department.getId(),
                        department.getName(),
                        occupied,
                        Math.max(0, totalCapacity - maintenance - occupied),
                        maintenance));
            }
        }
        return rows.stream()
                .sorted(java.util.Comparator.comparing(BedOccupancyReportRow::date)
                        .thenComparing(BedOccupancyReportRow::department))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentReportRow> appointments(LocalDate startDate, LocalDate endDate) {
        return appointmentRepository.findByDateBetweenOrderByDateAscTokenNumberAsc(startDate, endDate).stream()
                .map(this::toAppointmentRow)
                .toList();
    }

    private AppointmentReportRow toAppointmentRow(Appointment appointment) {
        return new AppointmentReportRow(
                appointment.getId(),
                appointment.getDate(),
                appointment.getSlotTime(),
                appointment.getTokenNumber(),
                appointment.getPatient().getName(),
                appointment.getDoctor().getName(),
                appointment.getDepartment().getName(),
                appointment.getStatus(),
                appointment.getType());
    }
}
