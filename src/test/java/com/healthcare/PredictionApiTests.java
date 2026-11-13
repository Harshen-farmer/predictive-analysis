package com.healthcare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.model.DailyLoad;
import com.healthcare.model.Department;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PredictionApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DailyLoadRepository dailyLoadRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void forecastsNormalSevenDayHistoryByWeekday() throws Exception {
        Department department = createDepartment();
        Map<DayOfWeek, Integer> counts = new EnumMap<>(DayOfWeek.class);
        for (int day = 0; day < 7; day++) {
            LocalDate date = LocalDate.now().minusDays(6L - day);
            int count = (day + 1) * 10;
            counts.put(date.getDayOfWeek(), count);
            addLoad(department, date, count);
        }

        JsonNode response = departmentForecast(department);
        assertThat(response.path("history")).hasSize(7);
        assertThat(response.path("forecast")).hasSize(7);
        for (int day = 0; day < 7; day++) {
            JsonNode forecast = response.path("forecast").get(day);
            LocalDate forecastDate = LocalDate.now().plusDays(day + 1L);
            assertThat(forecast.path("date").asText()).isEqualTo(forecastDate.toString());
            assertThat(forecast.path("predictedPatients").asInt())
                    .isEqualTo(counts.get(forecastDate.getDayOfWeek()));
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void usesAvailableShortHistoryAndFallsBackForMissingWeekdays() throws Exception {
        Department department = createDepartment();
        addLoad(department, LocalDate.now().minusDays(2), 20);
        addLoad(department, LocalDate.now(), 40);

        JsonNode response = departmentForecast(department);
        assertThat(response.path("history")).hasSize(2);
        assertThat(response.path("forecast")).hasSize(7);
        for (JsonNode forecast : response.path("forecast")) {
            DayOfWeek weekday = LocalDate.parse(forecast.path("date").asText()).getDayOfWeek();
            int expected = weekday == LocalDate.now().minusDays(2).getDayOfWeek() ? 20
                    : weekday == LocalDate.now().getDayOfWeek() ? 40
                    : 30;
            assertThat(forecast.path("predictedPatients").asInt()).isEqualTo(expected);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void zeroHistoryProducesZeroForecast() throws Exception {
        Department department = createDepartment();
        addLoad(department, LocalDate.now().minusDays(1), 0);
        addLoad(department, LocalDate.now(), 0);

        JsonNode response = departmentForecast(department);
        assertThat(response.path("forecast")).hasSize(7);
        for (JsonNode forecast : response.path("forecast")) {
            assertThat(forecast.path("predictedPatients").asInt()).isZero();
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void weekdaySpikeIsReflectedOnMatchingForecastWeekdays() throws Exception {
        Department department = createDepartment();
        DayOfWeek spikeDay = LocalDate.now().minusDays(3).getDayOfWeek();
        for (int day = 0; day < 7; day++) {
            LocalDate date = LocalDate.now().minusDays(6L - day);
            addLoad(department, date, date.getDayOfWeek() == spikeDay ? 100 : 10);
        }

        JsonNode response = departmentForecast(department);
        boolean foundSpikeDay = false;
        for (JsonNode forecast : response.path("forecast")) {
            if (LocalDate.parse(forecast.path("date").asText()).getDayOfWeek() == spikeDay) {
                assertThat(forecast.path("predictedPatients").asInt()).isEqualTo(100);
                foundSpikeDay = true;
            } else {
                assertThat(forecast.path("predictedPatients").asInt()).isEqualTo(10);
            }
        }
        assertThat(foundSpikeDay).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void unknownDepartmentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/predictions/{departmentId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department not found: " + Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void returnsOnlyTodaysLoadPerDepartmentForStaff() throws Exception {
        Department department = createDepartment();
        addLoad(department, LocalDate.now(), 37);

        MvcResult result = mockMvc.perform(get("/api/staff/load-today"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode departmentLoad = null;
        for (JsonNode item : response) {
            if (item.path("departmentId").asLong() == department.getId()) {
                departmentLoad = item;
                break;
            }
        }
        assertThat(departmentLoad).isNotNull();
        assertThat(departmentLoad.path("patientCount").asInt()).isEqualTo(37);
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotAccessAdminPredictions() throws Exception {
        mockMvc.perform(get("/api/admin/predictions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private Department createDepartment() {
        Department department = new Department(
                "Forecast Test " + System.nanoTime(), 10, 3.0, 5);
        department.setCreatedBy("test");
        return departmentRepository.save(department);
    }

    private void addLoad(Department department, LocalDate date, int count) {
        DailyLoad dailyLoad = new DailyLoad(department, date, count);
        dailyLoad.setCreatedBy("test");
        dailyLoadRepository.save(dailyLoad);
    }

    private JsonNode departmentForecast(Department department) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/predictions/{departmentId}", department.getId()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
