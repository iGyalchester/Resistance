package com.resistance.etl.core;

import java.util.Collections;
import java.util.List;

/**
 * Summary of one pipeline run: how many records were seen at each stage
 * and why the rejected ones were rejected.
 */
public final class EtlResult {

    private final int extracted;
    private final int loaded;
    private final List<String> rejections;

    public EtlResult(int extracted, int loaded, List<String> rejections) {
        this.extracted = extracted;
        this.loaded = loaded;
        this.rejections = List.copyOf(rejections);
    }

    public int getExtracted() {
        return extracted;
    }

    public int getLoaded() {
        return loaded;
    }

    public int getRejected() {
        return rejections.size();
    }

    public List<String> getRejections() {
        return Collections.unmodifiableList(rejections);
    }

    @Override
    public String toString() {
        return "EtlResult{extracted=" + extracted
                + ", loaded=" + loaded
                + ", rejected=" + getRejected() + '}';
    }
}
