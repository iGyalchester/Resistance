package com.resistance.mvc.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "required") @Size(max = 45, message = "at most 45 characters") String firstName,
        @Size(max = 45, message = "at most 45 characters") String lastName,
        @Email(message = "not an email address") @Size(max = 45, message = "at most 45 characters") String email) {
}
