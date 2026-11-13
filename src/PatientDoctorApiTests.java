package com.healthcare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.repository.AuditLogRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientDoctorApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void registersAndSearchesPatientsByNameOrPhone() throws Exception {
        String phone = uniquePhone();
        mockMvc.perform(post("/api/staff/patients")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Maya Patient","age":34,"gender":"Female","phone":"%s"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maya Patient"))
                .andExpect(jsonPath("$.phone").value(phone));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getUsername()).isEqualTo("staff");
                    assertThat(audit.getAction()).isEqualTo("CREATE");
                    assertThat(audit.getEntity()).isEqualTo("Patient");
                });

        mockMvc.perform(get("/api/staff/patients").param("search", "maya patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].phone").value(phone));

        mockMvc.perform(get("/api/staff/patients").param("search", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Maya Patient"));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void rejectsInvalidPatientAndDoctorFieldsAsJson() throws Exception {
        mockMvc.perform(post("/api/staff/patients")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"","age":121,"gender":"Female","phone":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.age").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rejectsInvalidDoctorHoursAndSlotLength() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        mockMvc.perform(post("/api/admin/doctors")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Invalid Hours","departmentId":%d,
                                 "workStart":"16:00:00","workEnd":"08:00:00",
                                 "slotMinutes":61,"onDuty":true}
                                """.formatted(department.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.workHoursValid").exists())
                .andExpect(jsonPath("$.fieldErrors.slotMinutes").exists());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCanUseStaffApisButNotAdminDoctorManagement() throws Exception {
        mockMvc.perform(get("/api/staff/doctors"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/doctors")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateDoctorAndFilterStaffListByDepartment() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        String doctorBody = """
                {"name":"Test Doctor","departmentId":%d,
                 "workStart":"08:00:00","workEnd":"16:00:00",
                 "slotMinutes":20,"onDuty":true}
                """.formatted(department.getId());
        String createResponse = mockMvc.perform(post("/api/admin/doctors")
                        .with(csrf())
                        .contentType("application/json")
                        .content(doctorBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(department.getId()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long doctorId = objectMapper.readTree(createResponse).path("id").asLong();

        mockMvc.perform(get("/api/staff/doctors").param("departmentId", department.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/doctors/{id}", doctorId)
                        .with(csrf())
                        .contentType("application/json")
                        .content(doctorBody.replace("Test Doctor", "Updated Doctor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Doctor"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/doctors/{id}", doctorId)
                        .with(csrf()))
                .andExpect(status().isOk());

        for (String action : new String[]{"CREATE", "UPDATE", "DELETE"}) {
            assertThat(auditLogRepository.findAll())
                    .anySatisfy(audit -> {
                        assertThat(audit.getUsername()).isEqualTo("admin");
                        assertThat(audit.getAction()).isEqualTo(action);
                        assertThat(audit.getEntity()).isEqualTo("Doctor");
                        assertThat(audit.getEntityId()).isEqualTo(Long.toString(doctorId));
                    });
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDoctorListIncludesOnDutyAndOffDutyDoctors() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        doctorRepository.save(new Doctor(
                "Off Duty Test Doctor", department, LocalTime.of(9, 0), LocalTime.of(17, 0), 20, false));

        mockMvc.perform(get("/api/admin/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[?(@.onDuty == false)]").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotAccessAdminDoctorList() throws Exception {
        mockMvc.perform(get("/api/admin/doctors"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private String uniquePhone() {
        long value = System.nanoTime() % 1_000_000_000L;
        return "9" + String.format("%09d", value);
    }
}
