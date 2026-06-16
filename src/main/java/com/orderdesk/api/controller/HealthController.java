package com.orderdesk.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "status", "online",
                "message", "OrderDesk backend online"
        );
    }
}
