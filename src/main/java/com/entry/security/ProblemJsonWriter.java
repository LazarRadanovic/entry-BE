package com.entry.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Writes ProblemDetail-shaped JSON from the security filter chain (outside Spring MVC). */
final class ProblemJsonWriter {

    private ProblemJsonWriter() {
    }

    /** Only call with constant, JSON-safe strings. */
    static void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
