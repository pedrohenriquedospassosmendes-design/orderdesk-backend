package com.orderdesk.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_like", uniqueConstraints = @UniqueConstraint(columnNames = {"storeId", "userKey"}))
public class StoreLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long storeId;

    @Column(nullable = false, length = 120)
    private String userKey;

    private LocalDateTime createdAt = LocalDateTime.now();

    public StoreLike() {}

    public StoreLike(Long storeId, String userKey) {
        this.storeId = storeId;
        this.userKey = userKey;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public String getUserKey() { return userKey; }
    public void setUserKey(String userKey) { this.userKey = userKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
