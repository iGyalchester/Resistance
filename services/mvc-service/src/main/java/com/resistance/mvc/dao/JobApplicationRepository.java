package com.resistance.mvc.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.resistance.shared.models.entity.JobApplication;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {

	// that's it ... no need to write any code LOL!

    // add a method to sort by last name
    public List<JobApplication> findAllByOrderByLastNameAsc();

}
