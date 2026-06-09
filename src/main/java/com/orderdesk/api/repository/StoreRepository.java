package com.orderdesk.api.repository;

import com.orderdesk.api.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    List<Store> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    long countByOwnerId(Long ownerId);
    List<Store> findByActiveTrueOrderByCreatedAtDesc();
    List<Store> findByActiveTrueAndNameContainingIgnoreCaseOrActiveTrueAndCategoryContainingIgnoreCaseOrActiveTrueAndCityContainingIgnoreCaseOrderByCreatedAtDesc(String name, String category, String city);
}
