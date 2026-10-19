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
            // 保持与 QuestionResponse 相同的结构，Electron 才能正确显示 REQUEST_TOO_LARGE。
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"请求体过大，请缩短问题或减少附件\",\"retryable\":false}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
