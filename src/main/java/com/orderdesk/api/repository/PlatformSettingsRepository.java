package com.orderdesk.api.repository;

import com.orderdesk.api.model.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}
