package com.healthcare.service;

import com.healthcare.dto.AppointmentRequest;
import com.healthcare.dto.AppointmentResponse;
import com.healthcare.model.Appointment;
import com.healthcare.model.AppointmentStatus;
import com.healthcare.model.AppointmentTokenCounter;
import com.healthcare.model.AppointmentType;
import com.healthcare.model.DailyLoad;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.Patient;
import com.healthcare.repository.AppointmentRepository;
import com.healthcare.repository.AppointmentTokenCounterRepository;
import com.healthcare.repository.DoctorAttendanceRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.PatientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Service
public class AppointmentService {

    private static final Set<AppointmentStatus> SLOT_RELEASING_STATUSES =
            Set.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentTokenCounterRepository tokenCounterRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DepartmentRepository departmentRepository;
    private final DailyLoadRepository dailyLoadRepository;
    private final AuditService auditService;
    private final DoctorAttendanceRepository attendanceRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentTokenCounterRepository tokenCounterRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DepartmentRepository departmentRepository,
            DailyLoadRepository dailyLoadRepository,
            AuditService auditService,
            DoctorAttendanceRepository attendanceRepository) {
        this.appointmentRepository = appointmentRepository;
        this.tokenCounterRepository = tokenCounterRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.departmentRepository = departmentRepository;
        this.dailyLoadRepository = dailyLoadRepository;
        this.auditService = auditService;
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public List<LocalTime> freeSlots(Long doctorId, LocalDate date) {
        validateNotPast(date);
        Doctor doctor = findDoctor(doctorId);
        ensureOnDuty(doctor, date);

        Set<LocalTime> bookedSlots = appointmentRepository.findByDoctorIdAndDate(doctorId, date).stream()
                .filter(appointment -> !SLOT_RELEASING_STATUSES.contains(appointment.getStatus()))
                .map(Appointment::getSlotTime)
                .filter(slotTime -> slotTime != null)
                .collect(java.util.stream.Collectors.toSet());

        LocalTime now = LocalTime.now();
        return generateSlots(doctor, date).stream()
                .filter(slot -> !bookedSlots.contains(slot))
                .filter(slot -> !date.equals(LocalDate.now()) || slot.isAfter(now))
                .toList();
    }

    @Transactional
    public AppointmentResponse book(AppointmentRequest request, Authentication authentication) {
        validateNotPast(request.date());

        Doctor doctor = findDoctor(request.doctorId());
        ensureOnDuty(doctor, request.date());
        Department department = departmentRepository.findByIdForUpdate(doctor.getDepartment().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found: " + doctor.getDepartment().getId()));
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.patientId()));

        LocalTime slotTime = null;
        if (request.type() == AppointmentType.WALK_IN && request.slotTime() == null) {
            if (!"General Ward".equalsIgnoreCase(department.getName())) {
                throw new AppointmentRuleException(
                        "A walk-in without a time slot can only be assigned to the General Ward.");
            }
            long assignedPatients = appointmentRepository.countByDoctor_IdAndDateAndStatusNotIn(
                    doctor.getId(), request.date(), SLOT_RELEASING_STATUSES);
            if (assignedPatients >= department.getPatientsPerDoctor()) {
                throw new AppointmentRuleException(
                        "This doctor has reached the General Ward patient limit for the day.");
            }
        } else if (request.type() == AppointmentType.WALK_IN) {
            if (!generateSlots(doctor, request.date()).contains(request.slotTime())) {
                throw new AppointmentRuleException("The selected slot is outside the doctor's working hours.");
            }
            if (request.date().equals(LocalDate.now()) && !request.slotTime().isAfter(LocalTime.now())) {
                throw new AppointmentRuleException("The selected slot is in the past.");
            }
            boolean alreadyBooked = appointmentRepository.findByDoctorIdAndDate(doctor.getId(), request.date())
                    .stream()
                    .anyMatch(existing -> request.slotTime().equals(existing.getSlotTime())
                            && !SLOT_RELEASING_STATUSES.contains(existing.getStatus()));
            if (alreadyBooked) {
                throw new AppointmentRuleException("The selected slot is already booked.");
            }
            slotTime = request.slotTime();
        }

        int tokenNumber = nextTokenNumber(department, request.date());
        Appointment appointment = new Appointment(
                patient,
                doctor,
                department,
                request.date(),
                slotTime,
                tokenNumber,
                AppointmentStatus.BOOKED,
                request.type());
        appointment.setCreatedBy(authentication.getName());
        Appointment saved = appointmentRepository.save(appointment);

        incrementDailyLoad(department, request.date());
        auditService.record("CREATE", "Appointment", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus newStatus) {
        Appointment appointment = findAppointment(id);
        if (!isValidTransition(appointment.getStatus(), newStatus)) {
            throw new AppointmentRuleException(
                    "Cannot change appointment status from " + appointment.getStatus() + " to " + newStatus + ".");
        }

        appointment.setStatus(newStatus);
        Appointment saved = appointmentRepository.save(appointment);
        auditService.record("UPDATE", "Appointment", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> search(LocalDate date, Long departmentId, AppointmentStatus status) {
        return appointmentRepository.search(date, departmentId, status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Appointment appointment = findAppointment(id);
        appointmentRepository.delete(appointment);
        auditService.record("DELETE", "Appointment", id);
    }

    private int nextTokenNumber(Department department, LocalDate date) {
        AppointmentTokenCounter counter = tokenCounterRepository
                .findByDepartment_IdAndDate(department.getId(), date)
                .orElseGet(() -> new AppointmentTokenCounter(
                        department,
                        date,
                        appointmentRepository.findMaximumTokenNumber(department.getId(), date)));
        counter.setLastToken(counter.getLastToken() + 1);
        tokenCounterRepository.save(counter);
        return counter.getLastToken();
    }

    private void incrementDailyLoad(Department department, LocalDate date) {
        DailyLoad dailyLoad = dailyLoadRepository.findByDepartment_IdAndDate(department.getId(), date)
                .orElseGet(() -> new DailyLoad(department, date, 0));
        dailyLoad.setPatientCount(dailyLoad.getPatientCount() + 1);
        dailyLoadRepository.save(dailyLoad);
    }

    private List<LocalTime> generateSlots(Doctor doctor, LocalDate date) {
        java.util.ArrayList<LocalTime> slots = new java.util.ArrayList<>();
        for (LocalTime slot = doctor.getWorkStart();
             !slot.plusMinutes(doctor.getSlotMinutes()).isAfter(doctor.getWorkEnd());
             slot = slot.plusMinutes(doctor.getSlotMinutes())) {
            slots.add(slot);
        }
        return slots;
    }

    private void validateNotPast(LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            throw new AppointmentRuleException("Appointments cannot be booked for a past date.");
        }
    }

    private void ensureOnDuty(Doctor doctor, LocalDate date) {
        if (!doctor.isOnDuty()) {
            throw new AppointmentRuleException("Doctor " + doctor.getName() + " is off duty.");
        }
        if (attendanceRepository.findByDoctor_IdAndDate(doctor.getId(), date)
                .filter(attendance -> !attendance.isPresent())
                .isPresent()) {
            throw new AppointmentRuleException("Doctor " + doctor.getName() + " is absent on " + date + ".");
        }
    }

    private Doctor findDoctor(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
    }

    private boolean isValidTransition(AppointmentStatus current, AppointmentStatus next) {
        return switch (current) {
            case BOOKED -> next == AppointmentStatus.CHECKED_IN
                    || next == AppointmentStatus.CANCELLED
                    || next == AppointmentStatus.NO_SHOW;
            case CHECKED_IN -> next == AppointmentStatus.COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getName(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getName(),
                appointment.getDepartment().getId(),
                appointment.getDate(),
                appointment.getSlotTime(),
                appointment.getTokenNumber(),
                appointment.getStatus(),
                appointment.getType());
    }
}
