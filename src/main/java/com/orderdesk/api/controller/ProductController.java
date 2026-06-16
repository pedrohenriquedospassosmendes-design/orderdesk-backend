package com.orderdesk.api.controller;

import com.orderdesk.api.dto.ProductRequest;
import com.orderdesk.api.model.Product;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductRepository products;
    private final StoreRepository stores;
    private final UserAccountRepository users;

    public ProductController(ProductRepository products, StoreRepository stores, UserAccountRepository users) {
        this.products = products;
        this.stores = stores;
        this.users = users;
    }

    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<?> listByStoreId(@PathVariable Long storeId) {
        if (stores.findById(storeId).isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        return ResponseEntity.ok(products.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @GetMapping("/stores/slug/{slug}/products")
    public ResponseEntity<?> listBySlug(@PathVariable String slug) {
        Optional<Store> store = stores.findBySlugIgnoreCase(slug).filter(Store::isActive);
        if (store.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        return ResponseEntity.ok(products.findByStoreIdAndAvailableTrueOrderByCreatedAtDesc(store.get().getId()));
    }

    @PostMapping("/stores/{storeId}/products")
    public ResponseEntity<?> create(@PathVariable Long storeId, @RequestParam String token, @RequestBody ProductRequest request) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> optStore = stores.findById(storeId);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        if (!user.get().getId().equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode cadastrar produto nesta loja."));
        if (request.name == null || request.name.isBlank() || request.price == null) return ResponseEntity.badRequest().body(message("Nome e preco sao obrigatorios."));
        Product p = new Product();
        p.setStoreId(storeId);
        apply(p, request);
        return ResponseEntity.ok(products.save(p));
    }

    @PutMapping("/stores/{storeId}/products/{productId}")
    public ResponseEntity<?> update(@PathVariable Long storeId, @PathVariable Long productId, @RequestParam String token, @RequestBody ProductRequest request) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> optStore = stores.findById(storeId);
        if (optStore.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        if (!user.get().getId().equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode editar produto nesta loja."));
        Optional<Product> optProduct = products.findById(productId);
        if (optProduct.isEmpty() || !optProduct.get().getStoreId().equals(storeId)) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Produto nao encontrado."));
        Product p = optProduct.get();
        apply(p, request);
        return ResponseEntity.ok(products.save(p));
    }

    @PatchMapping("/products/{productId}/availability")
    public ResponseEntity<?> setAvailability(@PathVariable Long productId, @RequestParam String token, @RequestParam boolean available) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Product> optProduct = products.findById(productId);
        if (optProduct.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Produto nao encontrado."));
        Product p = optProduct.get();
        Optional<Store> optStore = stores.findById(p.getStoreId());
        if (optStore.isEmpty() || !user.get().getId().equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode alterar este produto."));
        p.setAvailable(available);
        return ResponseEntity.ok(products.save(p));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<?> delete(@PathVariable Long productId, @RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Product> optProduct = products.findById(productId);
        if (optProduct.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Produto nao encontrado."));
        Product p = optProduct.get();
        Optional<Store> optStore = stores.findById(p.getStoreId());
        if (optStore.isEmpty() || !user.get().getId().equals(optStore.get().getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode excluir este produto."));
        products.delete(p);
        return ResponseEntity.ok(message("Produto excluido."));
    }

    private Optional<UserAccount> requireUser(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return users.findBySessionToken(token);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessao expirada. Entre novamente."));
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
