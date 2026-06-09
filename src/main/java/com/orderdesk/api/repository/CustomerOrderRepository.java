package com.orderdesk.api.repository;

import com.orderdesk.api.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<CustomerOrder> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String customerEmail);
    List<CustomerOrder> findByCustomerAccountIdOrderByCreatedAtDesc(Long customerAccountId);
    void deleteByStoreId(Long storeId);
}
