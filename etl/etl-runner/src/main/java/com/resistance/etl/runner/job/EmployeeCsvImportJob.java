package com.resistance.etl.runner.job;

import com.resistance.etl.core.EtlException;
import com.resistance.etl.core.EtlPipeline;
import com.resistance.etl.core.EtlResult;
import com.resistance.etl.core.Loader;
import com.resistance.etl.processors.CsvEmployeeExtractor;
import com.resistance.etl.processors.EmployeeEntityMapper;
import com.resistance.etl.runner.repository.EmployeeRepository;
import com.resistance.etl.validators.EmployeeRecordValidator;
import com.resistance.shared.models.entity.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Imports employees from a CSV file into the employee_directory database.
 * With etl.dry-run=true (the default) the records are logged instead of saved.
 */
@Component
public class EmployeeCsvImportJob {

    private static final Logger log = LoggerFactory.getLogger(EmployeeCsvImportJob.class);

    private final EmployeeRepository employeeRepository;
    private final ResourceLoader resourceLoader;
    private final String inputLocation;
    private final boolean dryRun;

    public EmployeeCsvImportJob(EmployeeRepository employeeRepository,
                                ResourceLoader resourceLoader,
                                @Value("${etl.input-location:classpath:sample-data/employees.csv}") String inputLocation,
                                @Value("${etl.dry-run:true}") boolean dryRun) {
        this.employeeRepository = employeeRepository;
        this.resourceLoader = resourceLoader;
        this.inputLocation = inputLocation;
        this.dryRun = dryRun;
    }

    public EtlResult run() {
        Resource input = resourceLoader.getResource(inputLocation);

        CsvEmployeeExtractor extractor = new CsvEmployeeExtractor(() -> open(input));

        Loader<Employee> loader = dryRun ? this::logOnly : this::saveAll;

        EtlPipeline<com.resistance.shared.models.dto.EmployeeDto, Employee> pipeline =
                new EtlPipeline<>(
                        "employee-csv-import",
                        extractor,
                        new EmployeeRecordValidator(),
                        new EmployeeEntityMapper(),
                        loader);

        return pipeline.run();
    }

    private InputStream open(Resource resource) {
        try {
            return resource.getInputStream();
        } catch (IOException e) {
            throw new EtlException("Cannot open ETL input: " + inputLocation, e);
        }
    }

    private void saveAll(List<Employee> employees) {
        employeeRepository.saveAll(employees);
        log.info("Saved {} employee(s) to the database", employees.size());
    }

    private void logOnly(List<Employee> employees) {
        log.info("Dry run - would save {} employee(s):", employees.size());
        employees.forEach(e -> log.info("  {}", e));
    }
}
