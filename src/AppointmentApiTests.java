package com.healthcare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.model.AppointmentStatus;
import com.healthcare.model.AppointmentType;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.DoctorAttendance;
import com.healthcare.model.Patient;
import com.healthcare.repository.AppointmentRepository;
import com.healthcare.repository.AuditLogRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorAttendanceRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorAttendanceRepository doctorAttendanceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DailyLoadRepository dailyLoadRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void calculatesSlotsAndRemovesBookedSlot() throws Exception {
        Doctor doctor = availableDoctor();
        LocalDate date = futureDate(1);
        List<LocalTime> expectedSlots = calculateSlots(doctor);

        JsonNode initial = getSlots(doctor, date);
        assertThat(initial.size()).isEqualTo(expectedSlots.size());
        assertThat(initial.get(0).asText()).startsWith(doctor.getWorkStart().toString());

        Patient patient = createPatient();
        JsonNode booked = book(patient, doctor, date, expectedSlots.get(0), AppointmentType.WALK_IN);
        assertThat(booked.path("slotTime").asText()).startsWith(expectedSlots.get(0).toString());
        assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getUsername()).isEqualTo("staff");
                    assertThat(audit.getAction()).isEqualTo("CREATE");
                    assertThat(audit.getEntity()).isEqualTo("Appointment");
                });

        JsonNode remaining = getSlots(doctor, date);
        assertThat(remaining.size()).isEqualTo(expectedSlots.size() - 1);
        assertThat(remaining.toString()).doesNotContain(expectedSlots.get(0).toString());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void rejectsDoubleBookingWithClearError() throws Exception {
        Doctor doctor = availableDoctor();
        LocalDate date = futureDate(2);
        LocalTime slot = calculateSlots(doctor).get(0);
        book(createPatient(), doctor, date, slot, AppointmentType.WALK_IN);

        mockMvc.perform(post("/api/staff/appointments")
                        .with(csrf())
                        .contentType("application/json")
                        .content(bookingJson(createPatient().getId(), doctor.getId(), date, slot,
                                AppointmentType.WALK_IN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The selected slot is already booked."));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void generalWardQueueUsesPatientLimitAndHidesAbsentDoctors() throws Exception {
        Doctor doctor = doctorRepository.findByOnDutyTrue().stream()
                .filter(item -> item.getDepartment().getName().equalsIgnoreCase("General Ward"))
                .findFirst()
                .orElseThrow();
        Department generalWard = doctor.getDepartment();
        generalWard.setPatientsPerDoctor(1);
        departmentRepository.save(generalWard);
        LocalDate date = LocalDate.now();

        mockMvc.perform(get("/api/staff/doctors/general-ward").param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.doctorId == " + doctor.getId() + ")].patientsToday").value(0))
                .andExpect(jsonPath("$[?(@.doctorId == " + doctor.getId() + ")].patientLimit").value(1));

        JsonNode created = book(createPatient(), doctor, date, null, AppointmentType.WALK_IN);
        assertThat(created.path("slotTime").isNull()).isTrue();
        assertThat(created.path("type").asText()).isEqualTo("WALK_IN");

        mockMvc.perform(post("/api/staff/appointments")
                        .with(csrf())
                        .contentType("application/json")
                        .content(bookingJson(createPatient().getId(), doctor.getId(), date, null,
                                AppointmentType.WALK_IN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "This doctor has reached the General Ward patient limit for the day."));

        DoctorAttendance attendance = new DoctorAttendance(doctor, date, false);
        attendance.setCreatedBy("test");
        doctorAttendanceRepository.save(attendance);

        mockMvc.perform(get("/api/staff/doctors")
                        .param("departmentId", generalWard.getId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"id\":" + doctor.getId()))));
        mockMvc.perform(get("/api/staff/doctors/general-ward").param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"doctorId\":" + doctor.getId()))));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void rejectsPastAppointmentDates() throws Exception {
        Doctor doctor = availableDoctor();
        mockMvc.perform(post("/api/staff/appointments")
                        .with(csrf())
                        .contentType("application/json")
                        .content(bookingJson(
                                createPatient().getId(),
                                doctor.getId(),
                                LocalDate.now().minusDays(1),
                                calculateSlots(doctor).get(0),
                                AppointmentType.WALK_IN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Appointments cannot be booked for a past date."));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void incrementsTokensPerDepartmentAndDateAndUpdatesDailyLoad() throws Exception {
        List<Doctor> doctors = doctorRepository.findByOnDutyTrue();
        assertThat(doctors).hasSizeGreaterThanOrEqualTo(2);
        Doctor firstDoctor = doctors.get(0);
        Doctor secondDoctor = doctors.stream()
                .filter(doctor -> doctor.getDepartment().getId().equals(firstDoctor.getDepartment().getId())
                        && !doctor.getId().equals(firstDoctor.getId()))
                .findFirst()
                .orElseThrow();
        LocalDate date = futureDate(3);

        JsonNode first = book(createPatient(), firstDoctor, date, null, AppointmentType.EMERGENCY);
        JsonNode second = book(createPatient(), secondDoctor, date, null, AppointmentType.EMERGENCY);

        assertThat(first.path("tokenNumber").asInt()).isEqualTo(1);
        assertThat(first.path("slotTime").isNull()).isTrue();
        assertThat(second.path("tokenNumber").asInt()).isEqualTo(2);
        assertThat(appointmentRepository.findAll())
                .filteredOn(appointment -> appointment.getDate().equals(date))
                .hasSize(2);
        assertThat(dailyLoadRepository.findByDepartment_IdAndDate(
                firstDoctor.getDepartment().getId(), date))
                .get()
                .extracting(load -> load.getPatientCount())
                .isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void enforcesAppointmentStatusTransitions() throws Exception {
        Doctor doctor = availableDoctor();
        JsonNode created = book(
                createPatient(), doctor, futureDate(4), calculateSlots(doctor).get(0), AppointmentType.WALK_IN);
        long appointmentId = created.path("id").asLong();

        updateStatus(appointmentId, AppointmentStatus.CHECKED_IN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
        mockMvc.perform(get("/api/staff/appointments")
                        .param("date", created.path("date").asText())
                        .param("departmentId", created.path("departmentId").asText())
                        .param("status", "CHECKED_IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appointmentId));
        updateStatus(appointmentId, AppointmentStatus.COMPLETED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        updateStatus(appointmentId, AppointmentStatus.CANCELLED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Cannot change appointment status from COMPLETED to CANCELLED."));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanDeleteAppointment() throws Exception {
        Doctor doctor = availableDoctor();
        JsonNode created = book(
                createPatient(), doctor, futureDate(5), calculateSlots(doctor).get(0), AppointmentType.WALK_IN);
        long appointmentId = created.path("id").asLong();

        mockMvc.perform(delete("/api/admin/appointments/{id}", appointmentId).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(appointmentRepository.findById(appointmentId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotDeleteAppointment() throws Exception {
        Doctor doctor = availableDoctor();
        JsonNode created = book(
                createPatient(), doctor, futureDate(6), calculateSlots(doctor).get(0), AppointmentType.WALK_IN);

        mockMvc.perform(delete("/api/admin/appointments/{id}", created.path("id").asLong()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    private Doctor availableDoctor() {
        return doctorRepository.findByOnDutyTrue().stream()
                .filter(doctor -> !calculateSlots(doctor).isEmpty())
                .findFirst()
                .orElseThrow();
    }

    private List<LocalTime> calculateSlots(Doctor doctor) {
        java.util.ArrayList<LocalTime> slots = new java.util.ArrayList<>();
        for (LocalTime slot = doctor.getWorkStart();
             !slot.plusMinutes(doctor.getSlotMinutes()).isAfter(doctor.getWorkEnd());
             slot = slot.plusMinutes(doctor.getSlotMinutes())) {
            slots.add(slot);
        }
        return slots;
    }

    private LocalDate futureDate(int days) {
        return LocalDate.now().plusDays(days);
    }

    private Patient createPatient() {
        String phone = "8" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        Patient patient = new Patient("Appointment Patient", 30, "Other", phone);
        patient.setCreatedBy("test");
        return patientRepository.save(patient);
    }

    private JsonNode getSlots(Doctor doctor, LocalDate date) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/staff/doctors/{id}/slots", doctor.getId())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode book(
            Patient patient, Doctor doctor, LocalDate date, LocalTime slot, AppointmentType type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/appointments")
                        .with(csrf())
                        .contentType("application/json")
                        .content(bookingJson(patient.getId(), doctor.getId(), date, slot, type)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bookingJson(
            Long patientId, Long doctorId, LocalDate date, LocalTime slot, AppointmentType type) {
        String slotField = slot == null ? "" : ",\"slotTime\":\"" + slot + "\"";
        return """
                {"patientId":%d,"doctorId":%d,"date":"%s","type":"%s"%s}
                """.formatted(patientId, doctorId, date, type, slotField);
    }

    private org.springframework.test.web.servlet.ResultActions updateStatus(long id, AppointmentStatus status)
            throws Exception {
        return mockMvc.perform(put("/api/staff/appointments/{id}/status", id)
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"" + status.name() + "\"}"));
    }
}
