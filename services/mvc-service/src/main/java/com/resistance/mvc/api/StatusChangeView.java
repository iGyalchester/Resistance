package com.resistance.mvc.api;

import com.resistance.shared.models.entity.StatusHistory;

import java.time.Instant;

/** One row of an application's timeline. fromStatus is null for the creation event. */
public record StatusChangeView(String fromStatus, String toStatus, Instant changedAt, String source) {

    public static StatusChangeView of(StatusHistory change) {
        return new StatusChangeView(
                change.getFromStatus() == null ? null : change.getFromStatus().name(),
                change.getToStatus().name(),
                change.getChangedAt(),
                change.getSource());
    }
}
