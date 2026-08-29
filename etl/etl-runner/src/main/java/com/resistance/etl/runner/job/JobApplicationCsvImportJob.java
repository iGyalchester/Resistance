package com.resistance.etl.runner.job;

import com.resistance.etl.core.EtlException;
import com.resistance.etl.core.EtlPipeline;
import com.resistance.etl.core.EtlResult;
import com.resistance.etl.core.Loader;
import com.resistance.etl.processors.CsvJobApplicationExtractor;
import com.resistance.etl.processors.JobApplicationEntityMapper;
import com.resistance.etl.runner.repository.JobApplicationRepository;
import com.resistance.etl.validators.JobApplicationRecordValidator;
import com.resistance.shared.models.entity.JobApplication;
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
 * Imports applications from a CSV file into the job_tracker database.
 * With etl.dry-run=true (the default) the records are logged instead of saved.
 */
@Component
public class JobApplicationCsvImportJob {

    private static final Logger log = LoggerFactory.getLogger(JobApplicationCsvImportJob.class);

    private final JobApplicationRepository applicationRepository;
    private final ResourceLoader resourceLoader;
    private final String inputLocation;
    private final boolean dryRun;

    public JobApplicationCsvImportJob(JobApplicationRepository applicationRepository,
                                ResourceLoader resourceLoader,
                                @Value("${etl.input-location:classpath:sample-data/applications.csv}") String inputLocation,
                                @Value("${etl.dry-run:true}") boolean dryRun) {
        this.applicationRepository = applicationRepository;
        this.resourceLoader = resourceLoader;
        this.inputLocation = inputLocation;
        this.dryRun = dryRun;
    }

    public EtlResult run() {
        Resource input = resourceLoader.getResource(inputLocation);

        CsvJobApplicationExtractor extractor = new CsvJobApplicationExtractor(() -> open(input));

        Loader<JobApplication> loader = dryRun ? this::logOnly : this::saveAll;

        EtlPipeline<com.resistance.shared.models.dto.JobApplicationDto, JobApplication> pipeline =
                new EtlPipeline<>(
                        "application-csv-import",
                        extractor,
                        new JobApplicationRecordValidator(),
                        new JobApplicationEntityMapper(),
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

    private void saveAll(List<JobApplication> applications) {
        applicationRepository.saveAll(applications);
        log.info("Saved {} application(s) to the database", applications.size());
    }

    private void logOnly(List<JobApplication> applications) {
        log.info("Dry run - would save {} application(s):", applications.size());
        applications.forEach(e -> log.info("  {}", e));
    }
}
