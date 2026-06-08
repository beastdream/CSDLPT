package com.example.tpcc.service;

import com.example.tpcc.site1.entity.StockReservation;
import com.example.tpcc.site1.repo.StockRepository;
import com.example.tpcc.site1.repo.StockReservationRepository;
import com.example.tpcc.site2.entity.GlobalTransactionLog;
import com.example.tpcc.site2.entity.Orders;
import com.example.tpcc.site2.repo.CustomerRepository;
import com.example.tpcc.site2.repo.DistrictRepository;
import com.example.tpcc.site2.repo.GlobalTransactionLogRepository;
import com.example.tpcc.site2.repo.OrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NewOrderService {

    private static final String ORDER_CREATED = "ORDER_CREATED";
    private static final String PENDING = "PENDING";
    private static final String STOCK_RESERVED = "STOCK_RESERVED";
    private static final String COMPLETED = "COMPLETED";
    private static final String COMPENSATING = "COMPENSATING";
    private static final String COMPENSATED = "COMPENSATED";
    private static final String FAILED = "FAILED";

    private static final String RES_RESERVED = "RESERVED";
    private static final String RES_CONFIRMED = "CONFIRMED";
    private static final String RES_COMPENSATED = "COMPENSATED";

    private final StockRepository stockRepo;
    private final StockReservationRepository reservationRepo;

    private final OrderRepository orderRepo;
    private final CustomerRepository customerRepo;
    private final DistrictRepository districtRepo;
    private final GlobalTransactionLogRepository txLogRepo;

    private final TransactionTemplate site1Tx;
    private final TransactionTemplate site2Tx;

    public NewOrderService(
            StockRepository stockRepo,
            StockReservationRepository reservationRepo,
            OrderRepository orderRepo,
            CustomerRepository customerRepo,
            DistrictRepository districtRepo,
            GlobalTransactionLogRepository txLogRepo,
            @Qualifier("site1TransactionManager") PlatformTransactionManager site1TransactionManager,
            @Qualifier("site2TransactionManager") PlatformTransactionManager site2TransactionManager) {
        this.stockRepo = stockRepo;
        this.reservationRepo = reservationRepo;
        this.orderRepo = orderRepo;
        this.customerRepo = customerRepo;
        this.districtRepo = districtRepo;
        this.txLogRepo = txLogRepo;

        this.site1Tx = new TransactionTemplate(site1TransactionManager);
        this.site2Tx = new TransactionTemplate(site2TransactionManager);
    }

    public String processNewOrder(
            String txId,
            int itemId,
            int warehouseId,
            int districtId,
            int customerId,
            int orderQty) {
        String finalTxId = normalizeTxId(txId);

        if (itemId <= 0 || warehouseId <= 0 || districtId <= 0 || customerId <= 0 || orderQty <= 0) {
            return "FAILED: invalid input";
        }

        Optional<GlobalTransactionLog> existing = txLogRepo.findById(finalTxId);
        if (existing.isPresent()) {
            String status = existing.get().getStatus();

            if (COMPLETED.equals(status)) {
                return "SUCCESS: duplicate request ignored, tx already completed. txId=" + finalTxId;
            }

            if (COMPENSATED.equals(status) || FAILED.equals(status)) {
                return "FAILED: duplicate request ignored, previous tx status=" + status + ", txId=" + finalTxId;
            }

            return "FAILED: transaction is already processing. status=" + status + ", txId=" + finalTxId;
        }

        try {
            createOrKeepGlobalLog(finalTxId, itemId, warehouseId, districtId, customerId, orderQty);

            try {
                validateCustomerAndDistrict(customerId, districtId, warehouseId);
            } catch (Exception validationException) {
                markTxFailed(finalTxId, validationException.getMessage());
                return "FAILED: validation error. txId=" + finalTxId
                        + ". Error=" + validationException.getMessage();
            }

            boolean stockReserved = reserveStockWithRetry(finalTxId, itemId, warehouseId, orderQty);
            if (!stockReserved) {
                markTxFailed(finalTxId, "Insufficient stock or invalid item/warehouse");
                return "FAILED: insufficient stock or invalid item/warehouse. txId=" + finalTxId;
            }

            try {
                markTxStatus(finalTxId, STOCK_RESERVED, null);
            } catch (Exception markException) {
                try {
                    compensateStock(finalTxId);
                } catch (Exception compensationException) {
                    return "FAILED: stock reserved but global log update and compensation failed. txId="
                            + finalTxId
                            + ". Run recovery manually.";
                }

                return "FAILED: global log update failed; stock compensated. txId=" + finalTxId;
            }

            try {
                createOrderAndCompleteTx(finalTxId, districtId, customerId);
            } catch (Exception site2Exception) {
                markTxStatus(finalTxId, COMPENSATING, site2Exception.getMessage());
                compensateStock(finalTxId);
                markTxStatus(finalTxId, COMPENSATED, "Site 2 failed, stock compensated");

                return "FAILED: Site 2 error; stock compensated. txId=" + finalTxId
                        + ". Error=" + site2Exception.getMessage();
            }

            try {
                confirmReservation(finalTxId);
                markTxStatus(finalTxId, COMPLETED, null);
            } catch (Exception confirmException) {
                return "SUCCESS: order created but reservation confirmation pending. txId="
                        + finalTxId
                        + ". Run recovery later. Error=" + confirmException.getMessage();
            }

            return "SUCCESS: distributed transaction completed. txId=" + finalTxId;

        } catch (Exception ex) {
            return "FAILED: system error. txId=" + finalTxId + ". Error=" + ex.getMessage();
        }
    }

    private String normalizeTxId(String txId) {
        if (txId == null || txId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return txId.trim();
    }

    private void createOrKeepGlobalLog(
            String txId,
            int itemId,
            int warehouseId,
            int districtId,
            int customerId,
            int orderQty) {
        site2Tx.executeWithoutResult(status -> {
            if (txLogRepo.existsById(txId)) {
                return;
            }

            GlobalTransactionLog log = new GlobalTransactionLog();
            log.setTxId(txId);
            log.setItemId(itemId);
            log.setWarehouseId(warehouseId);
            log.setDistrictId(districtId);
            log.setCustomerId(customerId);
            log.setOrderQty(orderQty);
            log.setStatus(PENDING);

            txLogRepo.save(log);
        });
    }

    private void validateCustomerAndDistrict(int customerId, int districtId, int warehouseId) {
        site2Tx.executeWithoutResult(status -> {
            var customer = customerRepo.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

            if (customer.getDistrictId() != districtId) {
                throw new IllegalArgumentException("Customer does not belong to district");
            }

            var district = districtRepo.findById(districtId)
                    .orElseThrow(() -> new IllegalArgumentException("District not found: " + districtId));

            if (district.getWarehouseId() != warehouseId) {
                throw new IllegalArgumentException("District does not belong to warehouse");
            }
        });
    }

    private boolean reserveStockWithRetry(String txId, int itemId, int warehouseId, int qty) {
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return reserveStock(txId, itemId, warehouseId, qty);
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException ex) {
                if (attempt == maxAttempts) {
                    throw ex;
                }

                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", interruptedException);
                }
            }
        }

        return false;
    }

    private boolean reserveStock(String txId, int itemId, int warehouseId, int qty) {
        return Boolean.TRUE.equals(site1Tx.execute(status -> {
            Optional<StockReservation> existing = reservationRepo.findById(txId);

            if (existing.isPresent()) {
                String reservationStatus = existing.get().getStatus();
                return RES_RESERVED.equals(reservationStatus) || RES_CONFIRMED.equals(reservationStatus);
            }

            int rows = stockRepo.deductStock(itemId, warehouseId, qty);
            if (rows == 0) {
                return false;
            }

            StockReservation reservation = new StockReservation();
            reservation.setTxId(txId);
            reservation.setItemId(itemId);
            reservation.setWarehouseId(warehouseId);
            reservation.setQty(qty);
            reservation.setStatus(RES_RESERVED);

            reservationRepo.save(reservation);

            return true;
        }));
    }

    private void createOrderAndCompleteTx(String txId, int districtId, int customerId) {
        site2Tx.executeWithoutResult(status -> {
            Orders order = new Orders();
            order.setTxId(txId);
            order.setDistrictId(districtId);
            order.setCustomerId(customerId);
            order.setEntryDate(LocalDateTime.now());

            orderRepo.save(order);

            GlobalTransactionLog log = txLogRepo.findById(txId)
                    .orElseThrow(() -> new IllegalStateException("Global tx log not found: " + txId));

            log.setStatus(ORDER_CREATED);
            log.setErrorMessage(null);

            txLogRepo.save(log);
        });
    }

    private void confirmReservation(String txId) {
        site1Tx.executeWithoutResult(status -> {
            StockReservation reservation = reservationRepo.findById(txId)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found: " + txId));

            reservation.setStatus(RES_CONFIRMED);
            reservationRepo.save(reservation);
        });
    }

    private void confirmReservationIfReserved(String txId) {
        site1Tx.executeWithoutResult(status -> {
            StockReservation reservation = reservationRepo.findById(txId)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found: " + txId));

            if (RES_RESERVED.equals(reservation.getStatus())) {
                reservation.setStatus(RES_CONFIRMED);
                reservationRepo.save(reservation);
            }
        });
    }

    private void compensateStock(String txId) {
        site1Tx.executeWithoutResult(status -> {
            StockReservation reservation = reservationRepo.findById(txId)
                    .orElseThrow(() -> new IllegalStateException("Reservation not found: " + txId));

            if (RES_COMPENSATED.equals(reservation.getStatus())) {
                return;
            }

            if (RES_CONFIRMED.equals(reservation.getStatus())) {
                throw new IllegalStateException("Cannot compensate confirmed reservation: " + txId);
            }

            stockRepo.addBackStock(
                    reservation.getItemId(),
                    reservation.getWarehouseId(),
                    reservation.getQty());

            reservation.setStatus(RES_COMPENSATED);
            reservationRepo.save(reservation);
        });
    }

    private void markTxStatus(String txId, String statusValue, String errorMessage) {
        site2Tx.executeWithoutResult(status -> {
            GlobalTransactionLog log = txLogRepo.findById(txId)
                    .orElseThrow(() -> new IllegalStateException("Global tx log not found: " + txId));

            log.setStatus(statusValue);
            log.setErrorMessage(errorMessage);

            txLogRepo.save(log);
        });
    }

    private void markTxFailed(String txId, String errorMessage) {
        markTxStatus(txId, FAILED, errorMessage);
    }

    private boolean reservationExists(String txId) {
        return Boolean.TRUE.equals(site1Tx.execute(status -> reservationRepo.existsById(txId)));
    }

    public int recoverPendingTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        List<GlobalTransactionLog> logs = txLogRepo.findTop50ByStatusInOrderByCreatedAtAsc(
                List.of(PENDING, STOCK_RESERVED, COMPENSATING, ORDER_CREATED));

        int recovered = 0;

        for (GlobalTransactionLog log : logs) {
            try {
                if (ORDER_CREATED.equals(log.getStatus())) {
                    confirmReservationIfReserved(log.getTxId());
                    markTxStatus(log.getTxId(), COMPLETED, null);
                    recovered++;
                    continue;
                }

                if (PENDING.equals(log.getStatus())) {
                    if (reservationExists(log.getTxId())) {
                        markTxStatus(log.getTxId(), COMPENSATING, "Recovery found reserved stock from PENDING tx");
                        compensateStock(log.getTxId());
                        markTxStatus(log.getTxId(), COMPENSATED, "Recovered PENDING transaction by compensation");
                        recovered++;
                    } else {
                        markTxStatus(log.getTxId(), FAILED, "Recovery found PENDING transaction without reservation");
                    }
                    continue;
                }

                if (STOCK_RESERVED.equals(log.getStatus()) || COMPENSATING.equals(log.getStatus())) {
                    markTxStatus(log.getTxId(), COMPENSATING, "Recovery compensation started");
                    compensateStock(log.getTxId());
                    markTxStatus(log.getTxId(), COMPENSATED, "Recovered by compensation worker");
                    recovered++;
                }

            } catch (Exception ex) {
                try {
                    // Không nên biến ORDER_CREATED thành FAILED nếu chỉ lỗi confirm ở site 1.
                    // Giữ nguyên status để lần recovery sau xử lý tiếp.
                    if (!ORDER_CREATED.equals(log.getStatus())) {
                        markTxStatus(log.getTxId(), FAILED, "Recovery failed: " + ex.getMessage());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return recovered;
    }
}