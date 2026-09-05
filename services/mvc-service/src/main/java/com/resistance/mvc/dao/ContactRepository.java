package com.resistance.mvc.dao;

import com.resistance.shared.models.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Integer> {

	// the signed-in user's address book, alphabetical; ownerless rows are
	// deliberately excluded (see ContactServiceImpl)
	List<Contact> findByOwnerIdOrderByLastNameAsc(int ownerId);
}
