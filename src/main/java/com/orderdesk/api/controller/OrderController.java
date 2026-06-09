package com.orderdesk.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderdesk.api.dto.OrderRequest;
import com.orderdesk.api.model.CustomerOrder;
import com.orderdesk.api.model.Product;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.service.BillingService;
import com.orderdesk.api.service.FeeService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;
    private final FeeService feeService;
    private final BillingService billingService;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderController(StoreRepository stores, ProductRepository products, CustomerOrderRepository orders, FeeService feeService, BillingService billingService) {
        this.stores = stores;
        this.products = products;
        this.orders = orders;
        this.feeService = feeService;
        this.billingService = billingService;
    }

    @PostMapping("/stores/slug/{slug}/orders")
    @Transactional
    public ResponseEntity<?> create(@PathVariable String slug, @RequestBody OrderRequest request) throws JsonProcessingException {
        Optional<Store> optStore = stores.findBySlugIgnoreCase(slug).filter(Store::isActive);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        Store store = optStore.get();
        if (!billingService.canReceiveOrders(store)) {
            billingService.refreshAndSave(store);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Esta loja não está recebendo pedidos no momento."));
        }
        if (!storeCanReceiveOrders(store)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message(storeClosedMessage(store)));
        }

        if (request.items == null || request.items.isEmpty()) {
            return ResponseEntity.badRequest().body(message("Pedido sem itens."));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Map<String, Object>> itemList = new ArrayList<>();

        for (OrderRequest.OrderItemRequest item : request.items) {
            if (item.productId == null || item.quantity == null || item.quantity <= 0) continue;
            Optional<Product> optProduct = products.findById(item.productId);
            if (optProduct.isEmpty()) continue;
            Product product = optProduct.get();
            if (!product.getStoreId().equals(store.getId()) || !product.isAvailable()) continue;

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.quantity));
            subtotal = subtotal.add(lineTotal);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", product.getId());
            row.put("name", product.getName());
            row.put("quantity", item.quantity);
            row.put("unitPrice", product.getPrice());
            row.put("total", lineTotal);
            itemList.add(row);
        }

        if (itemList.isEmpty()) return ResponseEntity.badRequest().body(message("Nenhum item válido no pedido."));

        BigDecimal minimum = store.getMinimumOrderAmount() == null ? BigDecimal.ZERO : store.getMinimumOrderAmount();
        if (minimum.compareTo(BigDecimal.ZERO) > 0 && subtotal.compareTo(minimum) < 0) {
            return ResponseEntity.badRequest().body(message("Pedido mínimo desta loja: R$ " + minimum + "."));
        }
        String deliveryType = cleanDeliveryType(request.deliveryType);
        BigDecimal delivery = "PICKUP".equals(deliveryType) ? BigDecimal.ZERO : (store.getDeliveryFee() == null ? BigDecimal.ZERO : store.getDeliveryFee());
        BigDecimal platformFee = feeService.platformFee(subtotal);
        BigDecimal total = subtotal.add(delivery).add(platformFee);

        CustomerOrder order = new CustomerOrder();
        order.setStoreId(store.getId());
        order.setCustomerName(clean(request.customerName));
        order.setCustomerPhone(clean(request.customerPhone));
        order.setCustomerAddress(clean(request.customerAddress));
        order.setCustomerEmail(clean(request.customerEmail));
        order.setCustomerAccountId(request.customerAccountId);
        order.setPaymentMethod(clean(request.paymentMethod));
        order.setDeliveryType(deliveryType);
        order.setCustomerDistrict(clean(request.customerDistrict));
        order.setCustomerNumber(clean(request.customerNumber));
        order.setCustomerComplement(clean(request.customerComplement));
        order.setCustomerReference(clean(request.customerReference));
        order.setNotes(clean(request.notes));
        order.setItemsJson(mapper.writeValueAsString(itemList));
        order.setSubtotal(subtotal);
        order.setDeliveryFee(delivery);
        order.setPlatformFee(platformFee);
        order.setTotal(total);
        order.setWhatsappUrl(buildContactUrl(store, order, itemList));

        CustomerOrder saved = orders.save(order);
        billingService.recordOrder(store);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/stores/{storeId}/orders")
    public ResponseEntity<?> list(@PathVariable Long storeId, @RequestParam Long ownerId) {
        var optStore = stores.findById(storeId);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        if (!ownerId.equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode ver os pedidos desta loja."));
        return ResponseEntity.ok(orders.findByStoreIdOrderByCreatedAtDesc(storeId));
    }



    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long orderId, @RequestParam Long ownerId, @RequestParam String status) {
        var optOrder = orders.findById(orderId);
        if (optOrder.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Pedido não encontrado."));
        CustomerOrder order = optOrder.get();
        var optStore = stores.findById(order.getStoreId());
        if (optStore.isEmpty() || !ownerId.equals(optStore.get().getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode alterar este pedido."));
        }
        String cleanStatus = status == null ? "novo" : status.trim().toLowerCase();
        java.util.Set<String> allowed = java.util.Set.of("novo", "confirmado", "preparando", "saiu_entrega", "finalizado", "cancelado", "cancelado_cliente");
        if (!allowed.contains(cleanStatus)) return ResponseEntity.badRequest().body(message("Status inválido."));
        order.setStatus(cleanStatus);
        return ResponseEntity.ok(orders.save(order));
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelByCustomer(@PathVariable Long orderId,
                                              @RequestParam(required = false) Long accountId,
                                              @RequestParam(required = false) String email) {
        var optOrder = orders.findById(orderId);
        if (optOrder.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Pedido não encontrado."));
        CustomerOrder order = optOrder.get();
        if (!belongsToCustomer(order, accountId, email)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Não foi possível cancelar este pedido."));
        }
        String status = order.getStatus() == null ? "novo" : order.getStatus().trim().toLowerCase();
        if (!Set.of("novo", "confirmado").contains(status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Este pedido já está em preparo e não pode mais ser cancelado pelo site. Entre em contato com a loja."));
        }
        order.setStatus("cancelado_cliente");
        return ResponseEntity.ok(orders.save(order));
    }

    private String buildContactUrl(Store store, CustomerOrder order, List<Map<String, Object>> items) {
        StringBuilder msg = new StringBuilder();
        msg.append("Olá! Novo pedido pelo OrderDesk%0A%0A");
        msg.append("Loja: ").append(store.getName()).append("%0A");
        msg.append("Cliente: ").append(orEmpty(order.getCustomerName())).append("%0A");
        msg.append("Telefone: ").append(orEmpty(order.getCustomerPhone())).append("%0A");
        msg.append("Tipo: ").append("PICKUP".equals(order.getDeliveryType()) ? "Retirada" : "Entrega").append("%0A");
        if ("PICKUP".equals(order.getDeliveryType())) {
            msg.append("Retirada na loja%0A");
        } else {
            msg.append("Endereço: ").append(orEmpty(order.getCustomerAddress())).append(", ").append(orEmpty(order.getCustomerNumber())).append("%0A");
            msg.append("Bairro: ").append(orEmpty(order.getCustomerDistrict())).append("%0A");
            if (order.getCustomerComplement() != null && !order.getCustomerComplement().isBlank()) msg.append("Complemento: ").append(order.getCustomerComplement()).append("%0A");
            if (order.getCustomerReference() != null && !order.getCustomerReference().isBlank()) msg.append("Referência: ").append(order.getCustomerReference()).append("%0A");
        }
        if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) msg.append("Email: ").append(order.getCustomerEmail()).append("%0A");
        msg.append("Pagamento: ").append(orEmpty(order.getPaymentMethod())).append("%0A%0A");
        msg.append("Itens:%0A");
        for (Map<String, Object> item : items) {
            msg.append(item.get("quantity")).append("x ").append(item.get("name")).append(" - R$ ").append(item.get("total")).append("%0A");
        }
        msg.append("%0ASubtotal: R$ ").append(order.getSubtotal());
        msg.append("%0AEntrega: R$ ").append(order.getDeliveryFee());
        msg.append("%0ATaxa: R$ ").append(order.getPlatformFee());
        msg.append("%0ATotal: R$ ").append(order.getTotal());
        String encoded = URLEncoder.encode(msg.toString().replace("%0A", "\n"), StandardCharsets.UTF_8);
        String type = store.getContactType() == null ? "WHATSAPP" : store.getContactType().trim().toUpperCase();
        String value = store.getContactValue() == null || store.getContactValue().isBlank() ? store.getWhatsapp() : store.getContactValue();
        value = value == null ? "" : value.trim();
        if ("INSTAGRAM".equals(type)) {
            String handle = value.replace("@", "").replaceAll("\\s+", "");
            return "https://instagram.com/" + handle;
        }
        if ("TELEGRAM".equals(type)) {
            String handle = value.replace("@", "").replaceAll("\\s+", "");
            return "https://t.me/" + handle;
        }
        if ("SITE".equals(type)) {
            String url = store.getWebsiteUrl() != null && !store.getWebsiteUrl().isBlank() ? store.getWebsiteUrl() : value;
            if (url == null || url.isBlank()) return null;
            return (url.startsWith("http://") || url.startsWith("https://")) ? url : "https://" + url;
        }
        if ("PHONE".equals(type)) {
            return "tel:" + value.replaceAll("\\D+", "");
        }
        String phone = value.replaceAll("\\D+", "");
        return "https://wa.me/" + phone + "?text=" + encoded;
    }

    private String clean(String v) { return v == null ? null : v.trim(); }
    private String orEmpty(String v) { return v == null ? "" : v; }
    private Map<String, String> message(String text) { return Map.of("message", text); }

    private String cleanDeliveryType(String value) {
        return "PICKUP".equalsIgnoreCase(value) ? "PICKUP" : "DELIVERY";
    }

    private boolean belongsToCustomer(CustomerOrder order, Long accountId, String email) {
        if (accountId != null && order.getCustomerAccountId() != null && accountId.equals(order.getCustomerAccountId())) return true;
        return email != null && order.getCustomerEmail() != null && email.trim().equalsIgnoreCase(order.getCustomerEmail().trim());
    }

    private boolean storeCanReceiveOrders(Store store) {
        if (store == null || !store.isActive()) return false;
        String status = store.getStoreStatus() == null ? "OPEN" : store.getStoreStatus().trim().toUpperCase();
        if ("PAUSED".equals(status) || "CLOSED".equals(status) || store.isForceClosed()) return false;
        if (store.isForceOpen()) return true;
        if (store.getOpeningTime() == null || store.getOpeningTime().isBlank() || store.getClosingTime() == null || store.getClosingTime().isBlank()) return true;
        try {
            DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
            boolean worksToday = switch (day) {
                case SATURDAY -> store.isOpenSaturday();
                case SUNDAY -> store.isOpenSunday();
                default -> store.isOpenWeekdays();
            };
            if (!worksToday) return false;
            LocalTime now = LocalTime.now();
            LocalTime open = LocalTime.parse(store.getOpeningTime());
            LocalTime close = LocalTime.parse(store.getClosingTime());
            if (close.isBefore(open)) return !now.isBefore(open) || !now.isAfter(close);
            return !now.isBefore(open) && !now.isAfter(close);
        } catch (Exception ignored) {
            return true;
        }
    }

    private String storeClosedMessage(Store store) {
        String status = store.getStoreStatus() == null ? "OPEN" : store.getStoreStatus().trim().toUpperCase();
        if ("PAUSED".equals(status)) return "Esta loja não está recebendo pedidos no momento.";
        return "Esta loja está fechada no momento.";
    }
}
