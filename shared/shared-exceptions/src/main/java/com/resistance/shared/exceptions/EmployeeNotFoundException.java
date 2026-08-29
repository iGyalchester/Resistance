package com.resistance.shared.exceptions;

public class EmployeeNotFoundException extends ResourceNotFoundException {

    public EmployeeNotFoundException(int employeeId) {
        super("Employee id not found - " + employeeId);
    }

    public EmployeeNotFoundException(String message) {
        super(message);
    }
}
