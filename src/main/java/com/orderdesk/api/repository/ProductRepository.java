package com.orderdesk.api.repository;

import com.orderdesk.api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<Product> findByStoreIdAndAvailableTrueOrderByCreatedAtDesc(Long storeId);
    void deleteByStoreId(Long storeId);
}
