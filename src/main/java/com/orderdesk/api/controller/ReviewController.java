package com.orderdesk.api.controller;

import com.orderdesk.api.dto.ReviewRequest;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.StoreReview;
import com.orderdesk.api.repository.StoreRepository;
import com.orderdesk.api.repository.StoreReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stores")
public class ReviewController {
    private final StoreRepository stores;
    private final StoreReviewRepository reviews;

    public ReviewController(StoreRepository stores, StoreReviewRepository reviews) {
        this.stores = stores;
        this.reviews = reviews;
    }

    @GetMapping("/{storeId}/reviews")
    public ResponseEntity<?> list(@PathVariable Long storeId) {
        var store = stores.findById(storeId);
        if (store.isEmpty() || !store.get().isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        }
        return ResponseEntity.ok(reviews.findByStoreIdOrderByCreatedAtDesc(storeId));
    }

    @PostMapping("/{storeId}/reviews")
    public ResponseEntity<?> create(@PathVariable Long storeId, @RequestBody ReviewRequest request) {
        var optStore = stores.findById(storeId);
        if (optStore.isEmpty() || !optStore.get().isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message("Loja não encontrada."));
        }
        if (request.rating == null || request.rating < 1 || request.rating > 5) {
            return ResponseEntity.badRequest().body(message("A nota precisa ser de 1 a 5."));
        }

        String visitorKey = clean(request.visitorKey, "");
        if (visitorKey.isBlank()) {
            return ResponseEntity.badRequest().body(message("Não foi possível identificar o visitante."));
        }

        var existing = reviews.findByStoreIdAndVisitorKey(storeId, visitorKey);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Você já avaliou essa loja."));
        }

        StoreReview review = new StoreReview();
        review.setStoreId(storeId);
        review.setVisitorKey(visitorKey);
        review.setCustomerName(clean(request.customerName, "Cliente"));
        review.setRating(request.rating);
        review.setComment(clean(request.comment, ""));
        StoreReview saved = reviews.save(review);

        Store store = optStore.get();
        List<StoreReview> allReviews = reviews.findByStoreIdOrderByCreatedAtDesc(storeId);
        int count = allReviews.size();
        double avg = allReviews.stream().mapToInt(StoreReview::getRating).average().orElse(0.0);
        store.setReviewCount(count);
        store.setRatingAverage(Math.round(avg * 10.0) / 10.0);
        stores.save(store);

        return ResponseEntity.ok(saved);
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isBlank()) return fallback;
        return value.trim();
    }

    private Map<String, String> message(String text) {
        return Map.of("message", text);
    }
}
