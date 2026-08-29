package com.resistance.etl.processors;

import com.resistance.etl.core.Transformer;
import com.resistance.shared.models.dto.JobApplicationDto;

import java.util.Locale;

/**
 * Cleans up raw CSV input: trims company and position, lowercases status.
 */
public class JobApplicationNormalizer implements Transformer<JobApplicationDto, JobApplicationDto> {

    @Override
    public JobApplicationDto transform(JobApplicationDto input) {
        return new JobApplicationDto(
                trim(input.getCompanyName()),
                trim(input.getPositionTitle()),
                input.getStatus() == null ? null : input.getStatus().trim().toLowerCase(Locale.ROOT));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
