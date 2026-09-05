package com.resistance.mvc.service;

import java.util.List;
import java.util.Optional;

import com.resistance.shared.models.entity.Contact;

/**
 * Owner-scoped, exactly like {@link JobApplicationService}: contacts are
 * one user's address book, not a shared directory, so every operation
 * takes the acting account's id and the service is the single place the
 * multi-user boundary is enforced.
 */
public interface ContactService {

	List<Contact> findAllForOwner(int ownerId);

	Optional<Contact> findByIdForOwner(int theId, int ownerId);

	void saveForOwner(Contact theContact, int ownerId);

	boolean deleteByIdForOwner(int theId, int ownerId);

}
