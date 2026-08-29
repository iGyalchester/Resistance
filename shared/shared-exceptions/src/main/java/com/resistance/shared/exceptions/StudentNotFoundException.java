package com.resistance.shared.exceptions;

public class StudentNotFoundException extends ResourceNotFoundException {

    public StudentNotFoundException(int studentId) {
        super("Student id not found - " + studentId);
    }

    public StudentNotFoundException(String message) {
        super(message);
    }
}
