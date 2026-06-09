package com.orderdesk.api.controller;

import com.orderdesk.api.dto.ProductRequest;
import com.orderdesk.api.model.Product;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductRepository products;
    private final StoreRepository stores;

    public ProductController(ProductRepository products, StoreRepository stores) {
        this.products = products;
        this.stores = stores;
    }

    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<?> listByStoreId(@PathVariable Long storeId) {
        if (stores.findById(storeId).isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        return ResponseEntity.ok(products.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @GetMapping("/stores/slug/{slug}/products")
    public ResponseEntity<?> listBySlug(@PathVariable String slug) {
        var store = stores.findBySlugIgnoreCase(slug).filter(s -> s.isActive());
        if (store.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        return ResponseEntity.ok(products.findByStoreIdAndAvailableTrueOrderByCreatedAtDesc(store.get().getId()));
    }

    @PostMapping("/stores/{storeId}/products")
    public ResponseEntity<?> create(@PathVariable Long storeId, @RequestBody ProductRequest request) {
        var optStore = stores.findById(storeId);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        if (request.ownerId == null || !request.ownerId.equals(optStore.get().getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode cadastrar produto nesta loja."));
        }
        if (request.name == null || request.name.isBlank() || request.price == null) {
            return ResponseEntity.badRequest().body(message("Nome e preço são obrigatórios."));
        }
        Product p = new Product();
        p.setStoreId(storeId);
        apply(p, request);
        return ResponseEntity.ok(products.save(p));
    }

    @PutMapping("/stores/{storeId}/products/{productId}")
    public ResponseEntity<?> update(@PathVariable Long storeId, @PathVariable Long productId, @RequestBody ProductRequest request) {
        var optStore = stores.findById(storeId);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        if (request.ownerId == null || !request.ownerId.equals(optStore.get().getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode editar produto nesta loja."));
        }
        var optProduct = products.findById(productId);
        if (optProduct.isEmpty() || !optProduct.get().getStoreId().equals(storeId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Produto não encontrado."));
        }
        Product p = optProduct.get();
        apply(p, request);
        return ResponseEntity.ok(products.save(p));
    }



    @PatchMapping("/products/{productId}/availability")
    public ResponseEntity<?> setAvailability(@PathVariable Long productId, @RequestParam Long ownerId, @RequestParam boolean available) {
        var optProduct = products.findById(productId);
        if (optProduct.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Produto não encontrado."));
        Product p = optProduct.get();
        var optStore = stores.findById(p.getStoreId());
        if (optStore.isEmpty() || !ownerId.equals(optStore.get().getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode alterar este produto."));
        }
        p.setAvailable(available);
        return ResponseEntity.ok(products.save(p));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<?> delete(@PathVariable Long productId, @RequestParam Long ownerId) {
        var optProduct = products.findById(productId);
        if (optProduct.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Produto não encontrado."));
        Product p = optProduct.get();
        var optStore = stores.findById(p.getStoreId());
        if (optStore.isEmpty() || !ownerId.equals(optStore.get().getOwnerId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Você não pode excluir este produto."));
        }
        products.delete(p);
        return ResponseEntity.ok(message("Produto excluído."));
    }

    private void apply(Product p, ProductRequest request) {
        p.setName(clean(request.name));
        p.setDescription(clean(request.description));
        p.setPrice(request.price == null ? BigDecimal.ZERO : request.price);
        p.setImageUrl(cleanImage(request.imageUrl));
        p.setCategory(clean(request.category));
        p.setAvailable(request.available == null || request.available);
        p.setFeatured(request.featured != null && request.featured);
        p.setPromotional(request.promotional != null && request.promotional);
    }

    private String clean(String v) { return v == null ? null : v.trim(); }
    private Map<String, String> message(String text) { return Map.of("message", text); }
    private String cleanImage(String v) {
        if (v == null || v.isBlank()) return null;
        String clean = v.trim();
        if (clean.length() > 280000) throw new IllegalArgumentException("Imagem muito grande.");
        return clean;
    }
}
