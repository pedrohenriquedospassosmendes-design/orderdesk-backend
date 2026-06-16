package com.orderdesk.api.controller;

import com.orderdesk.api.dto.StoreRequest;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.StoreLike;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.CustomerOrderRepository;
import com.orderdesk.api.repository.ProductRepository;
import com.orderdesk.api.repository.StoreLikeRepository;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.StoreReviewRepository;
import com.orderdesk.api.repository.UserAccountRepository;
import com.orderdesk.api.service.BillingService;
import com.orderdesk.api.service.SlugService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/stores")
public class StoreController {
    private final StoreRepository stores;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;
    private final StoreReviewRepository reviews;
    private final StoreLikeRepository likes;
    private final SlugService slugService;
    private final BillingService billingService;

    public StoreController(StoreRepository stores, UserAccountRepository users, ProductRepository products, CustomerOrderRepository orders, StoreReviewRepository reviews, StoreLikeRepository likes, SlugService slugService, BillingService billingService) {
        this.stores = stores;
        this.users = users;
        this.products = products;
        this.orders = orders;
        this.reviews = reviews;
        this.likes = likes;
        this.slugService = slugService;
        this.billingService = billingService;
    }

    @GetMapping
    public List<Store> list(@RequestParam(required = false) String q,
                            @RequestParam(required = false) String country,
                            @RequestParam(required = false) String state,
                            @RequestParam(required = false) String city,
                            @RequestParam(required = false) String userCountry,
                            @RequestParam(required = false) String userState,
                            @RequestParam(required = false) String userCity) {
        List<Store> result;
        if (q == null || q.isBlank()) {
            result = stores.findByActiveTrueOrderByCreatedAtDesc();
        } else {
            result = stores.findByActiveTrueAndNameContainingIgnoreCaseOrActiveTrueAndCategoryContainingIgnoreCaseOrActiveTrueAndCityContainingIgnoreCaseOrderByCreatedAtDesc(q, q, q);
        }
        result = result.stream().filter(store -> matches(store.getCountryCode(), country)
                && matches(store.getState(), state)
                && matches(store.getCity(), city)
                && !"PAUSED".equalsIgnoreCase(store.getStoreStatus())).toList();
        result = new java.util.ArrayList<>(result);
        result.sort((a, b) -> {
            int locationCompare = Integer.compare(locationScore(b, userCountry, userState, userCity), locationScore(a, userCountry, userState, userCity));
            if (locationCompare != 0) return locationCompare;
            int likesCompare = Integer.compare(b.getLikesCount(), a.getLikesCount());
            if (likesCompare != 0) return likesCompare;
            int ratingCompare = Double.compare(b.getRatingAverage(), a.getRatingAverage());
            if (ratingCompare != 0) return ratingCompare;
            long bid = b.getId() == null ? 0L : b.getId();
            long aid = a.getId() == null ? 0L : a.getId();
            return Long.compare(bid, aid);
        });
        return result;
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(@RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        return ResponseEntity.ok(stores.findByOwnerIdOrderByCreatedAtDesc(user.get().getId()).stream().map(billingService::refreshAndSave).toList());
    }

    @GetMapping("/mine/{ownerId}")
    public ResponseEntity<?> mineLegacy(@PathVariable Long ownerId, @RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        if (!user.get().getId().equals(ownerId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode ver lojas de outra conta."));
        return ResponseEntity.ok(stores.findByOwnerIdOrderByCreatedAtDesc(user.get().getId()).stream().map(billingService::refreshAndSave).toList());
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<?> bySlug(@PathVariable String slug) {
        return stores.findBySlugIgnoreCase(slug)
                .filter(Store::isActive)
                .<ResponseEntity<?>>map(store -> ResponseEntity.ok(billingService.refreshAndSave(store)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada.")));
    }

    @GetMapping("/{id}/billing")
    public ResponseEntity<?> billing(@PathVariable Long id, @RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (!user.get().getId().equals(store.getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode ver esta loja."));
        return ResponseEntity.ok(billingService.summary(billingService.refreshAndSave(store)));
    }

    @PatchMapping("/{id}/billing/test-usage")
    public ResponseEntity<?> setBillingUsageForTesting(@PathVariable Long id, @RequestParam String token, @RequestParam int used) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (!user.get().getId().equals(store.getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode alterar esta loja."));
        return ResponseEntity.ok(billingService.summary(billingService.setUsageForTesting(store, used)));
    }

    @PostMapping("/{id}/like")
    @Transactional
    public ResponseEntity<?> like(@PathVariable Long id, @RequestHeader(value = "X-User-Key", required = false) String userKey) {
        Optional<Store> opt = stores.findById(id);
        if (opt.isEmpty() || !opt.get().isActive()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        if (userKey == null || userKey.isBlank()) return ResponseEntity.badRequest().body(message("Nao foi possivel identificar sua curtida."));
        String cleanKey = userKey.trim();
        Store store = opt.get();
        if (likes.findByStoreIdAndUserKey(id, cleanKey).isPresent()) {
            store.setLikesCount((int) likes.countByStoreId(id));
            return ResponseEntity.ok(store);
        }
        likes.save(new StoreLike(id, cleanKey));
        store.setLikesCount((int) likes.countByStoreId(id));
        return ResponseEntity.ok(stores.save(store));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam String token, @RequestBody StoreRequest request) {
        Optional<UserAccount> owner = requireUser(token);
        if (owner.isEmpty()) return unauthorized();
        if (!"store".equalsIgnoreCase(owner.get().getAccountType())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Crie uma conta de loja para cadastrar uma loja."));
        }
        if (request.name == null || request.name.isBlank()) return ResponseEntity.badRequest().body(message("Nome da loja e obrigatorio."));
        if (stores.countByOwnerId(owner.get().getId()) >= 4) return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Limite de 4 lojas por conta atingido."));
        Store store = new Store();
        apply(store, request);
        store.setOwnerId(owner.get().getId());
        store.setSlug(slugService.cleanOrGenerate(request.slug, request.name, null));
        store.setActive(true);
        billingService.refresh(store);
        return ResponseEntity.ok(stores.save(store));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestParam String token, @RequestBody StoreRequest request) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (!user.get().getId().equals(store.getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode editar esta loja."));
        if (request.name == null || request.name.isBlank()) return ResponseEntity.badRequest().body(message("Nome da loja e obrigatorio."));
        apply(store, request);
        store.setSlug(slugService.cleanOrGenerate(request.slug, request.name, id));
        return ResponseEntity.ok(stores.save(store));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestParam String token, @RequestParam boolean active) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (!user.get().getId().equals(store.getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode alterar esta loja."));
        store.setActive(active);
        return ResponseEntity.ok(stores.save(store));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam String token) {
        Optional<UserAccount> user = requireUser(token);
        if (user.isEmpty()) return unauthorized();
        Optional<Store> opt = stores.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja nao encontrada."));
        Store store = opt.get();
        if (!user.get().getId().equals(store.getOwnerId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Voce nao pode excluir esta loja."));
        orders.deleteByStoreId(id);
        reviews.deleteByStoreId(id);
        likes.deleteByStoreId(id);
        products.deleteByStoreId(id);
        stores.delete(store);
        return ResponseEntity.ok().body(message("Loja excluida."));
    }

    private void apply(Store store, StoreRequest request) {
        store.setName(clean(request.name));
        store.setCategory(clean(request.category));
        store.setWhatsapp(onlyDigits(request.whatsapp));
        store.setContactType(cleanContactType(request.contactType));
        store.setContactValue(clean(request.contactValue));
        store.setWebsiteUrl(cleanUrl(request.websiteUrl));
        store.setDescription(clean(request.description));
        store.setLogoUrl(cleanImage(request.logoUrl));
        store.setBannerUrl(cleanImage(request.bannerUrl));
        store.setAddress(clean(request.address));
        store.setCity(clean(request.city));
        store.setState(clean(request.state));
        store.setCountryCode(cleanCountryCode(request.countryCode));
        store.setCountryName(cleanCountryName(request.countryName, store.getCountryCode()));
        store.setOpeningHours(clean(request.openingHours));
        store.setOpeningTime(clean(request.openingTime));
        store.setClosingTime(clean(request.closingTime));
        store.setOpenWeekdays(request.openWeekdays == null || request.openWeekdays);
        store.setOpenSaturday(request.openSaturday == null || request.openSaturday);
        store.setOpenSunday(request.openSunday != null && request.openSunday);
        store.setForceOpen(request.forceOpen != null && request.forceOpen);
        store.setForceClosed(request.forceClosed != null && request.forceClosed);
        store.setStoreStatus(cleanStoreStatus(request.storeStatus));
        store.setDeliveryTime(clean(request.deliveryTime));
        store.setDeliveryFee(request.deliveryFee == null ? BigDecimal.ZERO : request.deliveryFee);
        store.setMinimumOrderAmount(request.minimumOrderAmount == null ? BigDecimal.ZERO : request.minimumOrderAmount);
        store.setPaymentMethods(clean(request.paymentMethods));
    }

    private Optional<UserAccount> requireUser(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return users.findBySessionToken(token);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessao expirada. Entre novamente."));
    }

    private String clean(String v) { return v == null ? null : v.trim(); }

    private String cleanImage(String v) {
        if (v == null || v.isBlank()) return null;
        String clean = v.trim();
        if (clean.length() > 280000) throw new IllegalArgumentException("Imagem muito grande. Use uma imagem menor que 2 MB.");
        return clean;
    }

    private String onlyDigits(String v) { return v == null ? null : v.replaceAll("\\D+", ""); }

    private String cleanContactType(String v) {
        if (v == null || v.isBlank()) return "WHATSAPP";
        String type = v.trim().toUpperCase();
        return java.util.Set.of("WHATSAPP", "INSTAGRAM", "TELEGRAM", "PHONE", "SITE").contains(type) ? type : "WHATSAPP";
    }

    private String cleanStoreStatus(String v) {
        if (v == null || v.isBlank()) return "OPEN";
        String clean = v.trim().toUpperCase();
        return java.util.Set.of("OPEN", "CLOSED", "PAUSED").contains(clean) ? clean : "OPEN";
    }

    private String cleanUrl(String v) {
        if (v == null || v.isBlank()) return null;
        String clean = v.trim();
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean;
        return "https://" + clean;
    }

    private boolean matches(String value, String filter) {
        if (filter == null || filter.isBlank()) return true;
        return value != null && value.trim().equalsIgnoreCase(filter.trim());
    }

    private int locationScore(Store store, String userCountry, String userState, String userCity) {
        if (isFilled(userCountry) && isFilled(userState) && isFilled(userCity)
                && matches(store.getCountryCode(), userCountry) && matches(store.getState(), userState) && matches(store.getCity(), userCity)) return 3;
        if (isFilled(userCountry) && isFilled(userState)
                && matches(store.getCountryCode(), userCountry) && matches(store.getState(), userState)) return 2;
        if (isFilled(userCountry) && matches(store.getCountryCode(), userCountry)) return 1;
        return 0;
    }

    private boolean isFilled(String value) { return value != null && !value.isBlank(); }
    private Map<String, String> message(String text) { return Map.of("message", text); }

    private String cleanCountryCode(String value) {
        if (value == null || value.isBlank()) return "BR";
        String clean = value.trim().toUpperCase();
        return clean.length() > 3 ? clean.substring(0, 3) : clean;
    }

    private String cleanCountryName(String value, String code) {
        if (value != null && !value.isBlank()) return value.trim();
        return "BR".equalsIgnoreCase(code) ? "Brasil" : code;
    }
}
