package com.orderdesk.api.dto;

public class AuthDtos {
    public record RegisterRequest(String name, String email, String password, String accountType, String avatarUrl, String countryCode, String countryName, String state, String city) {}
    public record LoginRequest(String email, String password) {}
    public record AuthResponse(Long userId, String name, String email, String token, String accountType, String avatarUrl, boolean darkMode, boolean reduceMotion, String countryCode, String countryName, String state, String city) {}
    public record ProfileUpdateRequest(String name, String email, String accountType, String avatarUrl, Boolean darkMode, Boolean reduceMotion, String countryCode, String countryName, String state, String city) {}
    public record DeleteAccountRequest(String password) {}
}
