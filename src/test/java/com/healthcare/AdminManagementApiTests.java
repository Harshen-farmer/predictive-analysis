package com.healthcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.model.Bed;
import com.healthcare.model.BedAllocation;
import com.healthcare.model.BedStatus;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.Patient;
import com.healthcare.repository.AppUserRepository;
import com.healthcare.repository.BedAllocationRepository;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

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
class AdminManagementApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BedAllocationRepository bedAllocationRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void cannotDisableOwnAccount() throws Exception {
        long adminId = appUserRepository.findByUsername("admin").orElseThrow().getId();
        mockMvc.perform(put("/api/admin/staff/{id}", adminId)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account."));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createsStaffWithBcryptAndAllowsDisableAndPasswordReset() throws Exception {
        String username = "newstaff" + System.nanoTime();
        String createResponse = mockMvc.perform(post("/api/admin/staff")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"starting-password"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(createResponse).path("id").asLong();

        assertThat(passwordEncoder.matches(
                "starting-password", appUserRepository.findById(id).orElseThrow().getPasswordHash())).isTrue();
        mockMvc.perform(put("/api/admin/staff/{id}", id)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"password\":\"changed-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        var saved = appUserRepository.findById(id).orElseThrow();
        assertThat(passwordEncoder.matches("changed-password", saved.getPasswordHash())).isTrue();
        assertThat(saved.isEnabled()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void departmentDeletionIsBlockedForDoctorsAndActiveBeds() throws Exception {
        Department withDoctor = createDepartment("Doctor Department", 0);
        Doctor doctor = new Doctor("Department Doctor", withDoctor,
                LocalTime.of(8, 0), LocalTime.of(16, 0), 20, true);
        doctor.setCreatedBy("test");
        doctorRepository.save(doctor);

        mockMvc.perform(delete("/api/admin/departments/{id}", withDoctor.getId()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Department cannot be deleted while it has doctors."));

        Department withActiveBed = createDepartment("Active Bed Department", 1);
        Bed bed = new Bed("ACTIVE-" + System.nanoTime(), withActiveBed, BedStatus.AVAILABLE);
        bed.setCreatedBy("test");
        bed = bedRepository.save(bed);
        Patient patient = createPatient();
        BedAllocation allocation = new BedAllocation(patient, bed, LocalDate.now(), "test");
        allocation.setCreatedBy("test");
        bedAllocationRepository.save(allocation);
        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        mockMvc.perform(delete("/api/admin/departments/{id}", withActiveBed.getId()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Department cannot be deleted while it has active bed allocations."));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void departmentCrudCreatesAndResizesPhysicalBedCapacity() throws Exception {
        String name = "Managed Department " + System.nanoTime();
        String created = mockMvc.perform(post("/api/admin/departments")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","bedCapacity":2,"averageStay":4.5,"patientsPerDoctor":8}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedCapacity").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(created).path("id").asLong();
        assertThat(bedRepository.findByDepartment_Id(id)).hasSize(2);

        mockMvc.perform(put("/api/admin/departments/{id}", id)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"%s Updated","bedCapacity":3,"averageStay":5.0,"patientsPerDoctor":7}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bedCapacity").value(3));
        assertThat(bedRepository.findByDepartment_Id(id)).hasSize(3);

        mockMvc.perform(delete("/api/admin/departments/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanMarkDoctorAbsentForTodayAndStaffDoctorListOmitsThem() throws Exception {
        Doctor doctor = doctorRepository.findByOnDutyTrue().stream().findFirst().orElseThrow();
        LocalDate today = LocalDate.now();

        mockMvc.perform(put("/api/admin/doctors/{id}/attendance", doctor.getId())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"date":"%s","present":false}
                                """.formatted(today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(false));

        mockMvc.perform(get("/api/admin/doctor-attendance").param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.doctorId == " + doctor.getId() + ")].present").value(false));

        mockMvc.perform(get("/api/staff/doctors")
                        .param("departmentId", doctor.getDepartment().getId().toString())
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"id\":" + doctor.getId()))));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotChangeDoctorAttendance() throws Exception {
        Doctor doctor = doctorRepository.findByOnDutyTrue().stream().findFirst().orElseThrow();
        mockMvc.perform(put("/api/admin/doctors/{id}/attendance", doctor.getId())
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"date":"%s","present":false}
                                """.formatted(LocalDate.now())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reportsCanBeDownloadedAsCsvAndAuditLogIsPaged() throws Exception {
        mockMvc.perform(get("/api/admin/reports/daily-load")
                        .param("date", LocalDate.now().toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "date,departmentId,department,patientCount")));

        mockMvc.perform(get("/api/admin/reports/bed-occupancy")
                        .param("date", LocalDate.now().toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "date,departmentId,department,occupied,available,maintenance")));

        mockMvc.perform(get("/api/admin/reports/appointments")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().plusDays(2).toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id,date,slotTime,tokenNumber,patient,doctor,department,status,type")));

        mockMvc.perform(get("/api/admin/audit-log")
                        .param("user", "admin")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void allocationThresholdsAreStoredAndValidated() throws Exception {
        mockMvc.perform(get("/api/admin/settings/allocation-thresholds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warningPercent").value(80.0))
                .andExpect(jsonPath("$.shortagePercent").value(100.0));

        mockMvc.perform(put("/api/admin/settings/allocation-thresholds")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"warningPercent\":70,\"shortagePercent\":95}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warningPercent").value(70.0))
                .andExpect(jsonPath("$.shortagePercent").value(95.0));

        mockMvc.perform(put("/api/admin/settings/allocation-thresholds")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"warningPercent\":100,\"shortagePercent\":90}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Warning threshold must be lower than shortage threshold."));
    }

    private Department createDepartment(String label, int capacity) {
        Department department = new Department(label + " " + System.nanoTime(), capacity, 3.0, 8);
        department.setCreatedBy("test");
        return departmentRepository.save(department);
    }

    private Patient createPatient() {
        String phone = "6" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        Patient patient = new Patient("Management Patient", 30, "Other", phone);
        patient.setCreatedBy("test");
        return patientRepository.save(patient);
    }
}
