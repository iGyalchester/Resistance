package com.resistance.mvc.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

	private final JobApplicationRepository applicationRepository;
	private final UserAccountRepository accountRepository;

	public JobApplicationServiceImpl(JobApplicationRepository theJobApplicationRepository,
									 UserAccountRepository theUserAccountRepository) {
		applicationRepository = theJobApplicationRepository;
		accountRepository = theUserAccountRepository;
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

		if (theJobApplication.getId() != 0
				&& findByIdForOwner(theJobApplication.getId(), ownerId).isEmpty()) {
			// updating an id that isn't yours: refuse rather than overwrite
			throw new IllegalArgumentException(
					"Application " + theJobApplication.getId() + " does not belong to account " + ownerId);
		}

		UserAccount owner = accountRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalStateException("No account " + ownerId));
		theJobApplication.setOwner(owner);

		applicationRepository.save(theJobApplication);
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
