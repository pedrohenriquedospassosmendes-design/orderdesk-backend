package com.orderdesk.api.repository;

import com.orderdesk.api.model.StoreReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoreReviewRepository extends JpaRepository<StoreReview, Long> {
    List<StoreReview> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    void deleteByStoreId(Long storeId);
    long countByStoreId(Long storeId);
    Optional<StoreReview> findByStoreIdAndVisitorKey(Long storeId, String visitorKey);
}
