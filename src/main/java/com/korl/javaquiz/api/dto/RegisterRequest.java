package com.korl.javaquiz.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    // Messages spelled out rather than left to the default bundle: the default is resolved
    // against the JVM locale, so the same bad password read differently on a developer's
    // machine and on the server.
    @Email(message = "Email must be a valid address")
    @NotBlank(message = "Email must not be empty")
    public String email;

    @NotBlank(message = "Password must not be empty")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    public String password;

    @Size(max = 80, message = "Display name must be at most 80 characters")
    public String displayName;
}
