package com.healthcare;

import com.healthcare.repository.BedRepository;
import com.healthcare.repository.DailyLoadRepository;
import com.healthcare.repository.DepartmentRepository;
import com.healthcare.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HealthcareApplicationTests {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private DailyLoadRepository dailyLoadRepository;

    @Test
    void sampleDataIsSeeded() {
        assertThat(departmentRepository.count()).isEqualTo(6L);
        assertThat(doctorRepository.count()).isBetween(18L, 24L);
        assertThat(bedRepository.count()).isEqualTo(
                departmentRepository.findAll().stream()
                        .mapToLong(department -> department.getTotalBeds())
                        .sum());
        assertThat(dailyLoadRepository.count()).isEqualTo(540L);
        assertThat(dailyLoadRepository.findAll().stream()
                .map(load -> load.getPatientCount())
                .distinct()
                .count())
                .isGreaterThan(1L);
    }
}
