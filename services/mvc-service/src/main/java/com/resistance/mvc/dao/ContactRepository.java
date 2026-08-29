package com.resistance.mvc.dao;

import com.resistance.shared.models.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Integer> {
}
