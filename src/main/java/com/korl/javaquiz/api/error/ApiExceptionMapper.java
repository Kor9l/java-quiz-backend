package com.korl.javaquiz.api.error;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException exception) {
        return Response.status(exception.getStatus())
                .entity(Map.of("message", exception.getMessage()))
                .build();
    }
}
