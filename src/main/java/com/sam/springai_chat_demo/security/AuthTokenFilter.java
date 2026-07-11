package com.sam.springai_chat_demo.security;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates a static shared-secret bearer token on the {@code /chat} endpoints.
 * Clients send {@code Authorization: Bearer <token>}; the value is compared against
 * the {@code app.auth.token} property. Actuator and other paths are left open so
 * health checks keep working (see {@link #shouldNotFilter}).
 *
 * <p>Fails closed: if {@code app.auth.token} is unset, every guarded request is rejected.
 *
 * <p>Runs before Spring MVC, so it writes its own {@link ProblemDetail} response rather
 * than delegating to {@code GlobalExceptionHandler}. A future JWT swap would replace the
 * comparison here (or move to Spring Security's OAuth2 resource server).
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public AuthTokenFilter(@Value("${app.auth.token:}") String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    /** Only guard the chat endpoints; leave actuator/health and everything else open. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/chat");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (expectedToken.isBlank()) {
            log.error("app.auth.token is not configured — rejecting request to {}", request.getRequestURI());
            writeUnauthorized(request, response, "Server auth token is not configured.");
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(request, response,
                    "Missing or malformed Authorization header (expected 'Bearer <token>').");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (!constantTimeEquals(token, expectedToken)) {
            writeUnauthorized(request, response, "Invalid auth token.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getWriter(), problem);
    }

    /** Length-constant comparison to avoid leaking the token via timing. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
