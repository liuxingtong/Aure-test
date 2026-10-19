package com.example.qa.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSizeFilter extends OncePerRequestFilter {
    static final int MAX_REQUEST_BYTES = 1_048_576;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/v1/questions")
                && request.getMethod().equalsIgnoreCase("POST")
                && request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "请求体过大");
            return;
        }
        chain.doFilter(request, response);
    }
}
