package com.resistance.mvc.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {

    /**
     * The status as the database has it right now. A query rather than
     * findById().getStatus() because within one request the persistence
     * context hands back the same managed instance the caller may already
     * have mutated - which would make "previous" equal to "new".
     */
    @Query("select a.status from JobApplication a where a.id = :id")
    Optional<ApplicationStatus> findStatusById(@Param("id") int id);

    List<JobApplication> findByOwnerId(int ownerId);

    List<JobApplication> findByOwnerIdOrderByCompanyNameAsc(int ownerId);

	// that's it ... no need to write any code LOL!

    // add a method to sort by company name
    public List<JobApplication> findAllByOrderByCompanyNameAsc();

}
