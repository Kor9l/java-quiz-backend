package com.korl.javaquiz.api.error;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** Security refusals answer in the same {@code {"message": ...}} shape as every other error. */
public final class SecurityExceptionMappers {

    private SecurityExceptionMappers() {
    }

    @Provider
    public static class Unauthorized implements ExceptionMapper<UnauthorizedException> {
        @Override
        public Response toResponse(UnauthorizedException exception) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .entity(Map.of("message", "Unauthorized"))
                    .build();
        }
    }

    @Provider
    public static class Forbidden implements ExceptionMapper<ForbiddenException> {
        @Override
        public Response toResponse(ForbiddenException exception) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("message", "Forbidden"))
                    .build();
        }
    }
}
