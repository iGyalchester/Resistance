package com.resistance.etl.runner;

import com.resistance.etl.runner.job.EmployeeCsvImportJob;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EntityScan("com.resistance.shared.models.entity")
public class EtlRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtlRunnerApplication.class, args);
    }

    @Bean
    public CommandLineRunner runEtl(EmployeeCsvImportJob employeeCsvImportJob) {
        return args -> employeeCsvImportJob.run();
    }
}
