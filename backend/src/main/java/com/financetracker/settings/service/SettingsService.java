package com.financetracker.settings.service;

import com.financetracker.auth.model.RegistrationMode;
import com.financetracker.settings.model.AppSetting;
import com.financetracker.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application-wide settings (ADR-016). The registration mode is stored in {@code app_settings} and changed by
 * the super admin at runtime. In dev, {@code app.registration.forced-mode} overrides it so local signups stay
 * open without touching the database.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String REGISTRATION_MODE = "registration_mode";

    private final AppSettingRepository settings;

    /** When set (dev profile), this mode is always used and the stored value is ignored. */
    @Value("${app.registration.forced-mode:}")
    private String forcedMode;

    @Transactional(readOnly = true)
    public RegistrationMode getRegistrationMode() {
        if (forcedMode != null && !forcedMode.isBlank()) {
            return RegistrationMode.valueOf(forcedMode.strip().toUpperCase());
        }
        return settings.findById(REGISTRATION_MODE)
            .map(setting -> RegistrationMode.valueOf(setting.getValue()))
            .orElse(RegistrationMode.INVITE_ONLY);
    }

    @Transactional
    public void setRegistrationMode(RegistrationMode mode) {
        AppSetting setting = settings.findById(REGISTRATION_MODE)
            .orElseGet(() -> new AppSetting(REGISTRATION_MODE, mode.name(), Instant.now()));
        setting.setValue(mode.name());
        setting.setUpdatedAt(Instant.now());
        settings.save(setting);
    }
}
