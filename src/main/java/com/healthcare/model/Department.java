package com.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "department")
public class Department extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "total_beds", nullable = false)
    private int totalBeds;

    @Column(name = "avg_stay_days", nullable = false)
    private double avgStayDays;

    @Column(name = "patients_per_doctor", nullable = false)
    private int patientsPerDoctor;

    protected Department() {
    }

    public Department(String name, int totalBeds, double avgStayDays, int patientsPerDoctor) {
        this.name = name;
        this.totalBeds = totalBeds;
        this.avgStayDays = avgStayDays;
        this.patientsPerDoctor = patientsPerDoctor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalBeds() {
        return totalBeds;
    }

    public void setTotalBeds(int totalBeds) {
        this.totalBeds = totalBeds;
    }

    public double getAvgStayDays() {
        return avgStayDays;
    }

    public void setAvgStayDays(double avgStayDays) {
        this.avgStayDays = avgStayDays;
    }

    public int getPatientsPerDoctor() {
        return patientsPerDoctor;
    }

    public void setPatientsPerDoctor(int patientsPerDoctor) {
        this.patientsPerDoctor = patientsPerDoctor;
    }
}
