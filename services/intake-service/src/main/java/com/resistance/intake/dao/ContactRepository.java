package com.resistance.intake.dao;

import com.resistance.shared.models.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Integer> {

    /**
     * Contacts are per-user, so the owner is part of the lookup: two
     * accounts hearing from the same recruiter each get their own row
     * rather than sharing (and being able to edit) one.
     */
    Optional<Contact> findByOwnerIdAndEmailIgnoreCase(int ownerId, String email);
}
