package com.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfer_request")
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_department_id", nullable = false)
    private Department fromDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_department_id", nullable = false)
    private Department toDepartment;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private TransferResourceType resourceType;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(nullable = false, length = 255)
    private String reason;

    protected TransferRequest() {
    }

    public TransferRequest(
            Department fromDepartment,
            Department toDepartment,
            TransferResourceType resourceType,
            int quantity,
            String reason) {
        this.fromDepartment = fromDepartment;
        this.toDepartment = toDepartment;
        this.resourceType = resourceType;
        this.quantity = quantity;
        this.reason = reason;
        this.status = TransferStatus.SUGGESTED;
    }

    public Long getId() {
        return id;
    }

    public Department getFromDepartment() {
        return fromDepartment;
    }

    public Department getToDepartment() {
        return toDepartment;
    }

    public TransferResourceType getResourceType() {
        return resourceType;
    }

    public int getQuantity() {
        return quantity;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getReason() {
        return reason;
    }
}
