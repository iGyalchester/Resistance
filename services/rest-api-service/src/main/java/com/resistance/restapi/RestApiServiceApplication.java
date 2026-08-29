package com.resistance.restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan("com.resistance.shared.models.entity")
public class RestApiServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RestApiServiceApplication.class, args);
	}

}
