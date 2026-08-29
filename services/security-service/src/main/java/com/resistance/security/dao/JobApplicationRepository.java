package com.resistance.security.dao;

import com.resistance.shared.models.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {

    // that's it ... no need to write any code LOL!

}
