package com.financetracker.access.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Access settings ({@code app.access.*}), see ADR-018. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.access")
public class AccessProperties {

    /**
     * Email of the first super admin ({@code APP_SUPER_ADMIN_EMAIL}). Registering with it creates a
     * SUPER_ADMIN; an existing account with it is promoted at startup. Blank disables both.
     */
    private String superAdminEmail = "";
}
