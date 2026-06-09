package com.orderdesk.api.dto;

import java.math.BigDecimal;

public class StoreRequest {
    public Long ownerId;
    public String name;
    public String slug;
    public String category;
    public String whatsapp;
    public String contactType;
    public String contactValue;
    public String websiteUrl;
    public String description;
    public String logoUrl;
    public String bannerUrl;
    public String address;
    public String city;
    public String state;
    public String countryCode;
    public String countryName;
    public String openingHours;
    public String openingTime;
    public String closingTime;
    public Boolean openWeekdays;
    public Boolean openSaturday;
    public Boolean openSunday;
    public Boolean forceOpen;
    public Boolean forceClosed;
    public String storeStatus;
    public String deliveryTime;
    public BigDecimal deliveryFee;
    public BigDecimal minimumOrderAmount;
    public String paymentMethods;
}
