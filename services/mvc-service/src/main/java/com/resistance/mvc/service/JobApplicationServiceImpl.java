package com.resistance.mvc.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.StatusHistoryRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

	private final JobApplicationRepository applicationRepository;
	private final UserAccountRepository accountRepository;
	private final StatusHistoryRepository historyRepository;
	private final Clock clock;

	public JobApplicationServiceImpl(JobApplicationRepository theJobApplicationRepository,
									 UserAccountRepository theUserAccountRepository,
									 StatusHistoryRepository theStatusHistoryRepository,
									 Clock theClock) {
		applicationRepository = theJobApplicationRepository;
		accountRepository = theUserAccountRepository;
		historyRepository = theStatusHistoryRepository;
		clock = theClock;
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
	}

	@Override
	public boolean deleteByIdForOwner(int theId, int ownerId) {
		Optional<JobApplication> owned = findByIdForOwner(theId, ownerId);
		if (owned.isEmpty()) {
			return false;
		}
		applicationRepository.deleteById(theId);
		return true;
	}

}
