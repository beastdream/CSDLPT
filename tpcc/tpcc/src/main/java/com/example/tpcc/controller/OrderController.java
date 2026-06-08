package com.example.tpcc.controller;

import com.example.tpcc.dto.OrderRequest;
import com.example.tpcc.service.NewOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final NewOrderService newOrderService;

    public OrderController(NewOrderService newOrderService) {
        this.newOrderService = newOrderService;
    }

    @PostMapping("/new")
    public ResponseEntity<String> createOrder(@RequestBody OrderRequest req) {
        String result = newOrderService.processNewOrder(
                req.getTxId(),
                req.getItemId(),
                req.getWarehouseId(),
                req.getDistrictId(),
                req.getCustomerId(),
                req.getOrderQty());

        if (result.startsWith("SUCCESS")) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/recovery/run")
    public ResponseEntity<String> runRecovery() {
        int recovered = newOrderService.recoverPendingTransactions();
        return ResponseEntity.ok("Recovered transactions: " + recovered);
    }
}