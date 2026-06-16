package com.orderdesk.api.controller;

import com.orderdesk.api.model.CustomerOrder;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private final Set<String> adminTokens = ConcurrentHashMap.newKeySet();

    @Value("${orderdesk.platform-admin.password:admin123}")
    private String adminPassword;

    public PlatformAdminController(UserAccountRepository users, StoreRepository stores, ProductRepository products, CustomerOrderRepository orders, StoreReviewRepository reviews, StoreLikeRepository likes) {
        this.users = users;
        this.stores = stores;
        this.products = products;
        this.orders = orders;
        this.reviews = reviews;
        this.likes = likes;
    }

    public record AdminLoginRequest(String password) {}
    public record AdminStoreUpdateRequest(String name, String category, String city, String state, String countryName,
                                          String storeStatus, String billingStatus, Boolean active,
                                          Boolean blockedForBilling, String planType, Integer monthlyOrderLimit,
                                          Integer currentMonthOrders) {}
    public record AdminAccountUpdateRequest(String name, String accountType, String platformRole, String city, String state, String countryName) {}

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

        BigDecimal totalOrdersValue = orders.findAll().stream()
                .map(CustomerOrder::getTotal)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", users.count());
        result.put("stores", stores.count());
        result.put("activeStores", stores.findAll().stream().filter(Store::isActive).count());
        result.put("products", products.count());
        result.put("orders", orders.count());
        result.put("totalOrdersValue", totalOrdersValue);
        result.put("nearLimitStores", stores.findAll().stream()
                .filter(store -> !"BLOCKED".equalsIgnoreCase(store.getBillingStatus()))
                .filter(store -> store.getCurrentMonthOrders() >= Math.max(1, store.getMonthlyOrderLimit() - 5))
                .count());
        result.put("billingBlockedStores", stores.findAll().stream()
                .filter(store -> store.isBlockedForBilling() || "BLOCKED".equalsIgnoreCase(store.getBillingStatus()) || "LIMIT_REACHED".equalsIgnoreCase(store.getBillingStatus()))
                .count());
        return ResponseEntity.ok(result);
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
                    row.put("city", store.getCity());
                    row.put("state", store.getState());
                    row.put("countryName", store.getCountryName());
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
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", order.getId());
                    row.put("storeId", order.getStoreId());
                    row.put("customerName", order.getCustomerName());
                    row.put("customerEmail", order.getCustomerEmail());
                    row.put("customerPhone", order.getCustomerPhone());
                    row.put("total", order.getTotal());
                    row.put("status", order.getStatus());
                    row.put("createdAt", order.getCreatedAt());
                    return row;
                }).toList());
    }

    @PatchMapping("/stores/{id}")
    public ResponseEntity<?> updateStore(@PathVariable Long id, @RequestParam String token, @RequestBody AdminStoreUpdateRequest request) {
        if (!isAdmin(token)) return forbidden();
        var opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (request.name() != null && !request.name().isBlank()) store.setName(clean(request.name()));
        if (request.category() != null) store.setCategory(clean(request.category()));
        if (request.city() != null) store.setCity(clean(request.city()));
        if (request.state() != null) store.setState(clean(request.state()));
        if (request.countryName() != null) store.setCountryName(clean(request.countryName()));
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
        row.put("city", store.getCity());
        row.put("state", store.getState());
        row.put("countryName", store.getCountryName());
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
