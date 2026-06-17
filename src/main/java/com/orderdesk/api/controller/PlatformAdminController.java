package com.orderdesk.api.controller;

import com.orderdesk.api.model.CustomerOrder;
import com.orderdesk.api.model.PlatformSettings;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.PlatformSettingsRepository;
import com.orderdesk.api.repository.StoreLikeRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.StoreReviewRepository;
import com.orderdesk.api.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {
    private final UserAccountRepository users;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;
    private final StoreReviewRepository reviews;
    private final StoreLikeRepository likes;
    private final PlatformSettingsRepository platformSettings;
    private final Set<String> adminTokens = ConcurrentHashMap.newKeySet();

    @Value("${orderdesk.platform-admin.password:admin123}")
    private String adminPassword;

    public PlatformAdminController(UserAccountRepository users, StoreRepository stores, ProductRepository products, CustomerOrderRepository orders, StoreReviewRepository reviews, StoreLikeRepository likes, PlatformSettingsRepository platformSettings) {
        this.users = users;
        this.stores = stores;
        this.products = products;
        this.orders = orders;
        this.reviews = reviews;
        this.likes = likes;
        this.platformSettings = platformSettings;
    }

    public record AdminLoginRequest(String password) {}
    public record AdminStoreUpdateRequest(String name, String slug, String category, String whatsapp, String contactType,
                                          String contactValue, String websiteUrl, String description, String address,
                                          String city, String state, String countryCode, String countryName,
                                          String openingHours, String openingTime, String closingTime,
                                          Boolean openWeekdays, Boolean openSaturday, Boolean openSunday,
                                          Boolean forceOpen, Boolean forceClosed, String deliveryTime,
                                          BigDecimal deliveryFee, BigDecimal minimumOrderAmount, String paymentMethods,
                                          String storeStatus, String billingStatus, Boolean active,
                                          Boolean blockedForBilling, String planType, Integer monthlyOrderLimit,
                                          Integer currentMonthOrders) {}
    public record AdminAccountUpdateRequest(String name, String accountType, String platformRole, String city, String state, String countryName) {}
    public record PlatformSettingsRequest(Boolean siteOpen, Boolean globalNoticeActive, String globalNoticeTitle,
                                          String globalNoticeMessage, String globalNoticeType, String closedTitle,
                                          String closedMessage) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        if (request == null || request.password() == null || !request.password().equals(adminPassword)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Senha incorreta."));
        }
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        adminTokens.add(token);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String token) {
        adminTokens.remove(token);
        return ResponseEntity.ok(message("Sessao encerrada."));
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam String token) {
        if (!isAdmin(token)) return forbidden();

        List<CustomerOrder> allOrders = orders.findAll();
        BigDecimal totalOrdersValue = allOrders.stream()
                .map(CustomerOrder::getTotal)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSubtotal = allOrders.stream()
                .map(CustomerOrder::getSubtotal)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeliveryFees = allOrders.stream()
                .map(CustomerOrder::getDeliveryFee)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPlatformFees = allOrders.stream()
                .map(CustomerOrder::getPlatformFee)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageTicket = allOrders.isEmpty()
                ? BigDecimal.ZERO
                : totalOrdersValue.divide(BigDecimal.valueOf(allOrders.size()), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", users.count());
        result.put("stores", stores.count());
        result.put("activeStores", stores.findAll().stream().filter(Store::isActive).count());
        result.put("products", products.count());
        result.put("orders", allOrders.size());
        result.put("totalOrdersValue", totalOrdersValue);
        result.put("totalSubtotal", totalSubtotal);
        result.put("totalDeliveryFees", totalDeliveryFees);
        result.put("totalPlatformFees", totalPlatformFees);
        result.put("platformProfit", totalPlatformFees);
        result.put("averageTicket", averageTicket);
        result.put("nearLimitStores", stores.findAll().stream()
                .filter(store -> !"BLOCKED".equalsIgnoreCase(store.getBillingStatus()))
                .filter(store -> store.getCurrentMonthOrders() >= Math.max(1, store.getMonthlyOrderLimit() - 5))
                .count());
        result.put("billingBlockedStores", stores.findAll().stream()
                .filter(store -> store.isBlockedForBilling() || "BLOCKED".equalsIgnoreCase(store.getBillingStatus()) || "LIMIT_REACHED".equalsIgnoreCase(store.getBillingStatus()))
                .count());
        result.put("siteOpen", settings().isSiteOpen());
        result.put("globalNoticeActive", settings().isGlobalNoticeActive());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/settings")
    public ResponseEntity<?> settings(@RequestParam String token) {
        if (!isAdmin(token)) return forbidden();
        return ResponseEntity.ok(settings());
    }

    @PatchMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestParam String token, @RequestBody PlatformSettingsRequest request) {
        if (!isAdmin(token)) return forbidden();
        PlatformSettings s = settings();
        if (request.siteOpen() != null) s.setSiteOpen(request.siteOpen());
        if (request.globalNoticeActive() != null) s.setGlobalNoticeActive(request.globalNoticeActive());
        if (request.globalNoticeTitle() != null) s.setGlobalNoticeTitle(clean(request.globalNoticeTitle()));
        if (request.globalNoticeMessage() != null) s.setGlobalNoticeMessage(clean(request.globalNoticeMessage()));
        if (request.globalNoticeType() != null) s.setGlobalNoticeType(cleanStatus(request.globalNoticeType(), "INFO"));
        if (request.closedTitle() != null) s.setClosedTitle(clean(request.closedTitle()));
        if (request.closedMessage() != null) s.setClosedMessage(clean(request.closedMessage()));
        return ResponseEntity.ok(platformSettings.save(s));
    }

    @GetMapping("/accounts")
    public ResponseEntity<?> accounts(@RequestParam String token) {
        if (!isAdmin(token)) return forbidden();
        return ResponseEntity.ok(users.findAll().stream()
                .sorted(Comparator.comparing(UserAccount::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("name", user.getName());
                    row.put("email", user.getEmail());
                    row.put("accountType", user.getAccountType());
                    row.put("platformRole", user.getPlatformRole());
                    row.put("city", user.getCity());
                    row.put("state", user.getState());
                    row.put("countryName", user.getCountryName());
                    row.put("createdAt", user.getCreatedAt());
                    row.put("lastLoginAt", user.getLastLoginAt());
                    row.put("stores", stores.countByOwnerId(user.getId()));
                    return row;
                }).toList());
    }

    @GetMapping("/stores")
    public ResponseEntity<?> stores(@RequestParam String token) {
        if (!isAdmin(token)) return forbidden();
        return ResponseEntity.ok(stores.findAll().stream()
                .sorted(Comparator.comparing(Store::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(store -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", store.getId());
                    row.put("name", store.getName());
                    row.put("slug", store.getSlug());
                    row.put("ownerId", store.getOwnerId());
                    row.put("category", store.getCategory());
                    row.put("whatsapp", store.getWhatsapp());
                    row.put("contactType", store.getContactType());
                    row.put("contactValue", store.getContactValue());
                    row.put("websiteUrl", store.getWebsiteUrl());
                    row.put("description", store.getDescription());
                    row.put("address", store.getAddress());
                    row.put("city", store.getCity());
                    row.put("state", store.getState());
                    row.put("countryCode", store.getCountryCode());
                    row.put("countryName", store.getCountryName());
                    row.put("openingHours", store.getOpeningHours());
                    row.put("openingTime", store.getOpeningTime());
                    row.put("closingTime", store.getClosingTime());
                    row.put("openWeekdays", store.isOpenWeekdays());
                    row.put("openSaturday", store.isOpenSaturday());
                    row.put("openSunday", store.isOpenSunday());
                    row.put("forceOpen", store.isForceOpen());
                    row.put("forceClosed", store.isForceClosed());
                    row.put("deliveryTime", store.getDeliveryTime());
                    row.put("deliveryFee", store.getDeliveryFee());
                    row.put("minimumOrderAmount", store.getMinimumOrderAmount());
                    row.put("paymentMethods", store.getPaymentMethods());
                    row.put("active", store.isActive());
                    row.put("storeStatus", store.getStoreStatus());
                    row.put("billingStatus", store.getBillingStatus());
                    row.put("currentMonthOrders", store.getCurrentMonthOrders());
                    row.put("monthlyOrderLimit", store.getMonthlyOrderLimit());
                    row.put("planType", store.getPlanType());
                    row.put("blockedForBilling", store.isBlockedForBilling());
                    row.put("createdAt", store.getCreatedAt());
                    return row;
                }).toList());
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(@RequestParam String token) {
        if (!isAdmin(token)) return forbidden();
        return ResponseEntity.ok(orders.findAll().stream()
                .sorted(Comparator.comparing(CustomerOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(300)
                .map(this::orderRow).toList());
    }

    @PatchMapping("/stores/{id}")
    public ResponseEntity<?> updateStore(@PathVariable Long id, @RequestParam String token, @RequestBody AdminStoreUpdateRequest request) {
        if (!isAdmin(token)) return forbidden();
        var opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (request.name() != null && !request.name().isBlank()) store.setName(clean(request.name()));
        if (request.slug() != null) store.setSlug(clean(request.slug()));
        if (request.category() != null) store.setCategory(clean(request.category()));
        if (request.whatsapp() != null) store.setWhatsapp(onlyDigits(request.whatsapp()));
        if (request.contactType() != null) store.setContactType(cleanStatus(request.contactType(), "WHATSAPP"));
        if (request.contactValue() != null) store.setContactValue(clean(request.contactValue()));
        if (request.websiteUrl() != null) store.setWebsiteUrl(clean(request.websiteUrl()));
        if (request.description() != null) store.setDescription(clean(request.description()));
        if (request.address() != null) store.setAddress(clean(request.address()));
        if (request.city() != null) store.setCity(clean(request.city()));
        if (request.state() != null) store.setState(clean(request.state()));
        if (request.countryCode() != null) store.setCountryCode(cleanStatus(request.countryCode(), "BR"));
        if (request.countryName() != null) store.setCountryName(clean(request.countryName()));
        if (request.openingHours() != null) store.setOpeningHours(clean(request.openingHours()));
        if (request.openingTime() != null) store.setOpeningTime(clean(request.openingTime()));
        if (request.closingTime() != null) store.setClosingTime(clean(request.closingTime()));
        if (request.openWeekdays() != null) store.setOpenWeekdays(request.openWeekdays());
        if (request.openSaturday() != null) store.setOpenSaturday(request.openSaturday());
        if (request.openSunday() != null) store.setOpenSunday(request.openSunday());
        if (request.forceOpen() != null) store.setForceOpen(request.forceOpen());
        if (request.forceClosed() != null) store.setForceClosed(request.forceClosed());
        if (request.deliveryTime() != null) store.setDeliveryTime(clean(request.deliveryTime()));
        if (request.deliveryFee() != null) store.setDeliveryFee(request.deliveryFee());
        if (request.minimumOrderAmount() != null) store.setMinimumOrderAmount(request.minimumOrderAmount());
        if (request.paymentMethods() != null) store.setPaymentMethods(clean(request.paymentMethods()));
        if (request.storeStatus() != null) store.setStoreStatus(cleanStatus(request.storeStatus(), "OPEN"));
        if (request.billingStatus() != null) store.setBillingStatus(cleanStatus(request.billingStatus(), "OK"));
        if (request.active() != null) store.setActive(request.active());
        if (request.blockedForBilling() != null) store.setBlockedForBilling(request.blockedForBilling());
        if (request.planType() != null) store.setPlanType(cleanStatus(request.planType(), "FREE"));
        if (request.monthlyOrderLimit() != null) store.setMonthlyOrderLimit(Math.max(0, request.monthlyOrderLimit()));
        if (request.currentMonthOrders() != null) store.setCurrentMonthOrders(Math.max(0, request.currentMonthOrders()));
        return ResponseEntity.ok(storeRow(stores.save(store)));
    }

    @PatchMapping("/stores/{id}/active")
    public ResponseEntity<?> setStoreActive(@PathVariable Long id, @RequestParam String token, @RequestParam boolean active) {
        if (!isAdmin(token)) return forbidden();
        var opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        store.setActive(active);
        return ResponseEntity.ok(storeRow(stores.save(store)));
    }

    @DeleteMapping("/stores/{id}")
    @Transactional
    public ResponseEntity<?> deleteStore(@PathVariable Long id, @RequestParam String token) {
        if (!isAdmin(token)) return forbidden();
        var opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        deleteStoreCascade(opt.get());
        return ResponseEntity.ok(message("Loja excluida."));
    }

    @PatchMapping("/accounts/{id}")
    public ResponseEntity<?> updateAccount(@PathVariable Long id, @RequestParam String token, @RequestBody AdminAccountUpdateRequest request) {
        if (!isAdmin(token)) return forbidden();
        var opt = users.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Conta nao encontrada."));
        UserAccount user = opt.get();
        if (request.name() != null && !request.name().isBlank()) user.setName(clean(request.name()));
        if (request.accountType() != null) user.setAccountType(cleanStatus(request.accountType(), "store").toLowerCase());
        if (request.platformRole() != null) user.setPlatformRole(cleanStatus(request.platformRole(), "USER"));
        if (request.city() != null) user.setCity(clean(request.city()));
        if (request.state() != null) user.setState(clean(request.state()));
        if (request.countryName() != null) user.setCountryName(clean(request.countryName()));
        users.save(user);
        return ResponseEntity.ok(message("Conta atualizada."));
    }

    @DeleteMapping("/accounts/{id}")
    @Transactional
    public ResponseEntity<?> deleteAccount(@PathVariable Long id, @RequestParam String token) {
        if (!isAdmin(token)) return forbidden();
        var opt = users.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Conta nao encontrada."));
        stores.findByOwnerIdOrderByCreatedAtDesc(id).forEach(this::deleteStoreCascade);
        users.delete(opt.get());
        return ResponseEntity.ok(message("Conta e lojas excluidas."));
    }

    private boolean isAdmin(String token) {
        return token != null && !token.isBlank() && adminTokens.contains(token);
    }

    private Map<String, Object> storeRow(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", store.getName());
        row.put("slug", store.getSlug());
        row.put("ownerId", store.getOwnerId());
        row.put("category", store.getCategory());
        row.put("whatsapp", store.getWhatsapp());
        row.put("contactType", store.getContactType());
        row.put("contactValue", store.getContactValue());
        row.put("websiteUrl", store.getWebsiteUrl());
        row.put("description", store.getDescription());
        row.put("address", store.getAddress());
        row.put("city", store.getCity());
        row.put("state", store.getState());
        row.put("countryCode", store.getCountryCode());
        row.put("countryName", store.getCountryName());
        row.put("openingHours", store.getOpeningHours());
        row.put("openingTime", store.getOpeningTime());
        row.put("closingTime", store.getClosingTime());
        row.put("openWeekdays", store.isOpenWeekdays());
        row.put("openSaturday", store.isOpenSaturday());
        row.put("openSunday", store.isOpenSunday());
        row.put("forceOpen", store.isForceOpen());
        row.put("forceClosed", store.isForceClosed());
        row.put("deliveryTime", store.getDeliveryTime());
        row.put("deliveryFee", store.getDeliveryFee());
        row.put("minimumOrderAmount", store.getMinimumOrderAmount());
        row.put("paymentMethods", store.getPaymentMethods());
        row.put("active", store.isActive());
        row.put("storeStatus", store.getStoreStatus());
        row.put("billingStatus", store.getBillingStatus());
        row.put("currentMonthOrders", store.getCurrentMonthOrders());
        row.put("monthlyOrderLimit", store.getMonthlyOrderLimit());
        row.put("planType", store.getPlanType());
        row.put("blockedForBilling", store.isBlockedForBilling());
        row.put("createdAt", store.getCreatedAt());
        return row;
    }

    private Map<String, Object> orderRow(CustomerOrder order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("storeId", order.getStoreId());
        row.put("storeName", storeName(order.getStoreId()));
        row.put("customerName", order.getCustomerName());
        row.put("customerEmail", order.getCustomerEmail());
        row.put("customerPhone", order.getCustomerPhone());
        row.put("customerAccountId", order.getCustomerAccountId());
        row.put("paymentMethod", order.getPaymentMethod());
        row.put("deliveryType", order.getDeliveryType());
        row.put("subtotal", order.getSubtotal());
        row.put("deliveryFee", order.getDeliveryFee());
        row.put("platformFee", order.getPlatformFee());
        row.put("total", order.getTotal());
        row.put("status", order.getStatus());
        row.put("createdAt", order.getCreatedAt());
        return row;
    }

    private String storeName(Long storeId) {
        if (storeId == null) return "Loja nao informada";
        return stores.findById(storeId)
                .map(Store::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Loja #" + storeId);
    }

    private void deleteStoreCascade(Store store) {
        Long id = store.getId();
        orders.deleteByStoreId(id);
        reviews.deleteByStoreId(id);
        likes.deleteByStoreId(id);
        products.deleteByStoreId(id);
        stores.delete(store);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String onlyDigits(String value) {
        return value == null ? null : value.replaceAll("\\D+", "");
    }

    private PlatformSettings settings() {
        return platformSettings.findById(1L).orElseGet(() -> platformSettings.save(new PlatformSettings()));
    }

    private String cleanStatus(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase().replaceAll("[^A-Z0-9_\\-]", "");
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode acessar esta area."));
    }

    private Map<String, String> message(String text) {
        return Map.of("message", text);
    }
}
