package com.orderdesk.api.dto;

import java.util.List;

public class OrderRequest {
    public String customerName;
    public String customerPhone;
    public String customerAddress;
    public String customerEmail;
    public Long customerAccountId;
    public String paymentMethod;
    public String deliveryType;
    public String customerDistrict;
    public String customerNumber;
    public String customerComplement;
    public String customerReference;
    public String notes;
    public List<OrderItemRequest> items;

    public static class OrderItemRequest {
        public Long productId;
        public Integer quantity;
    }
}
