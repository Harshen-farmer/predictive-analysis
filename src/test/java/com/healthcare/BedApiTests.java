package com.healthcare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.model.Bed;
import com.healthcare.model.BedStatus;
import com.healthcare.model.Department;
import com.healthcare.model.Patient;
import com.healthcare.repository.AuditLogRepository;
import com.healthcare.repository.BedAllocationRepository;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BedApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private BedAllocationRepository bedAllocationRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private DailyLoadRepository dailyLoadRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void rejectsAllocationWhenDepartmentHasNoFreeBeds() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        List<Bed> beds = bedRepository.findByDepartment_IdAndStatusOrderById(department.getId(), BedStatus.AVAILABLE);
        beds.forEach(bed -> bed.setStatus(BedStatus.MAINTENANCE));
        bedRepository.saveAll(beds);

        mockMvc.perform(post("/api/staff/beds/allocate")
                        .with(csrf())
                        .contentType("application/json")
                        .content(allocationJson(createPatient().getId(), department.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "No available beds in department " + department.getName() + "."));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void dischargeFreesBedAndUpdatesDailyLoadAndAudit() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        Patient patient = createPatient();
        LocalDate today = LocalDate.now();
        int previousDailyLoad = dailyLoadRepository.findByDepartment_IdAndDate(department.getId(), today)
                .map(load -> load.getPatientCount())
                .orElse(0);
        JsonNode allocation = allocate(patient, department);
        long bedId = allocation.path("bedId").asLong();

        assertThat(bedRepository.findById(bedId).orElseThrow().getStatus()).isEqualTo(BedStatus.OCCUPIED);
        assertThat(dailyLoadRepository.findByDepartment_IdAndDate(department.getId(), today))
                .get()
                .extracting(load -> load.getPatientCount())
                .isEqualTo(previousDailyLoad + 1);

        mockMvc.perform(put("/api/staff/beds/{allocationId}/discharge", allocation.path("id").asLong())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dischargeDate").value(today.toString()));

        assertThat(bedRepository.findById(bedId).orElseThrow().getStatus()).isEqualTo(BedStatus.AVAILABLE);
        assertThat(bedAllocationRepository.findById(allocation.path("id").asLong()).orElseThrow()
                .getDischargeDate()).isEqualTo(today);
        assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getUsername()).isEqualTo("staff");
                    assertThat(audit.getAction()).isEqualTo("CREATE");
                    assertThat(audit.getEntity()).isEqualTo("BedAllocation");
                })
                .anySatisfy(audit -> {
                    assertThat(audit.getUsername()).isEqualTo("staff");
                    assertThat(audit.getAction()).isEqualTo("DISCHARGE");
                    assertThat(audit.getEntity()).isEqualTo("BedAllocation");
                });
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void preventsAllocatingTheSameLastBedTwice() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        List<Bed> availableBeds =
                bedRepository.findByDepartment_IdAndStatusOrderById(department.getId(), BedStatus.AVAILABLE);
        Bed onlyFreeBed = availableBeds.get(0);
        availableBeds.stream()
                .filter(bed -> !bed.getId().equals(onlyFreeBed.getId()))
                .forEach(bed -> bed.setStatus(BedStatus.MAINTENANCE));
        bedRepository.saveAll(availableBeds);

        allocate(createPatient(), department);
        mockMvc.perform(post("/api/staff/beds/allocate")
                        .with(csrf())
                        .contentType("application/json")
                        .content(allocationJson(createPatient().getId(), department.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "No available beds in department " + department.getName() + "."));

        assertThat(bedRepository.findById(onlyFreeBed.getId()).orElseThrow().getStatus())
                .isEqualTo(BedStatus.OCCUPIED);
        assertThat(bedAllocationRepository.findAll())
                .filteredOn(allocation -> allocation.getBed().getId().equals(onlyFreeBed.getId())
                        && allocation.getDischargeDate() == null)
                .hasSize(1);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void reportsLiveOccupancyAndAvailableBeds() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        long availableBefore = bedRepository
                .findByDepartment_IdAndStatusOrderById(department.getId(), BedStatus.AVAILABLE).size();
        allocate(createPatient(), department);

        MvcResult occupancyResult = mockMvc.perform(get("/api/staff/beds/occupancy"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode occupancy = objectMapper.readTree(occupancyResult.getResponse().getContentAsString());
        JsonNode departmentOccupancy = null;
        for (JsonNode item : occupancy) {
            if (item.path("departmentId").asLong() == department.getId()) {
                departmentOccupancy = item;
                break;
            }
        }
        assertThat(departmentOccupancy).isNotNull();
        assertThat(departmentOccupancy.path("occupied").asLong()).isEqualTo(1);
        assertThat(departmentOccupancy.path("available").asLong()).isEqualTo(availableBefore - 1);

        mockMvc.perform(get("/api/staff/beds/available").param("departmentId", department.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value((int) availableBefore - 1));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void listsCurrentAdmissionsForStaffDischargeWorkflow() throws Exception {
        Department department = departmentRepository.findAll().get(0);
        JsonNode allocation = allocate(createPatient(), department);

        mockMvc.perform(get("/api/staff/beds/allocations/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(allocation.path("id").asLong()))
                .andExpect(jsonPath("$[0].bedNumber").value(allocation.path("bedNumber").asText()))
                .andExpect(jsonPath("$[0].dischargeDate").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanToggleMaintenanceAndStatusChangeIsAudited() throws Exception {
        Bed bed = bedRepository.findAll().stream()
                .filter(candidate -> candidate.getStatus() == BedStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(put("/api/admin/beds/{id}/status", bed.getId())
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getUsername()).isEqualTo("admin");
                    assertThat(audit.getAction()).isEqualTo("UPDATE");
                    assertThat(audit.getEntity()).isEqualTo("Bed");
                });
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotChangeBedMaintenanceStatus() throws Exception {
        Bed bed = bedRepository.findAll().get(0);
        mockMvc.perform(put("/api/admin/beds/{id}/status", bed.getId())
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    private Patient createPatient() {
        String phone = "7" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        Patient patient = new Patient("Bed Patient", 42, "Other", phone);
        patient.setCreatedBy("test");
        return patientRepository.save(patient);
    }

    private JsonNode allocate(Patient patient, Department department) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/beds/allocate")
                        .with(csrf())
                        .contentType("application/json")
                        .content(allocationJson(patient.getId(), department.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String allocationJson(Long patientId, Long departmentId) {
        return """
                {"patientId":%d,"departmentId":%d}
                """.formatted(patientId, departmentId);
    }
}
