package com.resistance.mvc.service;

import java.util.List;

import com.resistance.shared.models.entity.JobApplication;

public interface JobApplicationService {

	List<JobApplication> findAll();
	
	JobApplication findById(int theId);
	
	void save(JobApplication theJobApplication);
	
	void deleteById(int theId);
	
}
