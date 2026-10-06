package com.financetracker.category.api;

import com.financetracker.auth.dto.AuthRequest;
import com.financetracker.auth.dto.AuthResponse;
import com.financetracker.common.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("Category API Integration Tests")
class CategoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String authToken;

    @BeforeEach
    void setUp() {
        // Register or login
        var register = new com.financetracker.auth.dto.RegisterRequest("catuser@example.com", "password123");
        ResponseEntity<AuthResponse> regResponse = restTemplate.postForEntity(
            "/api/v1/auth/register", register, AuthResponse.class);

        if (regResponse.getStatusCode() == HttpStatus.OK && regResponse.getBody() != null) {
            authToken = regResponse.getBody().token();
        } else {
            var login = new AuthRequest("catuser@example.com", "password123");
            ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login", login, AuthResponse.class);
            authToken = loginResponse.getBody() != null ? loginResponse.getBody().token() : "";
        }
    }

    @Test
    @DisplayName("Should list categories from seeded data")
    void shouldListCategories() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        var entity = new HttpEntity<>(headers);

        ResponseEntity<List<String>> response = restTemplate.exchange(
            "/api/v1/categories", HttpMethod.GET, entity,
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()).contains("Coffee Shop", "Fast Food", "Transportation");
    }

    @Test
    @DisplayName("Should list category mappings from seeded data")
    void shouldListMappings() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        var entity = new HttpEntity<>(headers);

        ResponseEntity<List<?>> response = restTemplate.exchange(
            "/api/v1/categories/mappings", HttpMethod.GET, entity,
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }
}
