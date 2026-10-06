package com.financetracker.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Finance Tracker API")
                .version("1.0.0")
                .description("""
                    REST API for the CSV + Receipt OCR Finance Tracker.
                    Supports CSV bank statement parsing, receipt OCR extraction,
                    transaction management, and DB-backed categorization.
                    """)
                .contact(new Contact().email("dev@financetracker.com").name("Finance Tracker Team"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local Development"),
                new Server().url("https://api.finance-tracker.com").description("Production")))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("JWT token from POST /api/v1/auth/login"))
                .addSecuritySchemes("api-key", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Api-Key")
                    .description("API key for dedicated device authentication")))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt").addList("api-key"));
    }
}
