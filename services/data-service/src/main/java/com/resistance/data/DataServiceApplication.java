package com.resistance.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * The plain-JPA CRUD demo: {@code ContactDAOImpl} against the shared
 * {@code Contact} entity, without Spring Data repositories. Kept as the
 * contrast with mvc-service, which uses them.
 *
 * <p>There used to be a CommandLineRunner here that inserted three contacts
 * on every start - a leftover from following along with a course. Harmless
 * next to advanced-data-service's, which actually broke the build, but
 * still a service that mutated its database merely by booting.
 */
@SpringBootApplication
@EntityScan("com.resistance.shared.models.entity")
public class DataServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DataServiceApplication.class, args);
	}
}
