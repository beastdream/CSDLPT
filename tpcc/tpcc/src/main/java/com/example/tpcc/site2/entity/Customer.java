package com.example.tpcc.site2.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "Customer")
public class Customer {

    @Id
    @Column(name = "c_id")
    private int id;

    @Column(name = "c_d_id", nullable = false)
    private int districtId;

    @Column(name = "c_first", nullable = false)
    private String firstName;

    @Column(name = "c_credit", nullable = false)
    private String credit;

    public int getId() {
        return id;
    }

    public int getDistrictId() {
        return districtId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getCredit() {
        return credit;
    }
}