package com.example.qa.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        var started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("http request method={} path={} status={} requestId={} durationMs={}", request.getMethod(), request.getRequestURI(), response.getStatus(), requestId,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}
