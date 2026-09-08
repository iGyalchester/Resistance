package com.resistance.mvc.api;

import com.resistance.shared.models.entity.Contact;

/** A contact as the address-book page shows it, with how many applications reference it. */
public record ContactView(int id, String firstName, String lastName, String email, long applicationCount) {

    public static ContactView of(Contact contact, long applicationCount) {
        return new ContactView(contact.getId(), contact.getFirstName(), contact.getLastName(),
                contact.getEmail(), applicationCount);
    }
}
