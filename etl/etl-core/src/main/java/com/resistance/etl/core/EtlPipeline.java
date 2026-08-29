package com.resistance.etl.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates one extract -> validate -> transform -> load run.
 * Invalid records are collected and reported, not fatal to the run.
 */
public class EtlPipeline<I, O> {

    private static final Logger log = LoggerFactory.getLogger(EtlPipeline.class);

    private final String name;
    private final Extractor<I> extractor;
    private final RecordValidator<I> validator;
    private final Transformer<I, O> transformer;
    private final Loader<O> loader;

    public EtlPipeline(String name,
                       Extractor<I> extractor,
                       RecordValidator<I> validator,
                       Transformer<I, O> transformer,
                       Loader<O> loader) {
        this.name = name;
        this.extractor = extractor;
        this.validator = validator != null ? validator : RecordValidator.acceptAll();
        this.transformer = transformer;
        this.loader = loader;
    }

    public EtlResult run() {
        log.info("[{}] starting pipeline", name);

        List<I> rawRecords = extractor.extract();
        log.info("[{}] extracted {} record(s)", name, rawRecords.size());

        List<O> transformed = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        for (I record : rawRecords) {
            ValidationResult result = validator.validate(record);
            if (result.isValid()) {
                transformed.add(transformer.transform(record));
            } else {
                String reason = record + " -> " + String.join("; ", result.getErrors());
                rejections.add(reason);
                log.warn("[{}] rejected record: {}", name, reason);
            }
        }

        loader.load(transformed);

        EtlResult etlResult = new EtlResult(rawRecords.size(), transformed.size(), rejections);
        log.info("[{}] finished: {}", name, etlResult);
        return etlResult;
    }
}
