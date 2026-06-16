package com.orderdesk.api.controller;

import com.orderdesk.api.model.PlatformSettings;
import com.orderdesk.api.repository.PlatformSettingsRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformSettingsRepository settings;

    public PlatformController(PlatformSettingsRepository settings) {
        this.settings = settings;
    }

    @GetMapping("/settings")
    public PlatformSettings settings() {
        return settings.findById(1L).orElseGet(() -> settings.save(new PlatformSettings()));
    }
}
