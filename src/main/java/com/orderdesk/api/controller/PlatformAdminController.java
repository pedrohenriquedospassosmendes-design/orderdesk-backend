package com.orderdesk.api.controller;

import com.orderdesk.api.model.CustomerOrder;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.UserAccountRepository;
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
    private final Set<String> adminTokens = ConcurrentHashMap.newKeySet();

    @Value("${orderdesk.platform-admin.password:admin123}")
    private String adminPassword;

    public PlatformAdminController(UserAccountRepository users, StoreRepository stores, ProductRepository products, CustomerOrderRepository orders) {
        this.users = users;
        this.stores = stores;
        this.products = products;
        this.orders = orders;
    }

    public record AdminLoginRequest(String password) {}

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

    private boolean isAdmin(String token) {
        return token != null && !token.isBlank() && adminTokens.contains(token);
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode acessar esta area."));
    }

    private Map<String, String> message(String text) {
        return Map.of("message", text);
    }
}
