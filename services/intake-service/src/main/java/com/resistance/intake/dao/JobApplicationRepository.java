package com.resistance.intake.dao;

import com.resistance.shared.models.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {

    List<JobApplication> findByOwnerIdAndCompanyNameIgnoreCase(int ownerId, String companyName);

    List<JobApplication> findByOwnerId(int ownerId);
}
