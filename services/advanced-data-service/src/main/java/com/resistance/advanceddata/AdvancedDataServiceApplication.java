package com.resistance.advanceddata;

import com.resistance.advanceddata.dao.AppDAO;
import com.resistance.shared.models.advanced.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
@EntityScan("com.resistance.shared.models.advanced")
public class AdvancedDataServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdvancedDataServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(AppDAO appDAO) {

		return runner -> {

			// createJobPostingAndCandidates(appDAO);

			// findJobPostingAndCandidates(appDAO);

			// findCandidateAndJobPostings(appDAO);

			addMoreJobPostingsForCandidate(appDAO);

		};
	}

	private void addMoreJobPostingsForCandidate(AppDAO appDAO) {

		int theId = 2;
		Candidate tempCandidate = appDAO.findCandidateAndJobPostingsByCandidateId(theId);

		// create more jobPostings
		JobPosting tempJobPosting1 = new JobPosting("Rubik's Cube - How to Speed Cube");
		JobPosting tempJobPosting2 = new JobPosting("Atari 2600 - Game Development");

		// add jobPostings to candidate
		tempCandidate.addJobPosting(tempJobPosting1);
		tempCandidate.addJobPosting(tempJobPosting2);

		System.out.println("Updating candidate: " + tempCandidate);
		System.out.println("associated jobPostings: " + tempCandidate.getJobPostings());

		appDAO.update(tempCandidate);

		System.out.println("Done!");
	}

	private void findCandidateAndJobPostings(AppDAO appDAO) {

		int theId = 2;
		Candidate tempCandidate = appDAO.findCandidateAndJobPostingsByCandidateId(theId);

		System.out.println("Loaded candidate: " + tempCandidate);
		System.out.println("JobPostings: " + tempCandidate.getJobPostings());

		System.out.println("Done!");
	}

	private void findJobPostingAndCandidates(AppDAO appDAO) {

		int theId = 10;
		JobPosting tempJobPosting = appDAO.findJobPostingAndCandidatesByJobPostingId(theId);

		System.out.println("Loaded job posting: " + tempJobPosting);
		System.out.println("Candidates: " + tempJobPosting.getCandidates());

		System.out.println("Done!");
	}

	private void createJobPostingAndCandidates(AppDAO appDAO) {

		// create a job posting
		JobPosting tempJobPosting = new JobPosting("Pacman - How To Score One Million Points");

		// create the candidates
		Candidate tempCandidate1 = new Candidate("John", "Doe", "john@resistance.com");
		Candidate tempCandidate2 = new Candidate("Mary", "Public", "mary@resistance.com");

		// add candidates to the job posting
		tempJobPosting.addCandidate(tempCandidate1);
		tempJobPosting.addCandidate(tempCandidate2);

		// save the job posting and associated candidates
		System.out.println("Saving the job posting: " + tempJobPosting);
		System.out.println("associated candidates: " + tempJobPosting.getCandidates());

		appDAO.save(tempJobPosting);

		System.out.println("Done!");
	}

	private void retrieveJobPostingAndNotes(AppDAO appDAO) {

		// get the job posting and notes
		int theId = 10;
		JobPosting tempJobPosting = appDAO.findJobPostingAndNotesByJobPostingId(theId);

		// print the job posting
		System.out.println(tempJobPosting);

		// print the notes
		System.out.println(tempJobPosting.getNotes());

		System.out.println("Done!");
	}

	private void createJobPostingAndNotes(AppDAO appDAO) {

		// create a job posting
		JobPosting tempJobPosting = new JobPosting("Pacman - How To Score One Million Points");

		// add some notes
		tempJobPosting.addNote(new Note("Great job posting ... loved it!"));
		tempJobPosting.addNote(new Note("Cool job posting, job well done."));
		tempJobPosting.addNote(new Note("What a dumb job posting, you are an idiot!"));

		// save the job posting ... and leverage the cascade all
		System.out.println("Saving the job posting");
		System.out.println(tempJobPosting);
		System.out.println(tempJobPosting.getNotes());

		appDAO.save(tempJobPosting);

		System.out.println("Done!");
	}

	private void deleteJobPosting(AppDAO appDAO) {

		int theId = 10;

		System.out.println("Deleting job posting id: " + theId);

		appDAO.deleteJobPostingById(theId);

		System.out.println("Done!");
	}

	private void updateJobPosting(AppDAO appDAO) {

		int theId = 10;

		// find the job posting
		System.out.println("Finding job posting id: " + theId);
		JobPosting tempJobPosting = appDAO.findJobPostingById(theId);

		// update the job posting
		System.out.println("Updating job posting id: " + theId);
		tempJobPosting.setTitle("Enjoy the Simple Things");

		appDAO.update(tempJobPosting);

		System.out.println("Done!");
	}

	private void updateRecruiter(AppDAO appDAO) {

		int theId = 1;

		// find the recruiter
		System.out.println("Finding recruiter id: " + theId);
		Recruiter tempRecruiter = appDAO.findRecruiterById(theId);

		// update the recruiter
		System.out.println("Updating recruiter id: " + theId);
		tempRecruiter.setLastName("TESTER");

		appDAO.update(tempRecruiter);

		System.out.println("Done!");
	}

	private void findRecruiterWithJobPostingsJoinFetch(AppDAO appDAO) {

		int theId = 1;

		// find the recruiter
		System.out.println("Finding recruiter id: " + theId);
		Recruiter tempRecruiter = appDAO.findRecruiterByIdJoinFetch(theId);

		System.out.println("tempRecruiter: " + tempRecruiter);
		System.out.println("the associated jobPostings: " + tempRecruiter.getJobPostings());

		System.out.println("Done!");
	}

	private void findJobPostingsForRecruiter(AppDAO appDAO) {

		int theId = 1;
		// find recruiter
		System.out.println("Finding recruiter id: " + theId);

		Recruiter tempRecruiter = appDAO.findRecruiterById(theId);

		System.out.println("tempRecruiter: " + tempRecruiter);

		// find jobPostings for recruiter
		System.out.println("Finding jobPostings for recruiter id: " + theId);
		List<JobPosting> jobPostings = appDAO.findJobPostingsByRecruiterId(theId);

		// associate the objects
		tempRecruiter.setJobPostings(jobPostings);

		System.out.println("the associated jobPostings: " + tempRecruiter.getJobPostings());

		System.out.println("Done!");
	}

	private void findRecruiterWithJobPostings(AppDAO appDAO) {

		int theId = 1;
		System.out.println("Finding recruiter id: " + theId);

		Recruiter tempRecruiter = appDAO.findRecruiterById(theId);

		System.out.println("tempRecruiter: " + tempRecruiter);
		System.out.println("the associated jobPostings: " + tempRecruiter.getJobPostings());

		System.out.println("Done!");
	}

	private void createRecruiterWithJobPostings(AppDAO appDAO) {

		// create the recruiter
		Recruiter tempRecruiter =
				new Recruiter("Susan", "Public", "susan.public@resistance.com");

		// create the recruiter detail
		RecruiterDetail tempRecruiterDetail =
				new RecruiterDetail(
						"http://www.youtube.com",
						"Video Games");

		// associate the objects
		tempRecruiter.setRecruiterDetail(tempRecruiterDetail);

		// create some jobPostings
		JobPosting tempJobPosting1 = new JobPosting("Air Guitar - The Ultimate Guide");
		JobPosting tempJobPosting2 = new JobPosting("The Pinball Masterclass");

		// add jobPostings to recruiter
		tempRecruiter.add(tempJobPosting1);
		tempRecruiter.add(tempJobPosting2);

		// save the recruiter
		//
		// NOTE: this will ALSO save the jobPostings
		// because of CascadeType.PERSIST
		//
		System.out.println("Saving recruiter: " + tempRecruiter);
		System.out.println("The jobPostings: " + tempRecruiter.getJobPostings());
		appDAO.save(tempRecruiter);

		System.out.println("Done!");
	}

	private void deleteRecruiterDetail(AppDAO appDAO) {

		int theId = 3;
		System.out.println("Deleting recruiter detail id: " + theId);

		appDAO.deleteRecruiterDetailById(theId);

		System.out.println("Done!");
	}

	private void findRecruiterDetail(AppDAO appDAO) {

		// get the recruiter detail object
		int theId = 2;
		RecruiterDetail tempRecruiterDetail = appDAO.findRecruiterDetailById(theId);

		// print the recruiter detail
		System.out.println("tempRecruiterDetail: " + tempRecruiterDetail);

		// print the associated recruiter
		System.out.println("the associated recruiter: " + tempRecruiterDetail.getRecruiter());

		System.out.println("Done!");
	}

	private void deleteRecruiter(AppDAO appDAO) {

		int theId = 1;
		System.out.println("Deleting recruiter id: " + theId);

		appDAO.deleteRecruiterById(theId);

		System.out.println("Done!");
	}

	private void findRecruiter(AppDAO appDAO) {

		int theId = 2;
		System.out.println("Finding recruiter id: " + theId);

		Recruiter tempRecruiter = appDAO.findRecruiterById(theId);

		System.out.println("tempRecruiter: " + tempRecruiter);
		System.out.println("the associated recruiterDetail only: " + tempRecruiter.getRecruiterDetail());

	}

	private void createRecruiter(AppDAO appDAO) {

		/*
		// create the recruiter
		Recruiter tempRecruiter =
				new Recruiter("Chad", "Darby", "darby@resistance.com");

		// create the recruiter detail
		RecruiterDetail tempRecruiterDetail =
				new RecruiterDetail(
						"http://www.resistance.com/youtube",
						"Luv 2 code!!!");
		*/

		// create the recruiter
		Recruiter tempRecruiter =
				new Recruiter("Madhu", "Patel", "madhu@resistance.com");

		// create the recruiter detail
		RecruiterDetail tempRecruiterDetail =
				new RecruiterDetail(
						"http://www.resistance.com/youtube",
						"Guitar");

		// associate the objects
		tempRecruiter.setRecruiterDetail(tempRecruiterDetail);

		// save the recruiter
		//
		// NOTE: this will ALSO save the details object
		// because of CascadeType.ALL
		//
		System.out.println("Saving recruiter: " + tempRecruiter);
		appDAO.save(tempRecruiter);

		System.out.println("Done!");
	}
}








