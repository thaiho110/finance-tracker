package com.financetracker.auth.api;

import com.financetracker.auth.dto.AuthRequest;
import com.financetracker.auth.dto.AuthResponse;
import com.financetracker.auth.dto.RegisterRequest;
import com.financetracker.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Auth API Integration Tests")
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should register and login successfully")
    void shouldRegisterAndLogin() {
        // Register
        var register = new RegisterRequest("testuser@example.com", "password123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
            "/api/v1/auth/register", register, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().email()).isEqualTo("testuser@example.com");
        assertThat(registerResponse.getBody().token()).isNotBlank();

        // Login
        var login = new AuthRequest("testuser@example.com", "password123");
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
            "/api/v1/auth/login", login, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().token()).isNotBlank();
    }

    @Test
    @DisplayName("Should return 401 for invalid login")
    void shouldReturn401ForInvalidLogin() {
        var login = new AuthRequest("nonexistent@example.com", "wrongpassword");
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", login, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
