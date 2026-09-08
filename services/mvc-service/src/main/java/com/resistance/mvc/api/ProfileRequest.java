package com.resistance.mvc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank(message = "required") @Size(max = 90, message = "at most 90 characters") String fullName,
        @Size(max = 40, message = "at most 40 characters") String phone) {
}
