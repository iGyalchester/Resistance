package com.resistance.etl.processors;

import com.resistance.etl.core.EtlException;
import com.resistance.etl.core.Extractor;
import com.resistance.shared.models.dto.JobApplicationDto;
import com.resistance.shared.utils.CsvUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Reads application records from a CSV stream with a
 * {@code company_name,position_title,status} header row.
 */
public class CsvJobApplicationExtractor implements Extractor<JobApplicationDto> {

    private final Supplier<InputStream> source;

    public CsvJobApplicationExtractor(Supplier<InputStream> source) {
        this.source = source;
    }

    @Override
    public List<JobApplicationDto> extract() {
        List<JobApplicationDto> applications = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(source.get(), StandardCharsets.UTF_8))) {

            String line = reader.readLine(); // skip the header row
            while ((line = reader.readLine()) != null) {
                if (CsvUtils.isBlankLine(line)) {
                    continue;
                }
                List<String> fields = CsvUtils.parseLine(line);
                if (fields.size() < 3) {
                    throw new EtlException("Malformed CSV row (expected 3 fields): " + line);
                }
                applications.add(new JobApplicationDto(fields.get(0), fields.get(1), fields.get(2)));
            }
        } catch (IOException e) {
            throw new EtlException("Failed to read application CSV", e);
        }

        return applications;
    }
}
