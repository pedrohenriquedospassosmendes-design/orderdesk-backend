package com.orderdesk.api.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class FeeService {
    public BigDecimal platformFee(BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (subtotal.compareTo(new BigDecimal("30")) <= 0) return new BigDecimal("0.75");
        if (subtotal.compareTo(new BigDecimal("60")) <= 0) return new BigDecimal("1.50");
        if (subtotal.compareTo(new BigDecimal("100")) <= 0) return new BigDecimal("2.50");
        return new BigDecimal("3.50");
    }
}
