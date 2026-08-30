package com.resistance.mvc.service;

import java.util.List;
import java.util.Optional;

import com.resistance.shared.models.entity.JobApplication;

/**
 * Owner-scoped: every operation takes the acting account's id and only
 * ever sees that account's applications - the service is where the
 * multi-user boundary is enforced, so no controller can forget it.
 */
public interface JobApplicationService {

	List<JobApplication> findAllForOwner(int ownerId);

	Optional<JobApplication> findByIdForOwner(int theId, int ownerId);

	void saveForOwner(JobApplication theJobApplication, int ownerId);

	boolean deleteByIdForOwner(int theId, int ownerId);

}
