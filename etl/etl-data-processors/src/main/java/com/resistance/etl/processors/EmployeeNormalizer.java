package com.resistance.etl.processors;

import com.resistance.etl.core.Transformer;
import com.resistance.shared.models.dto.EmployeeDto;
import com.resistance.shared.utils.NameUtils;

/**
 * Cleans up raw CSV input: title-cases names and lowercases emails.
 */
public class EmployeeNormalizer implements Transformer<EmployeeDto, EmployeeDto> {

    @Override
    public EmployeeDto transform(EmployeeDto input) {
        return new EmployeeDto(
                NameUtils.toTitleCase(input.getFirstName()),
                NameUtils.toTitleCase(input.getLastName()),
                NameUtils.normalizeEmail(input.getEmail()));
    }
}
