package com.resistance.restapi.service;

import com.resistance.shared.exceptions.JobApplicationNotFoundException;
import com.resistance.restapi.dao.JobApplicationRepository;
import com.resistance.shared.models.entity.JobApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobApplicationServiceImpl implements JobApplicationService {

    private JobApplicationRepository applicationRepository;

    @Autowired
    public JobApplicationServiceImpl(JobApplicationRepository theJobApplicationRepository) {
        applicationRepository = theJobApplicationRepository;
    }

    @Override
    public List<JobApplication> findAll() {
        return applicationRepository.findAll();
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
            throw new JobApplicationNotFoundException("Did not find application id - " + theId);
        }

        return theJobApplication;
    }

    @Override
    public JobApplication save(JobApplication theJobApplication) {
        return applicationRepository.save(theJobApplication);
    }

    @Override
    public void deleteById(int theId) {
        applicationRepository.deleteById(theId);
    }
}






