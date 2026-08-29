package com.resistance.etl.core;

import java.util.List;

/**
 * Source side of a pipeline: pulls raw records from a file, API or database.
 */
@FunctionalInterface
public interface Extractor<T> {

    List<T> extract();
}
