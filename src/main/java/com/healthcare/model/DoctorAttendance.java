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
@Table(name = "doctor_attendance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_doctor_attendance_doctor_date", columnNames = {"doctor_id", "attendance_date"})
})
public class DoctorAttendance extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private boolean present;

    protected DoctorAttendance() {
    }

    public DoctorAttendance(Doctor doctor, LocalDate date, boolean present) {
        this.doctor = doctor;
        this.date = date;
        this.present = present;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }
}
