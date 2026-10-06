package com.financetracker.transaction.api;

import com.financetracker.auth.dto.AuthRequest;
import com.financetracker.auth.dto.AuthResponse;
import com.financetracker.common.AbstractIntegrationTest;
import com.financetracker.transaction.dto.TransactionRequest;
import com.financetracker.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Transaction API Integration Tests")
class TransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String authToken;

    @BeforeEach
    void setUp() {
        // Register a test user
        var register = new com.financetracker.auth.dto.RegisterRequest("txuser@example.com", "password123");
        ResponseEntity<AuthResponse> regResponse = restTemplate.postForEntity(
            "/api/v1/auth/register", register, AuthResponse.class);

        // If already registered, login instead
        if (regResponse.getStatusCode() == HttpStatus.OK && regResponse.getBody() != null) {
            authToken = regResponse.getBody().token();
        } else {
            var login = new AuthRequest("txuser@example.com", "password123");
            ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login", login, AuthResponse.class);
            authToken = loginResponse.getBody() != null ? loginResponse.getBody().token() : "";
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.set("X-Client-Id", "finance-tracker-web");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Should create transactions via batch endpoint")
    void shouldBatchCreateTransactions() {
        var request = List.of(
            new TransactionRequest(LocalDate.of(2024, 1, 15), "STARBUCKS #12345",
                "STARBUCKS", BigDecimal.valueOf(12.50), "Coffee Shop", "CSV"),
            new TransactionRequest(LocalDate.of(2024, 1, 16), "AMAZON PURCHASE",
                "AMAZON", BigDecimal.valueOf(99.99), "Online Shopping", "CSV")
        );

        var entity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<List<TransactionResponse>> response = restTemplate.exchange(
            "/api/v1/transactions/batch", HttpMethod.POST, entity,
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("Should list transactions with pagination")
    void shouldListTransactions() {
        var entity = new HttpEntity<>(authHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/transactions?page=0&size=10", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should return 401 without auth token")
    void shouldReturn401WithoutAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/transactions", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
