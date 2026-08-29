package com.resistance.etl.core;

import java.util.List;

/**
 * Sink side of a pipeline: persists transformed records to their destination.
 */
@FunctionalInterface
public interface Loader<T> {

    void load(List<T> records);
}
