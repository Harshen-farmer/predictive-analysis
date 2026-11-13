package com.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(name = "appointment_token_counter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointment_token_department_date",
                columnNames = {"department_id", "date"})
})
public class AppointmentTokenCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "last_token", nullable = false)
    private int lastToken;

    protected AppointmentTokenCounter() {
    }

    public AppointmentTokenCounter(Department department, LocalDate date, int lastToken) {
        this.department = department;
        this.date = date;
        this.lastToken = lastToken;
    }

    public int getLastToken() {
        return lastToken;
    }

    public void setLastToken(int lastToken) {
        this.lastToken = lastToken;
    }
}
