package com.resistance.intake.dao;

import com.resistance.shared.models.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Integer> {

    Optional<Contact> findByEmailIgnoreCase(String email);
}
