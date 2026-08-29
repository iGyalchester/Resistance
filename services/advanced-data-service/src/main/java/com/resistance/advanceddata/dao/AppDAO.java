package com.resistance.advanceddata.dao;

import com.resistance.shared.models.advanced.JobPosting;
import com.resistance.shared.models.advanced.Recruiter;
import com.resistance.shared.models.advanced.RecruiterDetail;
import com.resistance.shared.models.advanced.Candidate;

import java.util.List;

public interface AppDAO {

    void save(Recruiter theRecruiter);

    Recruiter findRecruiterById(int theId);

    void deleteRecruiterById(int theId);

    RecruiterDetail findRecruiterDetailById(int theId);

    void deleteRecruiterDetailById(int theId);

    List<JobPosting> findJobPostingsByRecruiterId(int theId);

    Recruiter findRecruiterByIdJoinFetch(int theId);

    void update(Recruiter tempRecruiter);

    void update(JobPosting tempJobPosting);

    JobPosting findJobPostingById(int theId);

    void deleteJobPostingById(int theId);

    void save(JobPosting theJobPosting);

    JobPosting findJobPostingAndNotesByJobPostingId(int theId);

    JobPosting findJobPostingAndCandidatesByJobPostingId(int theId);

    Candidate findCandidateAndJobPostingsByCandidateId(int theId);

    void update(Candidate tempCandidate);
}













