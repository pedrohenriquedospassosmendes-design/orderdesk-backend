package com.orderdesk.api.controller;

import com.orderdesk.api.dto.AuthDtos.*;
import com.orderdesk.api.model.Store;
import com.orderdesk.api.model.UserAccount;
import com.orderdesk.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final UserAccountRepository users;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final CustomerOrderRepository orders;
    private final StoreReviewRepository reviews;
    private final StoreLikeRepository likes;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserAccountRepository users, StoreRepository stores, ProductRepository products, CustomerOrderRepository orders, StoreReviewRepository reviews, StoreLikeRepository likes) {
        this.users = users;
        this.stores = stores;
        this.products = products;
        this.orders = orders;
        this.reviews = reviews;
        this.likes = likes;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.name() == null || request.name().isBlank()
                || request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().length() < 4) {
            return ResponseEntity.badRequest().body(message("Preencha nome, email e senha com pelo menos 4 caracteres."));
        }

        String email = request.email().trim().toLowerCase();
        if (!isValidEmail(email)) {
            return ResponseEntity.badRequest().body(message("Digite um email válido. Exemplo: nome@gmail.com"));
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Email já cadastrado. Clique em Entrar."));
        }

        UserAccount user = new UserAccount();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setAccountType(cleanAccountType(request.accountType()));
        user.setCountryCode(cleanCountryCode(request.countryCode()));
        user.setCountryName(cleanCountryName(request.countryName(), user.getCountryCode()));
        user.setState(clean(request.state()));
        user.setCity(clean(request.city()));
        user.setAvatarUrl(cleanImageValue(request.avatarUrl()));
        user.setSessionToken(newToken());
        user.setLastLoginAt(LocalDateTime.now());
        user = users.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(message("Digite email e senha."));
        }

        String email = request.email().trim().toLowerCase();
        if (!isValidEmail(email)) {
            return ResponseEntity.badRequest().body(message("Digite um email válido. Exemplo: nome@gmail.com"));
        }

        var opt = users.findByEmailIgnoreCase(email);
        if (opt.isEmpty() || !encoder.matches(request.password(), opt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Conta não existe ou senha incorreta."));
        }

        UserAccount user = opt.get();
        user.setSessionToken(newToken());
        user.setLastLoginAt(LocalDateTime.now());
        user = users.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessão inválida."));
        }
        return users.findBySessionToken(token)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(toResponse(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessão expirada. Entre novamente.")));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestParam String token, @RequestBody ProfileUpdateRequest request) {
        var opt = users.findBySessionToken(token);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessão expirada. Entre novamente."));

        UserAccount user = opt.get();
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(message("Nome é obrigatório."));
        }
        user.setName(request.name().trim());

        if (request.avatarUrl() != null) {
            user.setAvatarUrl(cleanImageValue(request.avatarUrl()));
        }
        if (request.darkMode() != null) user.setDarkMode(request.darkMode());
        if (request.reduceMotion() != null) user.setReduceMotion(request.reduceMotion());
        if (request.countryCode() != null) {
            user.setCountryCode(cleanCountryCode(request.countryCode()));
            user.setCountryName(cleanCountryName(request.countryName(), user.getCountryCode()));
        }
        if (request.state() != null) user.setState(clean(request.state()));
        if (request.city() != null) user.setCity(clean(request.city()));

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            if (!isValidEmail(email)) {
                return ResponseEntity.badRequest().body(message("Digite um email válido. Exemplo: nome@gmail.com"));
            }
            var existing = users.findByEmailIgnoreCase(email);
            if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(message("Esse email já está em uso."));
            }
            user.setEmail(email);
        }

        return ResponseEntity.ok(toResponse(users.save(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String token) {
        users.findBySessionToken(token).ifPresent(user -> {
            user.setSessionToken(null);
            users.save(user);
        });
        return ResponseEntity.ok(message("Sessão encerrada."));
    }

    @DeleteMapping("/account")
    @jakarta.transaction.Transactional
    public ResponseEntity<?> deleteAccount(@RequestParam String token, @RequestBody DeleteAccountRequest request) {
        var opt = users.findBySessionToken(token);
        if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message("Sessão expirada. Entre novamente."));
        UserAccount user = opt.get();
        if (request.password() == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message("Senha incorreta. A conta não foi excluída."));
        }
        for (Store store : stores.findByOwnerIdOrderByCreatedAtDesc(user.getId())) {
            Long id = store.getId();
            orders.deleteByStoreId(id);
            reviews.deleteByStoreId(id);
            likes.deleteByStoreId(id);
            products.deleteByStoreId(id);
            stores.delete(store);
        }
        users.delete(user);
        return ResponseEntity.ok(message("Conta excluída."));
    }

    private AuthResponse toResponse(UserAccount user) {
        return new AuthResponse(user.getId(), user.getName(), user.getEmail(), user.getSessionToken(),
                user.getAccountType(), user.getAvatarUrl(), user.isDarkMode(), user.isReduceMotion(),
                user.getCountryCode(), user.getCountryName(), user.getState(), user.getCity());
    }

    private Map<String, String> message(String text) {
        return Map.of("message", text);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanAccountType(String value) {
        if (value == null) return "store";
        String clean = value.trim().toLowerCase();
        return clean.equals("customer") ? "customer" : "store";
    }

    private String cleanCountryCode(String value) {
        if (value == null || value.isBlank()) return "BR";
        String clean = value.trim().toUpperCase();
        if (clean.length() > 3) return clean.substring(0, 3);
        return clean;
    }

    private String cleanCountryName(String value, String code) {
        if (value != null && !value.isBlank()) return value.trim();
        return "BR".equalsIgnoreCase(code) ? "Brasil" : code;
    }

    private String cleanImageValue(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 280000) throw new IllegalArgumentException("Imagem muito grande.");
        return clean;
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        String clean = email.trim();
        if (clean.length() < 6 || clean.length() > 120) return false;
        return EMAIL_PATTERN.matcher(clean).matches();
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
