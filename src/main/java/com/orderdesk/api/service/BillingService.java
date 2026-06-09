package com.orderdesk.api.service;

import com.orderdesk.api.model.Store;
import com.orderdesk.api.repository.StoreRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

@Service
public class BillingService {
    public static final int FREE_LIMIT = 30;
    public static final int NEAR_LIMIT_AT = 25;

    private final StoreRepository stores;

    public BillingService(StoreRepository stores) {
        this.stores = stores;
    }

    public Store refresh(Store store) {
        ensureDefaults(store);
        String currentMonth = YearMonth.now().toString();
        if (store.getBillingMonth() == null || !store.getBillingMonth().equals(currentMonth)) {
            store.setBillingMonth(currentMonth);
            store.setCurrentMonthOrders(0);
            store.setBlockedForBilling(false);
            store.setAmountDue(BigDecimal.ZERO);
        }
        applyStatus(store);
        return store;
    }

    public Store refreshAndSave(Store store) {
        refresh(store);
        return stores.save(store);
    }

    public boolean canReceiveOrders(Store store) {
        refresh(store);
        return !store.isBlockedForBilling();
    }

    public Store recordOrder(Store store) {
        refresh(store);
        store.setCurrentMonthOrders(Math.max(0, store.getCurrentMonthOrders()) + 1);
        applyStatus(store);
        return stores.save(store);
    }

    public Store setUsageForTesting(Store store, int used) {
        refresh(store);
        store.setCurrentMonthOrders(Math.max(0, used));
        applyStatus(store);
        return stores.save(store);
    }

    public Map<String, Object> summary(Store store) {
        refresh(store);
        int limit = normalizedLimit(store);
        int used = Math.max(0, store.getCurrentMonthOrders());
        int remaining = Math.max(0, limit - used);
        return Map.of(
                "planType", store.getPlanType(),
                "monthlyOrderLimit", limit,
                "currentMonthOrders", used,
                "ordersRemaining", remaining,
                "billingStatus", store.getBillingStatus(),
                "blockedForBilling", store.isBlockedForBilling(),
                "billingMonth", store.getBillingMonth(),
                "amountDue", store.getAmountDue() == null ? BigDecimal.ZERO : store.getAmountDue()
        );
    }

    private void ensureDefaults(Store store) {
        if (store.getPlanType() == null || store.getPlanType().isBlank()) store.setPlanType("FREE");
        if (store.getMonthlyOrderLimit() <= 0) store.setMonthlyOrderLimit(FREE_LIMIT);
        if (store.getBillingStatus() == null || store.getBillingStatus().isBlank()) store.setBillingStatus("OK");
        if (store.getAmountDue() == null) store.setAmountDue(BigDecimal.ZERO);
    }

    private void applyStatus(Store store) {
        String plan = store.getPlanType() == null ? "FREE" : store.getPlanType().trim().toUpperCase();
        int used = Math.max(0, store.getCurrentMonthOrders());
        int limit = normalizedLimit(store);

        if (!"FREE".equals(plan)) {
            store.setBillingStatus("OK");
            store.setBlockedForBilling(false);
            return;
        }

        if (used >= limit) {
            store.setBillingStatus("LIMIT_REACHED");
            store.setBlockedForBilling(true);
        } else if (used >= Math.min(NEAR_LIMIT_AT, limit)) {
            store.setBillingStatus("NEAR_LIMIT");
            store.setBlockedForBilling(false);
        } else {
            store.setBillingStatus("OK");
            store.setBlockedForBilling(false);
        }
    }

    private int normalizedLimit(Store store) {
        return store.getMonthlyOrderLimit() > 0 ? store.getMonthlyOrderLimit() : FREE_LIMIT;
    }
}
