package com.orderdesk.api.controller;

import com.orderdesk.api.model.CustomerOrder;
import com.orderdesk.api.model.Product;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.UserAccountRepository;
import com.orderdesk.api.service.BillingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;
    private final UserAccountRepository users;
    private final BillingService billingService;

    public StatsController(StoreRepository stores, ProductRepository products, CustomerOrderRepository orders, UserAccountRepository users, BillingService billingService) {
        this.stores = stores;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.billingService = billingService;
    }

    @GetMapping("/owner")
    public ResponseEntity<?> ownerStats(@RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        return ownerStatsFor(user.get().getId());
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<?> ownerStatsLegacy(@PathVariable Long ownerId, @RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        if (!user.get().getId().equals(ownerId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Voce nao pode ver estatisticas de outra conta."));
        return ownerStatsFor(user.get().getId());
    }

    private ResponseEntity<?> ownerStatsFor(Long ownerId) {
        List<Store> ownerStores = stores.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        int storeCount = ownerStores.size();
        int activeStoreCount = 0;
        int productCount = 0;
        int availableProductCount = 0;
        int orderCount = 0;
        int confirmedSalesCount = 0;
        int canceledOrdersCount = 0;
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal totalReceivedOrdersValue = BigDecimal.ZERO;
        BigDecimal averageConfirmedTicket = BigDecimal.ZERO;
        BigDecimal platformFees = BigDecimal.ZERO;
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        List<Map<String, Object>> storeRows = new ArrayList<>();
        int freePlanOrdersUsed = 0;
        int freePlanOrdersRemaining = 0;
        int billingBlockedStores = 0;

        for (Store store : ownerStores) {
            store = billingService.refreshAndSave(store);
            if (store.isActive()) activeStoreCount++;
            List<Product> storeProducts = products.findByStoreIdOrderByCreatedAtDesc(store.getId());
            productCount += storeProducts.size();
            for (Product p : storeProducts) if (p.isAvailable()) availableProductCount++;

            List<CustomerOrder> storeOrders = orders.findByStoreIdOrderByCreatedAtDesc(store.getId());
            BigDecimal storeSold = BigDecimal.ZERO;
            BigDecimal storeReceivedOrdersValue = BigDecimal.ZERO;
            int storeConfirmedSalesCount = 0;
            int storeCanceledOrdersCount = 0;
            for (CustomerOrder order : storeOrders) {
                orderCount++;
                String status = order.getStatus() == null ? "novo" : order.getStatus();
                BigDecimal orderTotal = order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
                statusCount.put(status, statusCount.getOrDefault(status, 0) + 1);
                totalReceivedOrdersValue = totalReceivedOrdersValue.add(orderTotal);
                storeReceivedOrdersValue = storeReceivedOrdersValue.add(orderTotal);
                if ("cancelado".equalsIgnoreCase(status) || "cancelado_cliente".equalsIgnoreCase(status)) {
                    canceledOrdersCount++;
                    storeCanceledOrdersCount++;
                }
                if (countsAsSale(status)) {
                    confirmedSalesCount++;
                    storeConfirmedSalesCount++;
                    totalSold = totalSold.add(orderTotal);
                    storeSold = storeSold.add(orderTotal);
                    platformFees = platformFees.add(order.getPlatformFee() == null ? BigDecimal.ZERO : order.getPlatformFee());
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeId", store.getId());
            row.put("storeName", store.getName());
            row.put("active", store.isActive());
            row.put("products", storeProducts.size());
            row.put("orders", storeOrders.size());
            row.put("confirmedSalesCount", storeConfirmedSalesCount);
            row.put("canceledOrdersCount", storeCanceledOrdersCount);
            row.put("totalReceivedOrdersValue", storeReceivedOrdersValue);
            row.put("totalSold", storeSold);
            row.putAll(billingService.summary(store));
            storeRows.add(row);
            freePlanOrdersUsed += Math.max(0, store.getCurrentMonthOrders());
            freePlanOrdersRemaining += Math.max(0, store.getMonthlyOrderLimit() - store.getCurrentMonthOrders());
            if (store.isBlockedForBilling()) billingBlockedStores++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("storeCount", storeCount);
        result.put("activeStoreCount", activeStoreCount);
        result.put("productCount", productCount);
        result.put("availableProductCount", availableProductCount);
        result.put("orderCount", orderCount);
        result.put("confirmedSalesCount", confirmedSalesCount);
        result.put("canceledOrdersCount", canceledOrdersCount);
        result.put("totalSold", totalSold);
        result.put("totalReceivedOrdersValue", totalReceivedOrdersValue);
        if (confirmedSalesCount > 0) averageConfirmedTicket = totalSold.divide(BigDecimal.valueOf(confirmedSalesCount), 2, java.math.RoundingMode.HALF_UP);
        result.put("averageConfirmedTicket", averageConfirmedTicket);
        result.put("platformFees", platformFees);
        result.put("statusCount", statusCount);
        result.put("stores", storeRows);
        result.put("freePlanOrdersUsed", freePlanOrdersUsed);
        result.put("freePlanOrdersRemaining", freePlanOrdersRemaining);
        result.put("billingBlockedStores", billingBlockedStores);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/customer")
    public ResponseEntity<?> customerStats(@RequestParam(required = false) String token,
                                           @RequestParam(required = false) Long accountId,
                                           @RequestParam(required = false) String email) {
        Optional<UserAccount> user = requireUser(token);
        List<CustomerOrder> customerOrders;
        if (user.isPresent()) {
            customerOrders = orders.findByCustomerAccountIdOrderByCreatedAtDesc(user.get().getId());
        } else if (accountId != null) {
            customerOrders = orders.findByCustomerAccountIdOrderByCreatedAtDesc(accountId);
        } else if (email != null && !email.isBlank()) {
            customerOrders = orders.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Conta nao informada."));
        }

        BigDecimal totalSpent = BigDecimal.ZERO;
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        for (CustomerOrder order : customerOrders) {
            String status = order.getStatus() == null ? "novo" : order.getStatus();
            statusCount.put(status, statusCount.getOrDefault(status, 0) + 1);
            if (countsAsSale(status)) totalSpent = totalSpent.add(order.getTotal() == null ? BigDecimal.ZERO : order.getTotal());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderCount", customerOrders.size());
        result.put("totalSpent", totalSpent);
        result.put("statusCount", statusCount);
        result.put("recentOrders", customerOrders.stream().limit(8).toList());
        return ResponseEntity.ok(result);
    }

    private Optional<UserAccount> requireUser(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return users.findBySessionToken(token);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Sessao expirada. Entre novamente."));
    }

    private boolean countsAsSale(String status) {
        if (status == null) return false;
        String clean = status.trim().toLowerCase();
        return Set.of("confirmado", "preparando", "saiu_entrega", "finalizado").contains(clean);
    }
}
