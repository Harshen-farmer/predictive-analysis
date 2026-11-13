package com.healthcare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.model.Bed;
import com.healthcare.model.BedStatus;
import com.healthcare.model.DailyLoad;
import com.healthcare.model.Department;
import com.healthcare.model.Doctor;
import com.healthcare.model.TransferRequest;
import com.healthcare.model.TransferResourceType;
import com.healthcare.model.TransferStatus;
import com.healthcare.repository.AuditLogRepository;
import com.healthcare.repository.AllocationSettingsRepository;
import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import com.healthcare.repository.TransferRequestRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AllocationApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DailyLoadRepository dailyLoadRepository;

    @Autowired
    private TransferRequestRepository transferRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AllocationSettingsRepository allocationSettingsRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void classifiesOkWarningAndShortageDepartments() throws Exception {
        DepartmentAllocationFixture ok = createDepartment("OK", 50, 0.1, 10, 20, 20);
        DepartmentAllocationFixture warning = createDepartment("Warning", 40, 1.0, 10, 50, 5);
        DepartmentAllocationFixture shortage = createDepartment("Shortage", 50, 10.0, 10, 100, 2);

        JsonNode allocations = getAllocation();
        assertThat(findAllocation(allocations, ok.department().getId()).path("status").asText())
                .isEqualTo("OK");
        assertThat(findAllocation(allocations, warning.department().getId()).path("status").asText())
                .isEqualTo("WARNING");
        assertThat(findAllocation(allocations, shortage.department().getId()).path("status").asText())
                .isEqualTo("SHORTAGE");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void suggestsBedAndDoctorTransfersFromSurplusToShortageDepartments() throws Exception {
        DepartmentAllocationFixture donor = createDepartment("Donor", 0, 1, 10, 10, 2);
        DepartmentAllocationFixture recipient = createDepartment("Recipient", 100, 1000, 1, 100, 1);

        JsonNode transfers = getTransfers();
        JsonNode bedTransfer = findTransfer(
                transfers, TransferResourceType.BED, donor.department().getId(), recipient.department().getId());
        JsonNode doctorTransfer = findTransfer(
                transfers, TransferResourceType.DOCTOR, donor.department().getId(), recipient.department().getId());
        assertThat(bedTransfer.path("fromDepartmentId").asLong()).isEqualTo(donor.department().getId());
        assertThat(bedTransfer.path("toDepartmentId").asLong()).isEqualTo(recipient.department().getId());
        assertThat(bedTransfer.path("quantity").asInt()).isEqualTo(10);
        assertThat(bedTransfer.path("reason").asText()).isNotBlank();
        assertThat(doctorTransfer.path("quantity").asInt()).isEqualTo(2);
        assertThat(doctorTransfer.path("status").asText()).isEqualTo("SUGGESTED");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void approvingTransfersMovesBedsAndDoctorsAndAuditsTheDecision() throws Exception {
        DepartmentAllocationFixture donor = createDepartment("Approval Donor", 0, 1, 10, 10, 2);
        DepartmentAllocationFixture recipient =
                createDepartment("Approval Recipient", 100, 1000, 1, 100, 1);
        JsonNode transfers = getTransfers();
        JsonNode bedTransfer = findTransfer(
                transfers, TransferResourceType.BED, donor.department().getId(), recipient.department().getId());
        JsonNode doctorTransfer = findTransfer(
                transfers, TransferResourceType.DOCTOR, donor.department().getId(), recipient.department().getId());

        mockMvc.perform(put("/api/admin/transfers/{id}/approve", bedTransfer.path("id").asLong())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("admin"));
        mockMvc.perform(put("/api/admin/transfers/{id}/approve", doctorTransfer.path("id").asLong())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(bedRepository.findByDepartment_IdAndStatusOrderById(
                donor.department().getId(), BedStatus.AVAILABLE)).isEmpty();
        assertThat(bedRepository.countByDepartment_IdAndStatus(
                recipient.department().getId(), BedStatus.AVAILABLE)).isEqualTo(110);
        assertThat(departmentRepository.findById(donor.department().getId()).orElseThrow().getTotalBeds())
                .isZero();
        assertThat(departmentRepository.findById(recipient.department().getId()).orElseThrow().getTotalBeds())
                .isEqualTo(110);
        assertThat(doctorRepository.countByDepartment_IdAndOnDutyTrue(donor.department().getId())).isZero();
        assertThat(doctorRepository.countByDepartment_IdAndOnDutyTrue(recipient.department().getId())).isEqualTo(3);
        assertThat(transferRequestRepository.findById(bedTransfer.path("id").asLong()).orElseThrow().getStatus())
                .isEqualTo(TransferStatus.APPROVED);
        assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getUsername()).isEqualTo("admin");
                    assertThat(audit.getAction()).isEqualTo("APPROVE");
                    assertThat(audit.getEntity()).isEqualTo("TransferRequest");
                });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void rejectsTransferAndRecordsTheDecision() throws Exception {
        DepartmentAllocationFixture donor = createDepartment("Reject Donor", 0, 1, 10, 10, 2);
        DepartmentAllocationFixture recipient = createDepartment("Reject Recipient", 100, 1000, 1, 100, 1);
        JsonNode transfer = findTransfer(
                getTransfers(), TransferResourceType.BED, donor.department().getId(), recipient.department().getId());

        mockMvc.perform(put("/api/admin/transfers/{id}/reject", transfer.path("id").asLong()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedBy").value("admin"));

        TransferRequest rejected = transferRequestRepository.findById(transfer.path("id").asLong()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(auditLogRepository.findAll())
                .anySatisfy(audit -> {
                    assertThat(audit.getAction()).isEqualTo("REJECT");
                    assertThat(audit.getEntity()).isEqualTo("TransferRequest");
                });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void allocationStatusUsesConfiguredThresholds() throws Exception {
        DepartmentAllocationFixture department =
                createDepartment("Configured Threshold", 40, 1.0, 10, 50, 5);
        JsonNode before = findAllocation(getAllocation(), department.department().getId());
        assertThat(before.path("status").asText()).isEqualTo("WARNING");

        var settings = allocationSettingsRepository.findById(1L).orElseThrow();
        settings.setWarningPercent(90);
        settings.setShortagePercent(110);
        allocationSettingsRepository.save(settings);

        JsonNode after = findAllocation(getAllocation(), department.department().getId());
        assertThat(after.path("status").asText()).isEqualTo("OK");
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotAccessAllocationOrTransferDecisions() throws Exception {
        mockMvc.perform(get("/api/admin/allocation"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/admin/transfers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private DepartmentAllocationFixture createDepartment(
            String name,
            int patientCount,
            double avgStay,
            int patientsPerDoctor,
            int bedCount,
            int doctorCount) {
        Department department = new Department(name + " " + System.nanoTime(), bedCount, avgStay, patientsPerDoctor);
        department.setCreatedBy("test");
        department = departmentRepository.save(department);

        List<Bed> beds = new ArrayList<>();
        for (int index = 0; index < bedCount; index++) {
            Bed bed = new Bed("B" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                    department, BedStatus.AVAILABLE);
            bed.setCreatedBy("test");
            beds.add(bed);
        }
        bedRepository.saveAll(beds);

        List<Doctor> doctors = new ArrayList<>();
        for (int index = 0; index < doctorCount; index++) {
            Doctor doctor = new Doctor(name + " Doctor " + index, department,
                    LocalTime.of(8, 0), LocalTime.of(16, 0), 20, true);
            doctor.setCreatedBy("test");
            doctors.add(doctor);
        }
        doctorRepository.saveAll(doctors);

        if (patientCount > 0) {
            for (int day = 0; day < 7; day++) {
                DailyLoad load = new DailyLoad(department, LocalDate.now().minusDays(6L - day), patientCount);
                load.setCreatedBy("test");
                dailyLoadRepository.save(load);
            }
        }
        return new DepartmentAllocationFixture(department);
    }

    private JsonNode getAllocation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/allocation"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("departments");
    }

    private JsonNode getTransfers() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/transfers"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findAllocation(JsonNode allocations, Long departmentId) {
        for (JsonNode allocation : allocations) {
            if (allocation.path("departmentId").asLong() == departmentId) {
                return allocation;
            }
        }
        throw new AssertionError("Department allocation not found: " + departmentId);
    }

    private JsonNode findTransfer(JsonNode transfers, TransferResourceType resourceType) {
        for (JsonNode transfer : transfers) {
            if (transfer.path("resourceType").asText().equals(resourceType.name())
                    && transfer.path("status").asText().equals("SUGGESTED")) {
                return transfer;
            }
        }
        throw new AssertionError("Transfer not found for resource: " + resourceType);
    }

    private JsonNode findTransfer(
            JsonNode transfers, TransferResourceType resourceType, Long fromDepartmentId, Long toDepartmentId) {
        for (JsonNode transfer : transfers) {
            if (transfer.path("resourceType").asText().equals(resourceType.name())
                    && transfer.path("fromDepartmentId").asLong() == fromDepartmentId
                    && transfer.path("toDepartmentId").asLong() == toDepartmentId
                    && transfer.path("status").asText().equals("SUGGESTED")) {
                return transfer;
            }
        }
        throw new AssertionError("Transfer not found: " + resourceType + " from " + fromDepartmentId
                + " to " + toDepartmentId);
    }

    private record DepartmentAllocationFixture(Department department) {
    }
}
