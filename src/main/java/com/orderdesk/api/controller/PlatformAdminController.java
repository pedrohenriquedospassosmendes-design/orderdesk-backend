package com.orderdesk.api.controller;

import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {
    private final UserAccountRepository users;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;

    public PlatformAdminController(UserAccountRepository users, StoreRepository stores, ProductRepository products, CustomerOrderRepository orders) {
        this.users = users;
        this.stores = stores;
        this.products = products;
        this.orders = orders;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam String token) {
        var admin = users.findBySessionToken(token);
        if (admin.isEmpty() || !isPlatformAdmin(admin.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode acessar esta área."));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", users.count());
        result.put("stores", stores.count());
        result.put("products", products.count());
        result.put("orders", orders.count());
        result.put("nearLimitStores", stores.findAll().stream()
                .filter(store -> !"BLOCKED".equalsIgnoreCase(store.getBillingStatus()))
                .filter(store -> store.getCurrentMonthOrders() >= Math.max(1, store.getMonthlyOrderLimit() - 5))
                .count());
        result.put("billingBlockedStores", stores.findAll().stream()
                .filter(store -> store.isBlockedForBilling() || "BLOCKED".equalsIgnoreCase(store.getBillingStatus()) || "LIMIT_REACHED".equalsIgnoreCase(store.getBillingStatus()))
                .count());
        return ResponseEntity.ok(result);
    }

    private boolean isPlatformAdmin(UserAccount user) {
        return "ADMIN".equalsIgnoreCase(user.getPlatformRole());
    }

    private Map<String, String> message(String text) {
        return Map.of("message", text);
    }
}
