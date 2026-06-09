package com.orderdesk.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "store")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;
    private String name;

    @Column(unique = true)
    private String slug;

    private String category;
    private String whatsapp;
    private String contactType = "WHATSAPP";
    private String contactValue;
    private String websiteUrl;

    @Column(length = 1200)
    private String description;

    @Column(columnDefinition = "TEXT", length = 300000)
    private String logoUrl;

    @Column(columnDefinition = "TEXT", length = 300000)
    private String bannerUrl;

    private String address;
    private String city;
    private String state;
    private String countryCode = "BR";
    private String countryName = "Brasil";
    private String openingHours;
    private String openingTime;
    private String closingTime;
    private boolean openWeekdays = true;
    private boolean openSaturday = true;
    private boolean openSunday = false;
    private boolean forceOpen = false;
    private boolean forceClosed = false;
    private String storeStatus = "OPEN";
    private String deliveryTime;
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;
    private String paymentMethods;
    private boolean active = true;
    private int likesCount = 0;
    private int reviewCount = 0;
    private double ratingAverage = 0.0;
    private String planType = "FREE";
    private int monthlyOrderLimit = 30;
    private int currentMonthOrders = 0;
    private String billingStatus = "OK";
    private boolean blockedForBilling = false;
    private String billingMonth;
    private BigDecimal amountDue = BigDecimal.ZERO;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }
    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }
    public String getOpeningTime() { return openingTime; }
    public void setOpeningTime(String openingTime) { this.openingTime = openingTime; }
    public String getClosingTime() { return closingTime; }
    public void setClosingTime(String closingTime) { this.closingTime = closingTime; }
    public boolean isOpenWeekdays() { return openWeekdays; }
    public void setOpenWeekdays(boolean openWeekdays) { this.openWeekdays = openWeekdays; }
    public boolean isOpenSaturday() { return openSaturday; }
    public void setOpenSaturday(boolean openSaturday) { this.openSaturday = openSaturday; }
    public boolean isOpenSunday() { return openSunday; }
    public void setOpenSunday(boolean openSunday) { this.openSunday = openSunday; }
    public boolean isForceOpen() { return forceOpen; }
    public void setForceOpen(boolean forceOpen) { this.forceOpen = forceOpen; }
    public boolean isForceClosed() { return forceClosed; }
    public void setForceClosed(boolean forceClosed) { this.forceClosed = forceClosed; }
    public String getStoreStatus() { return storeStatus; }
    public void setStoreStatus(String storeStatus) { this.storeStatus = storeStatus; }
    public String getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(String deliveryTime) { this.deliveryTime = deliveryTime; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getMinimumOrderAmount() { return minimumOrderAmount; }
    public void setMinimumOrderAmount(BigDecimal minimumOrderAmount) { this.minimumOrderAmount = minimumOrderAmount; }
    public String getPaymentMethods() { return paymentMethods; }
    public void setPaymentMethods(String paymentMethods) { this.paymentMethods = paymentMethods; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public double getRatingAverage() { return ratingAverage; }
    public void setRatingAverage(double ratingAverage) { this.ratingAverage = ratingAverage; }
    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }
    public int getMonthlyOrderLimit() { return monthlyOrderLimit; }
    public void setMonthlyOrderLimit(int monthlyOrderLimit) { this.monthlyOrderLimit = monthlyOrderLimit; }
    public int getCurrentMonthOrders() { return currentMonthOrders; }
    public void setCurrentMonthOrders(int currentMonthOrders) { this.currentMonthOrders = currentMonthOrders; }
    public String getBillingStatus() { return billingStatus; }
    public void setBillingStatus(String billingStatus) { this.billingStatus = billingStatus; }
    public boolean isBlockedForBilling() { return blockedForBilling; }
    public void setBlockedForBilling(boolean blockedForBilling) { this.blockedForBilling = blockedForBilling; }
    public String getBillingMonth() { return billingMonth; }
    public void setBillingMonth(String billingMonth) { this.billingMonth = billingMonth; }
    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
