package com.korl.javaquiz.api.error;

import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps the shape the UI reads — {@code {"message": "..."}} — instead of the default violation
 * report, which the client would render as a bare "Request failed (400)".
 */
@Provider
@Priority(Priorities.USER)
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", message.isBlank() ? "Validation failed" : message))
                .build();
    }
}
