package com.orderdesk.api.dto;

import java.math.BigDecimal;

public class ProductRequest {
    public Long ownerId;
    public String name;
    public String description;
    public BigDecimal price;
    public String imageUrl;
    public String category;
    public Boolean available;
    public Boolean featured;
    public Boolean promotional;
}
