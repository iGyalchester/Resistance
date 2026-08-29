package com.resistance.etl.validators;

import com.resistance.etl.core.RecordValidator;
import com.resistance.etl.core.ValidationResult;
import com.resistance.shared.models.dto.EmployeeDto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EmployeeRecordValidator implements RecordValidator<EmployeeDto> {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");

    @Override
    public ValidationResult validate(EmployeeDto record) {
        List<String> errors = new ArrayList<>();

        if (isBlank(record.getFirstName())) {
            errors.add("first name is required");
        }
        if (isBlank(record.getLastName())) {
            errors.add("last name is required");
        }
        if (isBlank(record.getEmail())) {
            errors.add("email is required");
        } else if (!EMAIL_PATTERN.matcher(record.getEmail().trim()).matches()) {
            errors.add("email is not a valid address: " + record.getEmail());
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
