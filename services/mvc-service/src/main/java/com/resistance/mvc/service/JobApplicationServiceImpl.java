package com.resistance.mvc.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.shared.models.entity.JobApplication;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

	private JobApplicationRepository applicationRepository;
	
	@Autowired
	public JobApplicationServiceImpl(JobApplicationRepository theJobApplicationRepository) {
		applicationRepository = theJobApplicationRepository;
	}
	
	@Override
	public List<JobApplication> findAll() {
		return applicationRepository.findAllByOrderByCompanyNameAsc();
	}

	@Override
	public JobApplication findById(int theId) {
		Optional<JobApplication> result = applicationRepository.findById(theId);
		
		JobApplication theJobApplication = null;
		
		if (result.isPresent()) {
			theJobApplication = result.get();
		}
		else {
			// we didn't find the application
			throw new RuntimeException("Did not find application id - " + theId);
		}
		
		return theJobApplication;
	}

	@Override
	public void save(JobApplication theJobApplication) {
		applicationRepository.save(theJobApplication);
	}

	@Override
	public void deleteById(int theId) {
		applicationRepository.deleteById(theId);
	}

}






