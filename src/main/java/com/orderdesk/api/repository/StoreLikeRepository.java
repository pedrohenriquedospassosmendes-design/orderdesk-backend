package com.orderdesk.api.repository;

import com.orderdesk.api.model.StoreLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreLikeRepository extends JpaRepository<StoreLike, Long> {
    Optional<StoreLike> findByStoreIdAndUserKey(Long storeId, String userKey);
    long countByStoreId(Long storeId);
    void deleteByStoreId(Long storeId);
}
