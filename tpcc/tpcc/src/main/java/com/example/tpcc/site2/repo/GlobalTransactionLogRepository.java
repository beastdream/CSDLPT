package com.example.tpcc.site2.repo;

import com.example.tpcc.site2.entity.GlobalTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GlobalTransactionLogRepository extends JpaRepository<GlobalTransactionLog, String> {
    List<GlobalTransactionLog> findTop50ByStatusInOrderByCreatedAtAsc(List<String> statuses);

    List<GlobalTransactionLog> findTop50ByStatusInAndUpdatedAtBeforeOrderByCreatedAtAsc(
            List<String> statuses,
            LocalDateTime cutoff);
}