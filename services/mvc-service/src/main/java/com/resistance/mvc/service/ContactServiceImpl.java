package com.resistance.mvc.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.resistance.mvc.dao.ContactRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;

@Service
public class ContactServiceImpl implements ContactService {

	private final ContactRepository contactRepository;
	private final UserAccountRepository accountRepository;
	private final AuditEventClient audit;

	public ContactServiceImpl(ContactRepository theContactRepository,
							  UserAccountRepository theUserAccountRepository,
							  AuditEventClient theAuditEventClient) {
		contactRepository = theContactRepository;
		accountRepository = theUserAccountRepository;
		audit = theAuditEventClient;
	}

	@Override
	public List<Contact> findAllForOwner(int ownerId) {
		return contactRepository.findByOwnerIdOrderByLastNameAsc(ownerId);
	}

	@Override
	public Optional<Contact> findByIdForOwner(int theId, int ownerId) {
		// somebody else's contact looks exactly like a missing one
		return contactRepository.findById(theId)
				.filter(contact -> contact.getOwner() != null && contact.getOwner().getId() == ownerId);
	}

	@Override
	public void saveForOwner(Contact theContact, int ownerId) {

		boolean isNew = theContact.getId() == 0;

		if (!isNew) {
			// updating an id that isn't yours: refuse rather than overwrite
			findByIdForOwner(theContact.getId(), ownerId)
					.orElseThrow(() -> new IllegalArgumentException(
							"Contact " + theContact.getId()
									+ " does not belong to account " + ownerId));
		}

		UserAccount owner = accountRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalStateException("No account " + ownerId));
		theContact.setOwner(owner);

		Contact saved = contactRepository.save(theContact);

		audit.emit("DATABASE_QUERY", isNew ? "CREATE" : "UPDATE", owner.getEmail(),
				"contact:" + saved.getId(), null);
	}

	@Override
	public boolean deleteByIdForOwner(int theId, int ownerId) {
		Optional<Contact> owned = findByIdForOwner(theId, ownerId);
		if (owned.isEmpty()) {
			return false;
		}
		contactRepository.deleteById(theId);
		audit.emit("DATABASE_QUERY", "DELETE",
				owned.get().getOwner().getEmail(), "contact:" + theId, null);
		return true;
	}

}
