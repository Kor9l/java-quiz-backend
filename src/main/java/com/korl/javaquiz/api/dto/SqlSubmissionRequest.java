package com.korl.javaquiz.api.dto;

import jakarta.validation.constraints.NotBlank;

public class SqlSubmissionRequest {

    @NotBlank(message = "SQL must not be empty")
    public String sql;
}
