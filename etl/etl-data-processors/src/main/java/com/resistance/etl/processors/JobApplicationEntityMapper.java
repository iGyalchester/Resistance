package com.resistance.etl.processors;

import com.resistance.etl.core.EtlException;
import com.resistance.etl.core.Transformer;
import com.resistance.shared.models.dto.JobApplicationDto;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;

/**
 * Maps a normalized DTO onto the JPA entity ready for persistence.
 */
public class JobApplicationEntityMapper implements Transformer<JobApplicationDto, JobApplication> {

    private final JobApplicationNormalizer normalizer = new JobApplicationNormalizer();

    @Override
    public JobApplication transform(JobApplicationDto input) {
        JobApplicationDto normalized = normalizer.transform(input);

        // records are validated before they reach the mapper; this guards
        // pipelines wired without the validator
        ApplicationStatus status = ApplicationStatus.fromString(normalized.getStatus())
                .orElseThrow(() -> new EtlException("Unknown application status: " + normalized.getStatus()));

        return new JobApplication(
                normalized.getCompanyName(),
                normalized.getPositionTitle(),
                status);
    }
}
