package com.resistance.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EntityScan("com.resistance.shared.models.entity")
@EnableScheduling
public class MvcServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MvcServiceApplication.class, args);
	}

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
