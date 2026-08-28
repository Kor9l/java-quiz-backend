package com.korl.javaquiz.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @Email(message = "Email must be a valid address")
    @NotBlank(message = "Email must not be empty")
    public String email;

    @NotBlank(message = "Password must not be empty")
    public String password;
}
