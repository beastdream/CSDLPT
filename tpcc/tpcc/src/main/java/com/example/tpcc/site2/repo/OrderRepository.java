package com.example.tpcc.site2.repo;

import com.example.tpcc.site2.entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Orders, Integer> {
    // Spring Data JPA sẽ tự động lo việc INSERT dữ liệu, bạn không cần viết SQL ở
    // đây.
}