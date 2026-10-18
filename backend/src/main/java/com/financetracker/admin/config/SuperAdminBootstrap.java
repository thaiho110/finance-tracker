package com.financetracker.admin.config;

import com.financetracker.access.config.AccessProperties;
import com.financetracker.access.model.Role;
import com.financetracker.admin.model.AuditAction;
import com.financetracker.admin.service.AuditService;
import com.financetracker.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure the account named by {@code APP_SUPER_ADMIN_EMAIL} is an enabled super admin (ADR-018).
 * New registrations with that email already get the role; this covers an account that existed before
 * the setting was added, or one that was locked out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final AccessProperties properties;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.getSuperAdminEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.strip())
            .filter(user -> !Role.SUPER_ADMIN.equals(user.getRole()) || !user.isEnabled())
            .ifPresent(user -> {
                String previous = user.getRole();
                user.setRole(Role.SUPER_ADMIN);
                user.setEnabled(true);
                userRepository.save(user);
                auditService.record(null, AuditAction.SUPER_ADMIN_BOOTSTRAPPED, user.getEmail(),
                    previous + " -> " + Role.SUPER_ADMIN + " (APP_SUPER_ADMIN_EMAIL)");
                log.info("Promoted {} to {} from APP_SUPER_ADMIN_EMAIL", user.getEmail(), Role.SUPER_ADMIN);
            });
    }
}
