package com.example.tpcc.site1.repo;

import com.example.tpcc.site1.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, String> {
    Optional<StockReservation> findByTxIdAndStatus(String txId, String status);
}