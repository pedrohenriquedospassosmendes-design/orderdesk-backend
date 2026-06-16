package com.orderdesk.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderdesk.api.dto.OrderRequest;
import com.orderdesk.api.model.CustomerOrder;
import com.orderdesk.api.model.Product;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.UserAccountRepository;
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
    private final UserAccountRepository users;
    private final FeeService feeService;
    private final BillingService billingService;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrderController(StoreRepository stores, ProductRepository products, CustomerOrderRepository orders, UserAccountRepository users, FeeService feeService, BillingService billingService) {
        this.stores = stores;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.feeService = feeService;
        this.billingService = billingService;
    }

    @PostMapping("/stores/slug/{slug}/orders")
    @Transactional
    public ResponseEntity<?> create(@PathVariable String slug, @RequestBody OrderRequest request) throws JsonProcessingException {
        Optional<Store> optStore = stores.findBySlugIgnoreCase(slug).filter(Store::isActive);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = optStore.get();
        if (!billingService.canReceiveOrders(store)) {
            billingService.refreshAndSave(store);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Esta loja nao esta recebendo pedidos no momento."));
        }
        if (!storeCanReceiveOrders(store)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message(storeClosedMessage(store)));
        if (request.items == null || request.items.isEmpty()) return ResponseEntity.badRequest().body(message("Pedido sem itens."));

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
        if (itemList.isEmpty()) return ResponseEntity.badRequest().body(message("Nenhum item valido no pedido."));

        BigDecimal minimum = store.getMinimumOrderAmount() == null ? BigDecimal.ZERO : store.getMinimumOrderAmount();
        if (minimum.compareTo(BigDecimal.ZERO) > 0 && subtotal.compareTo(minimum) < 0) return ResponseEntity.badRequest().body(message("Pedido minimo desta loja: R$ " + minimum + "."));

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
    public ResponseEntity<?> list(@PathVariable Long storeId, @RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> optStore = stores.findById(storeId);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        if (!user.get().getId().equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode ver os pedidos desta loja."));
        return ResponseEntity.ok(orders.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long orderId, @RequestParam String token, @RequestParam String status) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<CustomerOrder> optOrder = orders.findById(orderId);
        if (optOrder.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Pedido nao encontrado."));
        CustomerOrder order = optOrder.get();
        Optional<Store> optStore = stores.findById(order.getStoreId());
        if (optStore.isEmpty() || !user.get().getId().equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode alterar este pedido."));
        String cleanStatus = status == null ? "novo" : status.trim().toLowerCase();
        Set<String> allowed = Set.of("novo", "confirmado", "preparando", "saiu_entrega", "finalizado", "cancelado", "cancelado_cliente");
        if (!allowed.contains(cleanStatus)) return ResponseEntity.badRequest().body(message("Status invalido."));
        order.setStatus(cleanStatus);
        return ResponseEntity.ok(orders.save(order));
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelByCustomer(@PathVariable Long orderId,
                                              @RequestParam(required = false) String token,
                                              @RequestParam(required = false) Long accountId,
                                              @RequestParam(required = false) String email) {
        Optional<CustomerOrder> optOrder = orders.findById(orderId);
        if (optOrder.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Pedido nao encontrado."));
        CustomerOrder order = optOrder.get();
        Optional<UserAccount> user = requireUser(token);
        boolean owner = user.isPresent() && order.getCustomerAccountId() != null && user.get().getId().equals(order.getCustomerAccountId());
        if (!owner && !belongsToCustomer(order, accountId, email)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Nao foi possivel cancelar este pedido."));
        String status = order.getStatus() == null ? "novo" : order.getStatus().trim().toLowerCase();
        if (!Set.of("novo", "confirmado").contains(status)) return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Este pedido ja esta em preparo e nao pode mais ser cancelado pelo site. Entre em contato com a loja."));
        order.setStatus("cancelado_cliente");
        return ResponseEntity.ok(orders.save(order));
    }

    private Optional<UserAccount> requireUser(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return users.findBySessionToken(token);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessao expirada. Entre novamente."));
    }

    private String buildContactUrl(Store store, CustomerOrder order, List<Map<String, Object>> items) {
        StringBuilder msg = new StringBuilder();
        msg.append("Ola! Novo pedido pelo OrderDesk\n\n");
        msg.append("Loja: ").append(store.getName()).append("\n");
        msg.append("Cliente: ").append(orEmpty(order.getCustomerName())).append("\n");
        msg.append("Telefone: ").append(orEmpty(order.getCustomerPhone())).append("\n");
        msg.append("Tipo: ").append("PICKUP".equals(order.getDeliveryType()) ? "Retirada" : "Entrega").append("\n");
        if ("PICKUP".equals(order.getDeliveryType())) {
            msg.append("Retirada na loja\n");
        } else {
            msg.append("Endereco: ").append(orEmpty(order.getCustomerAddress())).append(", ").append(orEmpty(order.getCustomerNumber())).append("\n");
            msg.append("Bairro: ").append(orEmpty(order.getCustomerDistrict())).append("\n");
            if (order.getCustomerComplement() != null && !order.getCustomerComplement().isBlank()) msg.append("Complemento: ").append(order.getCustomerComplement()).append("\n");
            if (order.getCustomerReference() != null && !order.getCustomerReference().isBlank()) msg.append("Referencia: ").append(order.getCustomerReference()).append("\n");
        }
        if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) msg.append("Email: ").append(order.getCustomerEmail()).append("\n");
        msg.append("Pagamento: ").append(orEmpty(order.getPaymentMethod())).append("\n\n");
        msg.append("Itens:\n");
        for (Map<String, Object> item : items) msg.append(item.get("quantity")).append("x ").append(item.get("name")).append(" - R$ ").append(item.get("total")).append("\n");
        msg.append("\nSubtotal: R$ ").append(order.getSubtotal());
        msg.append("\nEntrega: R$ ").append(order.getDeliveryFee());
        msg.append("\nTaxa: R$ ").append(order.getPlatformFee());
        msg.append("\nTotal: R$ ").append(order.getTotal());
        String encoded = URLEncoder.encode(msg.toString(), StandardCharsets.UTF_8);
        String type = store.getContactType() == null ? "WHATSAPP" : store.getContactType().trim().toUpperCase();
        String value = store.getContactValue() == null || store.getContactValue().isBlank() ? store.getWhatsapp() : store.getContactValue();
        value = value == null ? "" : value.trim();
        if ("INSTAGRAM".equals(type)) return "https://instagram.com/" + value.replace("@", "").replaceAll("\\s+", "");
        if ("TELEGRAM".equals(type)) return "https://t.me/" + value.replace("@", "").replaceAll("\\s+", "");
        if ("SITE".equals(type)) {
            String url = store.getWebsiteUrl() != null && !store.getWebsiteUrl().isBlank() ? store.getWebsiteUrl() : value;
            if (url == null || url.isBlank()) return null;
            return (url.startsWith("http://") || url.startsWith("https://")) ? url : "https://" + url;
        }
        if ("PHONE".equals(type)) return "tel:" + value.replaceAll("\\D+", "");
        return "https://wa.me/" + value.replaceAll("\\D+", "") + "?text=" + encoded;
    }

    private String clean(String v) { return v == null ? null : v.trim(); }
    private String orEmpty(String v) { return v == null ? "" : v; }
    private Map<String, String> message(String text) { return Map.of("message", text); }
    private String cleanDeliveryType(String value) { return "PICKUP".equalsIgnoreCase(value) ? "PICKUP" : "DELIVERY"; }

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
        if ("PAUSED".equals(status)) return "Esta loja nao esta recebendo pedidos no momento.";
        return "Esta loja esta fechada no momento.";
    }
}
