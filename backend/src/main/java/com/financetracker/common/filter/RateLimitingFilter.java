package com.financetracker.common.filter;

import com.financetracker.common.config.rate_limiting.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that enforces token-bucket rate limiting.
 * Runs after authentication so it can scope limits by user/API key.
 * Sets standard rate-limit headers on every response.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip rate limiting for actuator and Swagger endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract identity for bucket scoping
        String key = resolveKey(request);
        String clientId = resolveClientId(request);

        // Check rate limit
        boolean allowed = rateLimiter.tryConsume(key, clientId);
        long remaining = rateLimiter.remainingTokens(key, clientId);
        long resetTime = rateLimiter.resetTimeSeconds(key, clientId);

        // Set response headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(resolveCapacity(clientId)));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, remaining)));
        if (resetTime > 0) {
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
        }

        if (!allowed) {
            long retryAfter = Math.max(1, resetTime - System.currentTimeMillis() / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(buildRateLimitProblem(retryAfter));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolve the bucket key from the authenticated principal or IP.
     * Priority: JWT subject → API key ID → client IP.
     */
    private String resolveKey(HttpServletRequest request) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails user) {
            return user.getUsername();
        }
        // Fall back to API key identity or IP
        String apiKey = request.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey.hashCode();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static String resolveClientId(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-Id");
        return clientId != null ? clientId : "unknown";
    }

    private static long resolveCapacity(String clientId) {
        return switch (clientId) {
            case String s when s.startsWith("finance-tracker-mobile") -> 60;
            case String s when s.startsWith("finance-tracker-device") -> 30;
            default -> 100;
        };
    }

    private static String buildRateLimitProblem(long retryAfter) {
        return "{\n" +
            "  \"type\": \"about:blank\",\n" +
            "  \"title\": \"Too Many Requests\",\n" +
            "  \"status\": 429,\n" +
            "  \"detail\": \"You have exceeded the rate limit. Please wait before retrying.\",\n" +
            "  \"retryAfter\": " + retryAfter + "\n" +
            "}";
    }
}
