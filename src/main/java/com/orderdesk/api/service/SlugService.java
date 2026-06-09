package com.orderdesk.api.service;

import com.orderdesk.api.repository.StoreRepository;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.Locale;

@Service
public class SlugService {
    private final StoreRepository storeRepository;

    public SlugService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public String generate(String raw) {
        String base = slugify(raw == null || raw.isBlank() ? "loja" : raw);
        String candidate = base;
        int count = 2;
        while (storeRepository.existsBySlugIgnoreCase(candidate)) {
            candidate = base + "-" + count;
            count++;
        }
        return candidate;
    }

    public String cleanOrGenerate(String slug, String name, Long editingId) {
        String base = slugify(slug == null || slug.isBlank() ? name : slug);
        if (base.isBlank()) base = "loja";
        String candidate = base;
        int count = 2;
        while (true) {
            var found = storeRepository.findBySlugIgnoreCase(candidate);
            if (found.isEmpty() || (editingId != null && found.get().getId().equals(editingId))) return candidate;
            candidate = base + "-" + count;
            count++;
        }
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replaceAll("-{2,}", "-");
    }
}
