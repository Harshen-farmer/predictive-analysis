package com.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "daily_load", uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_load_department_date",
                columnNames = {"department_id", "date"})
})
public class DailyLoad extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "patient_count", nullable = false)
    private int patientCount;

    protected DailyLoad() {
    }

    public DailyLoad(Department department, LocalDate date, int patientCount) {
        this.department = department;
        this.date = date;
        this.patientCount = patientCount;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getPatientCount() {
        return patientCount;
    }

    public void setPatientCount(int patientCount) {
        this.patientCount = patientCount;
    }
}
