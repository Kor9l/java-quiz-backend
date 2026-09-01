package com.korl.javaquiz.api.dto;

import jakarta.validation.constraints.NotBlank;

public class WordRequest {

    @NotBlank(message = "Word must not be empty")
    public String text;

    @NotBlank(message = "Translation must not be empty")
    public String translation;

    public String example;

    public boolean isNew;
}
