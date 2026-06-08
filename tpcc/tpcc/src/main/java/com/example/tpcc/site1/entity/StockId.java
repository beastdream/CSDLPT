package com.example.tpcc.site1.entity;

import java.io.Serializable;
import java.util.Objects;

public class StockId implements Serializable {
    private int s_i_id;
    private int s_w_id;

    public StockId() {
    }

    public StockId(int itemId, int warehouseId) {
        this.s_i_id = itemId;
        this.s_w_id = warehouseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StockId))
            return false;
        StockId stockId = (StockId) o;
        return s_i_id == stockId.s_i_id && s_w_id == stockId.s_w_id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(s_i_id, s_w_id);
    }
}