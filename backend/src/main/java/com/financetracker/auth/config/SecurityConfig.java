package com.financetracker.auth.config;

import com.financetracker.auth.filter.JwtAuthenticationFilter;
import com.financetracker.common.filter.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static com.financetracker.access.model.Permission.AUDIT_READ;
import static com.financetracker.access.model.Permission.CATEGORY_ADMIN;
import static com.financetracker.access.model.Permission.CATEGORY_READ;
import static com.financetracker.access.model.Permission.CATEGORY_WRITE;
import static com.financetracker.access.model.Permission.CSV_IMPORT;
import static com.financetracker.access.model.Permission.DASHBOARD_VIEW;
import static com.financetracker.access.model.Permission.FEATURE_MANAGE;
import static com.financetracker.access.model.Permission.RECEIPT_SCAN;
import static com.financetracker.access.model.Permission.ROLE_MANAGE;
import static com.financetracker.access.model.Permission.TRANSACTION_READ;
import static com.financetracker.access.model.Permission.TRANSACTION_WRITE;
import static com.financetracker.access.model.Permission.USER_CREATE;
import static com.financetracker.access.model.Permission.USER_READ;
import static com.financetracker.access.model.Permission.USER_SUSPEND;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final com.financetracker.auth.config.UserDetailsConfig userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(request -> {
                var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                corsConfig.setAllowedOrigins(java.util.List.of(
                    "http://localhost:5173", "http://localhost:3000",
                    "capacitor://localhost", "file://"));
                corsConfig.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                corsConfig.setAllowedHeaders(java.util.List.of("*"));
                corsConfig.setExposedHeaders(java.util.List.of("X-RateLimit-Remaining", "Sunset"));
                corsConfig.setAllowCredentials(true);
                return corsConfig;
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/me").authenticated()
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Spring Boot forwards errors (including 403 from the rules below) to /error; if /error itself
                // required a login, every "forbidden" would reach the client as 401 and look like an expired session.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                // Domain access (ADR-018): each domain's URLs need a permission from the role table.
                // Rules are checked top to bottom, so specific paths come before broader ones.
                .requestMatchers("/api/v1/csv/**").hasAuthority(CSV_IMPORT.name())
                .requestMatchers("/api/v1/ocr/**").hasAuthority(RECEIPT_SCAN.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/summary").hasAuthority(DASHBOARD_VIEW.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/**").hasAuthority(TRANSACTION_READ.name())
                .requestMatchers("/api/v1/transactions/**").hasAuthority(TRANSACTION_WRITE.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").hasAuthority(CATEGORY_READ.name())
                .requestMatchers("/api/v1/categories/**").hasAuthority(CATEGORY_WRITE.name())

                // Administration
                .requestMatchers("/api/v1/admin/users/*/features/**").hasAuthority(FEATURE_MANAGE.name())
                .requestMatchers(HttpMethod.PUT, "/api/v1/admin/users/*/role").hasAuthority(ROLE_MANAGE.name())
                .requestMatchers(HttpMethod.PUT, "/api/v1/admin/users/*/status").hasAuthority(USER_SUSPEND.name())
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/users").hasAuthority(USER_CREATE.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/**", "/api/v1/admin/roles/**", "/api/v1/admin/permissions")
                    .hasAuthority(USER_READ.name())
                .requestMatchers("/api/v1/admin/roles/**").hasAuthority(ROLE_MANAGE.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/audit/**").hasAuthority(AUDIT_READ.name())
                .requestMatchers("/api/v1/admin/categories/**").hasAuthority(CATEGORY_ADMIN.name())
                .requestMatchers("/api/v1/admin/**").denyAll()

                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Missing/expired token → 401 (not Spring's default 403) so clients know to refresh the token
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class)
            .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Check disabled/locked only after the password matched, so the login form can't be used
        // to find out which emails belong to disabled accounts.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
