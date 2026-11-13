package com.healthcare.service;

import com.healthcare.dto.DoctorRequest;
import com.healthcare.dto.DoctorAttendanceRequest;
import com.healthcare.dto.DoctorAttendanceResponse;
import com.healthcare.dto.DoctorResponse;
import com.healthcare.dto.DoctorWorkloadResponse;
import com.healthcare.model.AppointmentStatus;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.DoctorAttendance;
import com.healthcare.repository.AppointmentRepository;
import com.healthcare.repository.DoctorAttendanceRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;
    private final AppointmentRepository appointmentRepository;
    private final DoctorAttendanceRepository attendanceRepository;

    public DoctorService(
            DoctorRepository doctorRepository,
            DepartmentRepository departmentRepository,
            AuditService auditService,
            AppointmentRepository appointmentRepository,
            DoctorAttendanceRepository attendanceRepository) {
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
        this.appointmentRepository = appointmentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> onDuty(Long departmentId, LocalDate date) {
        List<Doctor> doctors = departmentId == null
                ? doctorRepository.findByOnDutyTrue()
                : doctorRepository.findByDepartment_IdAndOnDutyTrue(departmentId);
        Set<Long> absentDoctorIds = attendanceRepository.findByDate(date).stream()
                .filter(attendance -> !attendance.isPresent())
                .map(attendance -> attendance.getDoctor().getId())
                .collect(Collectors.toSet());
        return doctors.stream()
                .filter(doctor -> !absentDoctorIds.contains(doctor.getId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorWorkloadResponse> generalWardWorkload(LocalDate date) {
        Department department = departmentRepository.findByNameIgnoreCase("General Ward").orElse(null);
        if (department == null) {
            return List.of();
        }
        Set<Long> absentDoctorIds = attendanceRepository.findByDate(date).stream()
                .filter(attendance -> !attendance.isPresent())
                .map(attendance -> attendance.getDoctor().getId())
                .collect(Collectors.toSet());
        return doctorRepository.findByDepartment_IdAndOnDutyTrueOrderByNameAsc(department.getId()).stream()
                .filter(doctor -> !absentDoctorIds.contains(doctor.getId()))
                .map(doctor -> new DoctorWorkloadResponse(
                        doctor.getId(),
                        doctor.getName(),
                        department.getId(),
                        department.getName(),
                        activePatientCount(doctor.getId(), date),
                        department.getPatientsPerDoctor()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorAttendanceResponse> attendance(LocalDate date) {
        Map<Long, DoctorAttendance> dailyAttendance = attendanceRepository.findByDate(date).stream()
                .collect(Collectors.toMap(
                        attendance -> attendance.getDoctor().getId(),
                        Function.identity()));
        return doctorRepository.findAllByOrderByNameAsc().stream()
                .map(doctor -> toAttendanceResponse(doctor, date, dailyAttendance.get(doctor.getId())))
                .toList();
    }

    @Transactional
    public DoctorAttendanceResponse updateAttendance(
            Long doctorId, DoctorAttendanceRequest request, Authentication authentication) {
        if (request.date().isBefore(LocalDate.now())) {
            throw new AdminOperationException("Attendance cannot be changed for a past date.");
        }
        Doctor doctor = findDoctor(doctorId);
        DoctorAttendance attendance = attendanceRepository.findByDoctor_IdAndDate(doctorId, request.date())
                .orElseGet(() -> new DoctorAttendance(doctor, request.date(), request.present()));
        attendance.setPresent(request.present());
        attendance.setCreatedBy(authentication.getName());
        DoctorAttendance saved = attendanceRepository.save(attendance);
        auditService.record("UPDATE", "DoctorAttendance", saved.getId());
        return toAttendanceResponse(doctor, request.date(), saved);
    }

    @Transactional(readOnly = true)
    public long activePatientCount(Long doctorId, LocalDate date) {
        return appointmentRepository.countByDoctor_IdAndDateAndStatusNotIn(
                doctorId, date, Set.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW));
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listAll() {
        return doctorRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public DoctorResponse create(DoctorRequest request, Authentication authentication) {
        Department department = findDepartment(request.departmentId());
        Doctor doctor = createEntity(request, department);
        doctor.setCreatedBy(authentication.getName());
        Doctor saved = doctorRepository.save(doctor);
        auditService.record("CREATE", "Doctor", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public DoctorResponse update(Long id, DoctorRequest request) {
        Doctor doctor = findDoctor(id);
        Department department = findDepartment(request.departmentId());
        updateEntity(doctor, request, department);
        Doctor saved = doctorRepository.save(doctor);
        auditService.record("UPDATE", "Doctor", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Doctor doctor = findDoctor(id);
        if (attendanceRepository.existsByDoctor_Id(id)) {
            throw new AdminOperationException("Doctor cannot be deleted while daily attendance records exist.");
        }
        doctorRepository.delete(doctor);
        auditService.record("DELETE", "Doctor", id);
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
    }

    private Doctor findDoctor(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
    }

    private DoctorAttendanceResponse toAttendanceResponse(
            Doctor doctor, LocalDate date, DoctorAttendance attendance) {
        boolean present = doctor.isOnDuty() && (attendance == null || attendance.isPresent());
        return new DoctorAttendanceResponse(
                doctor.getId(), doctor.getName(), doctor.getDepartment().getName(), date, present, doctor.isOnDuty());
    }

    private Doctor createEntity(DoctorRequest request, Department department) {
        return new Doctor(
                request.name().trim(),
                department,
                request.workStart(),
                request.workEnd(),
                request.slotMinutes(),
                request.onDuty());
    }

    private void updateEntity(Doctor doctor, DoctorRequest request, Department department) {
        doctor.setName(request.name().trim());
        doctor.setDepartment(department);
        doctor.setWorkStart(request.workStart());
        doctor.setWorkEnd(request.workEnd());
        doctor.setSlotMinutes(request.slotMinutes());
        doctor.setOnDuty(request.onDuty());
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getName(),
                doctor.getDepartment().getId(),
                doctor.getDepartment().getName(),
                doctor.getWorkStart(),
                doctor.getWorkEnd(),
                doctor.getSlotMinutes(),
                doctor.isOnDuty());
    }
}
