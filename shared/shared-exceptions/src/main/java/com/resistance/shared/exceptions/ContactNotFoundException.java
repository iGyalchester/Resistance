package com.resistance.shared.exceptions;

public class ContactNotFoundException extends ResourceNotFoundException {

    public ContactNotFoundException(int contactId) {
        super("Contact id not found - " + contactId);
    }

    public ContactNotFoundException(String message) {
        super(message);
    }
}
