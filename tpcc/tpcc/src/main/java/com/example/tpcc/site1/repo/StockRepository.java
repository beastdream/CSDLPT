package com.example.tpcc.site1.repo;

import com.example.tpcc.site1.entity.Stock;
import com.example.tpcc.site1.entity.StockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends JpaRepository<Stock, StockId> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE Stock
            SET s_quantity = s_quantity - ?3
            WHERE s_i_id = ?1
              AND s_w_id = ?2
              AND s_quantity >= ?3
            """, nativeQuery = true)
    int deductStock(int itemId, int warehouseId, int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE Stock
            SET s_quantity = s_quantity + ?3
            WHERE s_i_id = ?1
              AND s_w_id = ?2
            """, nativeQuery = true)
    int addBackStock(int itemId, int warehouseId, int qty);

    @Query(value = """
            SELECT s_quantity
            FROM Stock
            WHERE s_i_id = ?1 AND s_w_id = ?2
            """, nativeQuery = true)
    Integer getQuantity(int itemId, int warehouseId);
}