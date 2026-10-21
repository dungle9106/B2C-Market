package com.marketplace.backend.config;

import com.marketplace.backend.service.RequestMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

// Times every /api request (security rejections included, so it runs first) for "Giam sat he thong".
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final RequestMetricsService metrics;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            // the route pattern ("/api/products/{id}") groups requests; ids in the raw path would not
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String endpoint = pattern != null ? pattern.toString() : request.getRequestURI().replaceAll("/\\d+(?=/|$)", "/{id}");
            metrics.record(request.getMethod(), endpoint, request.getRequestURI(), response.getStatus(), ms);
        }
    }
}
