package com.resistance.etl.core;

/**
 * Converts one record shape into another (normalization, mapping, enrichment).
 */
@FunctionalInterface
public interface Transformer<I, O> {

    O transform(I input);
}
