package com.korl.javaquiz.api.error;

import jakarta.ws.rs.core.Response.Status;

public class ApiException extends RuntimeException {

    private final Status status;

    public ApiException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
