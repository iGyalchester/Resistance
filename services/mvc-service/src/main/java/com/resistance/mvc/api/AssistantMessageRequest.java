package com.resistance.mvc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One chat message from the user. Capped so a pasted novel cannot become a bill. */
public record AssistantMessageRequest(@NotBlank @Size(max = 4000) String message) {
}
