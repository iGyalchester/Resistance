package com.resistance.etl.validators;

import com.resistance.etl.core.RecordValidator;
import com.resistance.etl.core.ValidationResult;
import com.resistance.shared.models.dto.JobApplicationDto;
import com.resistance.shared.models.entity.ApplicationStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JobApplicationRecordValidator implements RecordValidator<JobApplicationDto> {

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
        } else if (ApplicationStatus.fromString(record.getStatus()).isEmpty()) {
            errors.add("status must be one of "
                    + Arrays.toString(ApplicationStatus.values()) + ": " + record.getStatus());
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
