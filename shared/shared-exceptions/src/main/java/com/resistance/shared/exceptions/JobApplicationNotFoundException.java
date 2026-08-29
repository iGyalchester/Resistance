package com.resistance.shared.exceptions;

public class JobApplicationNotFoundException extends ResourceNotFoundException {

    public JobApplicationNotFoundException(int applicationId) {
        super("Job application id not found - " + applicationId);
    }

    public JobApplicationNotFoundException(String message) {
        super(message);
    }
}
