package com.resistance.advanceddata.dao;

import com.resistance.shared.models.advanced.JobPosting;
import com.resistance.shared.models.advanced.Recruiter;
import com.resistance.shared.models.advanced.RecruiterDetail;
import com.resistance.shared.models.advanced.Candidate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class AppDAOImpl implements AppDAO {

    // define field for entity manager
    private EntityManager entityManager;

    // inject entity manager using constructor injection
    @Autowired
    public AppDAOImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(Recruiter theRecruiter) {
        entityManager.persist(theRecruiter);
    }

    @Override
    public Recruiter findRecruiterById(int theId) {
        return entityManager.find(Recruiter.class, theId);
    }

    @Override
    @Transactional
    public void deleteRecruiterById(int theId) {

        // retrieve the recruiter
        Recruiter tempRecruiter = entityManager.find(Recruiter.class, theId);

        // get the jobPostings
        List<JobPosting> jobPostings = tempRecruiter.getJobPostings();

        // break association of all jobPostings for the recruiter
        for (JobPosting tempJobPosting : jobPostings) {
            tempJobPosting.setRecruiter(null);
        }

        // delete the recruiter
        entityManager.remove(tempRecruiter);
    }

    @Override
    public RecruiterDetail findRecruiterDetailById(int theId) {
        return entityManager.find(RecruiterDetail.class, theId);
    }

    @Override
    @Transactional
    public void deleteRecruiterDetailById(int theId) {

        // retrieve recruiter detail
        RecruiterDetail tempRecruiterDetail = entityManager.find(RecruiterDetail.class, theId);

        // remove the associated object reference
        // break bi-directional link
        //
        tempRecruiterDetail.getRecruiter().setRecruiterDetail(null);

        // delete the recruiter detail
        entityManager.remove(tempRecruiterDetail);
    }

    @Override
    public List<JobPosting> findJobPostingsByRecruiterId(int theId) {

        // create query
        TypedQuery<JobPosting> query = entityManager.createQuery(
                                    "from JobPosting where recruiter.id = :data", JobPosting.class);
        query.setParameter("data", theId);

        // execute query
        List<JobPosting> jobPostings = query.getResultList();

        return jobPostings;
    }

    @Override
    public Recruiter findRecruiterByIdJoinFetch(int theId) {

        // create query
        TypedQuery<Recruiter> query = entityManager.createQuery(
                                                "select i from Recruiter i "
                                                    + "JOIN FETCH i.jobPostings "
                                                    + "JOIN FETCH i.recruiterDetail "
                                                    + "where i.id = :data", Recruiter.class);
        query.setParameter("data", theId);

        // execute query
        Recruiter recruiter = query.getSingleResult();

        return recruiter;
    }

    @Override
    @Transactional
    public void update(Recruiter tempRecruiter) {
        entityManager.merge(tempRecruiter);
    }

    @Override
    @Transactional
    public void update(JobPosting tempJobPosting) {
        entityManager.merge(tempJobPosting);
    }

    @Override
    public JobPosting findJobPostingById(int theId) {
        return entityManager.find(JobPosting.class, theId);
    }

    @Override
    @Transactional
    public void deleteJobPostingById(int theId) {

        // retrieve the job posting
        JobPosting tempJobPosting = entityManager.find(JobPosting.class, theId);

        // delete the job posting
        entityManager.remove(tempJobPosting);
    }

    @Override
    @Transactional
    public void save(JobPosting theJobPosting) {
        entityManager.persist(theJobPosting);
    }

    @Override
    public JobPosting findJobPostingAndNotesByJobPostingId(int theId) {

        // create query
        TypedQuery<JobPosting> query = entityManager.createQuery(
                "select c from JobPosting c "
                + "JOIN FETCH c.notes "
                + "where c.id = :data", JobPosting.class);

        query.setParameter("data", theId);

        // execute query
        JobPosting jobPosting = query.getSingleResult();

        return jobPosting;
    }

    @Override
    public JobPosting findJobPostingAndCandidatesByJobPostingId(int theId) {

        // create query
        TypedQuery<JobPosting> query = entityManager.createQuery(
                "select c from JobPosting c "
                        + "JOIN FETCH c.candidates "
                        + "where c.id = :data", JobPosting.class);

        query.setParameter("data", theId);

        // execute query
        JobPosting jobPosting = query.getSingleResult();

        return jobPosting;
    }

    @Override
    public Candidate findCandidateAndJobPostingsByCandidateId(int theId) {

        // create query
        TypedQuery<Candidate> query = entityManager.createQuery(
                "select s from Candidate s "
                        + "JOIN FETCH s.jobPostings "
                        + "where s.id = :data", Candidate.class);

        query.setParameter("data", theId);

        // execute query
        Candidate candidate = query.getSingleResult();

        return candidate;
    }

    @Override
    @Transactional
    public void update(Candidate tempCandidate) {
        entityManager.merge(tempCandidate);
    }
}













