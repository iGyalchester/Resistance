package com.resistance.mvc.api;

import com.resistance.shared.models.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create/update body. Sizes mirror the column widths so a too-long value
 * is a 400 with a field name rather than a database error. contactId must
 * be one of the caller's own contacts; appliedOn is honoured on create only
 * (intake sets it from the email, and edits keep the original date).
 */
public record ApplicationRequest(
        @NotBlank(message = "required") @Size(max = 90, message = "at most 90 characters") String companyName,
        @Size(max = 90, message = "at most 90 characters") String positionTitle,
        @NotNull(message = "required") ApplicationStatus status,
        Integer contactId,
        LocalDate appliedOn) {
}
