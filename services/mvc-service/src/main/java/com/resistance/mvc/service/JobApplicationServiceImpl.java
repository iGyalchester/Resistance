package com.resistance.mvc.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.StatusHistoryRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

	private final JobApplicationRepository applicationRepository;
	private final UserAccountRepository accountRepository;
	private final StatusHistoryRepository historyRepository;
	private final Clock clock;
	private final AuditEventClient audit;

	public JobApplicationServiceImpl(JobApplicationRepository theJobApplicationRepository,
									 UserAccountRepository theUserAccountRepository,
									 StatusHistoryRepository theStatusHistoryRepository,
									 Clock theClock,
									 AuditEventClient theAuditEventClient) {
		applicationRepository = theJobApplicationRepository;
		accountRepository = theUserAccountRepository;
		historyRepository = theStatusHistoryRepository;
		clock = theClock;
		audit = theAuditEventClient;
	}

	@Override
	public List<JobApplication> findAllForOwner(int ownerId) {
		return applicationRepository.findByOwnerIdOrderByCompanyNameAsc(ownerId);
	}

	@Override
	public Optional<JobApplication> findByIdForOwner(int theId, int ownerId) {
		// somebody else's application looks exactly like a missing one
		return applicationRepository.findById(theId)
				.filter(app -> app.getOwner() != null && app.getOwner().getId() == ownerId);
	}

	@Override
	public void saveForOwner(JobApplication theJobApplication, int ownerId) {

		// The form posts a contact id and StringToContactConverter resolves it
		// with no request context, so a hand-edited dropdown value could
		// otherwise attach another user's contact to your application. This is
		// where that is refused.
		Contact contact = theJobApplication.getContact();
		if (contact != null
				&& (contact.getOwner() == null || contact.getOwner().getId() != ownerId)) {
			throw new IllegalArgumentException(
					"Contact " + contact.getId() + " does not belong to account " + ownerId);
		}

		ApplicationStatus previousStatus = null;
		boolean isNew = theJobApplication.getId() == 0;

		if (!isNew) {
			JobApplication existing = findByIdForOwner(theJobApplication.getId(), ownerId)
					// updating an id that isn't yours: refuse rather than overwrite
					.orElseThrow(() -> new IllegalArgumentException(
							"Application " + theJobApplication.getId()
									+ " does not belong to account " + ownerId));
			previousStatus = existing.getStatus();
			// the form doesn't carry these; keep what the row already has
			theJobApplication.setAppliedAt(existing.getAppliedAt());
		}

		UserAccount owner = accountRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalStateException("No account " + ownerId));
		theJobApplication.setOwner(owner);

		JobApplication saved = applicationRepository.save(theJobApplication);

		if (isNew || previousStatus != saved.getStatus()) {
			historyRepository.save(new StatusHistory(saved, previousStatus,
					saved.getStatus(), clock.instant(), StatusHistory.SOURCE_MANUAL));
		}

		audit.emit("DATABASE_QUERY", isNew ? "CREATE" : "UPDATE", owner.getEmail(),
				"job_application:" + saved.getId(), null);
	}

	@Override
	public boolean deleteByIdForOwner(int theId, int ownerId) {
		Optional<JobApplication> owned = findByIdForOwner(theId, ownerId);
		if (owned.isEmpty()) {
			return false;
		}
		applicationRepository.deleteById(theId);
		audit.emit("DATABASE_QUERY", "DELETE",
				owned.get().getOwner().getEmail(), "job_application:" + theId, null);
		return true;
	}

}
