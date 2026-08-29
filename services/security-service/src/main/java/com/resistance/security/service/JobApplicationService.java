package com.resistance.security.service;

import com.resistance.shared.models.entity.JobApplication;

import java.util.List;

public interface JobApplicationService {

    List<JobApplication> findAll();

    JobApplication findById(int theId);

    JobApplication save(JobApplication theJobApplication);

    void deleteById(int theId);

}
