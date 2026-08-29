package com.resistance.etl.validators;

import com.resistance.etl.core.RecordValidator;
import com.resistance.etl.core.ValidationResult;
import com.resistance.shared.models.dto.JobApplicationDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JobApplicationRecordValidator implements RecordValidator<JobApplicationDto> {

    private static final Set<String> VALID_STATUSES =
            Set.of("applied", "screening", "interview", "offer", "rejected", "accepted", "withdrawn");

    @Override
    public ValidationResult validate(JobApplicationDto record) {
        List<String> errors = new ArrayList<>();

        if (isBlank(record.getCompanyName())) {
            errors.add("company name is required");
        }
        if (isBlank(record.getPositionTitle())) {
            errors.add("position title is required");
        }
        if (isBlank(record.getStatus())) {
            errors.add("status is required");
        } else if (!VALID_STATUSES.contains(record.getStatus().trim().toLowerCase(Locale.ROOT))) {
            errors.add("status must be one of " + VALID_STATUSES + ": " + record.getStatus());
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
