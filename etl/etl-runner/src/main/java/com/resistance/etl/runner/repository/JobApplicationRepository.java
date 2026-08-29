package com.resistance.etl.runner.repository;

import com.resistance.shared.models.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {
}
