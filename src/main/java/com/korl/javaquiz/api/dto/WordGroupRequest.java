package com.korl.javaquiz.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WordGroupRequest {

    @NotBlank(message = "Group title must not be empty")
    @Size(max = 255, message = "Group title must be at most 255 characters")
    public String title;
}
