package com.financetracker.auth.filter;

import com.financetracker.auth.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;

    @InjectMocks private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    @DisplayName("Should authenticate a request with a valid token")
    void authenticatesValidToken() throws Exception {
        UserDetails user = new User("test@example.com", "hash", List.of());
        when(jwtService.extractUsername("good")).thenReturn("test@example.com");
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(user);
        when(jwtService.isTokenValid("good", user)).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("good"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Should continue unauthenticated (→ 401) when the token is expired instead of failing")
    void continuesWhenTokenExpired() throws Exception {
        when(jwtService.extractUsername("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("expired"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("request continued down the chain").isNotNull();
    }

    @Test
    @DisplayName("Should continue unauthenticated when the token's user no longer exists")
    void continuesWhenUserMissing() throws Exception {
        when(jwtService.extractUsername("orphan")).thenReturn("gone@example.com");
        when(userDetailsService.loadUserByUsername("gone@example.com")).thenThrow(new UsernameNotFoundException("gone"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken("orphan"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
