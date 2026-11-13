package com.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "allocation_settings")
public class AllocationSettings {

    @Id
    private Long id;

    @Column(name = "warning_percent", nullable = false)
    private double warningPercent;

    @Column(name = "shortage_percent", nullable = false)
    private double shortagePercent;

    protected AllocationSettings() {
    }

    public AllocationSettings(Long id, double warningPercent, double shortagePercent) {
        this.id = id;
        this.warningPercent = warningPercent;
        this.shortagePercent = shortagePercent;
    }

    public double getWarningPercent() {
        return warningPercent;
    }

    public void setWarningPercent(double warningPercent) {
        this.warningPercent = warningPercent;
    }

    public double getShortagePercent() {
        return shortagePercent;
    }

    public void setShortagePercent(double shortagePercent) {
        this.shortagePercent = shortagePercent;
    }
}
