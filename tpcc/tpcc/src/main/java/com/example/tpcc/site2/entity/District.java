package com.example.tpcc.site2.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "District")
public class District {

    @Id
    @Column(name = "d_id")
    private int id;

    @Column(name = "d_w_id", nullable = false)
    private int warehouseId;

    @Column(name = "d_name", nullable = false)
    private String name;

    public int getId() {
        return id;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public String getName() {
        return name;
    }
}