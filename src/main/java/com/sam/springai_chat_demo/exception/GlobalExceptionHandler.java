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

    /**
     * Anything not handled above. The full stack trace is logged server-side; the response
     * carries a one-line summary (exception type + first line of the message) so the client
     * sees the same gist as the console instead of an opaque message.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error handling request", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, summarize(e));
        problem.setTitle("Internal server error");
        return problem;
    }

    /** e.g. {@code "NullPointerException: Cannot invoke ..."} — type plus the message's first line. */
    private static String summarize(Throwable e) {
        String firstLine = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
        return firstLine.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + firstLine;
    }
}
