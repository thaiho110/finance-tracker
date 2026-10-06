package com.financetracker.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class ClientIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientId = request.getHeader("X-Client-Id");
        if (clientId == null || clientId.isBlank()) {
            clientId = "unknown";
        }

        try {
            MDC.put("clientId", clientId);
            MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
            response.setHeader("X-Request-Id", MDC.get("requestId"));
            request.setAttribute("X-Client-Id", clientId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
