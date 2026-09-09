package com.resistance.advanceddata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * The JPA relationship demos: one-to-one, one-to-many and many-to-many
 * between recruiters, job postings, candidates and notes. The mappings in
 * {@code shared-models/advanced} and the queries in {@code AppDAOImpl} are
 * the point of this module.
 *
 * <p>There used to be a CommandLineRunner here that wrote demo rows on
 * every start - a leftover from following along with a course. It was not
 * inert: {@code addMoreJobPostingsForCandidate} inserted two job postings
 * each boot, so the second start against the same database failed on
 * job_posting.TITLE_UNIQUE and took the whole context down with it. That
 * made `mvn install` pass exactly once per database, which is a strange
 * thing for a build to do and cost real time to diagnose. The DAO and the
 * mappings are unchanged; only the runner and its fifteen unreferenced
 * demo methods are gone.
 */
@SpringBootApplication
@EntityScan("com.resistance.shared.models.advanced")
public class AdvancedDataServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdvancedDataServiceApplication.class, args);
	}
}
