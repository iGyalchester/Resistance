package com.resistance.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan("com.resistance.shared.models.entity")
@EnableScheduling
public class IntakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntakeServiceApplication.class, args);
    }
}
