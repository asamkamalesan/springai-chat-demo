package com.sam.springai_chat_demo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralizes error handling for all controllers. Applies to the synchronous
 * {@code /chat} path; mid-stream errors on {@code /chat/stream} are handled
 * reactively in the controller, because the 200 response has already started
 * by the time they surface.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Auth, billing, and invalid-request errors from the Anthropic API. */
    @ExceptionHandler(NonTransientAiException.class)
    public ProblemDetail handleNonTransientAi(NonTransientAiException e) {
        log.warn("Anthropic API rejected the request: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("AI request error");
        return problem;
    }

    /** Anything unexpected. The detail is logged, not returned, to avoid leaking internals. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error handling request", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }
}
