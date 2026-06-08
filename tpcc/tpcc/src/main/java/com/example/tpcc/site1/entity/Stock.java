package com.example.tpcc.site1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(StockId.class)
@Table(name = "Stock")
public class Stock {
    @Id
    private int s_i_id;

    @Id
    private int s_w_id;

    private int s_quantity;

    public int getS_i_id() {
        return s_i_id;
    }

    public void setS_i_id(int s_i_id) {
        this.s_i_id = s_i_id;
    }

    public int getS_w_id() {
        return s_w_id;
    }

    public void setS_w_id(int s_w_id) {
        this.s_w_id = s_w_id;
    }

    public int getS_quantity() {
        return s_quantity;
    }

    public void setS_quantity(int s_quantity) {
        this.s_quantity = s_quantity;
    }
}