package com.resistance.etl.processors;

import com.resistance.etl.core.Transformer;
import com.resistance.shared.models.dto.EmployeeDto;
import com.resistance.shared.models.entity.Employee;

/**
 * Maps a normalized DTO onto the JPA entity ready for persistence.
 */
public class EmployeeEntityMapper implements Transformer<EmployeeDto, Employee> {

    private final EmployeeNormalizer normalizer = new EmployeeNormalizer();

    @Override
    public Employee transform(EmployeeDto input) {
        EmployeeDto normalized = normalizer.transform(input);
        return new Employee(
                normalized.getFirstName(),
                normalized.getLastName(),
                normalized.getEmail());
    }
}
