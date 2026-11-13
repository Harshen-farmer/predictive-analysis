package com.healthcare.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient")
public class Patient extends AuditableEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false, length = 30)
    private String gender;

    @Column(nullable = false, length = 30)
    private String phone;

    protected Patient() {
    }

    public Patient(String name, int age, String gender, String phone) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
