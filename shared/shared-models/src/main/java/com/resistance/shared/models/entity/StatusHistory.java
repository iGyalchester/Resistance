package com.resistance.shared.models.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One status transition of a job application - the raw material for
 * funnel metrics (time-in-stage, response rates, "ghosted" detection).
 * fromStatus is null for the creation event.
 */
@Entity
@Table(name="status_history")
public class StatusHistory {

    public static final String SOURCE_INTAKE = "INTAKE";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @ManyToOne
    @JoinColumn(name="application_id", nullable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name="from_status")
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name="to_status", nullable = false)
    private ApplicationStatus toStatus;

    @Column(name="changed_at", nullable = false)
    private Instant changedAt;

    // INTAKE (an email drove the change) or MANUAL (edited in the UI)
    @Column(name="source", nullable = false)
    private String source;

    public StatusHistory() {
    }

    public StatusHistory(JobApplication application, ApplicationStatus fromStatus,
                         ApplicationStatus toStatus, Instant changedAt, String source) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.source = source;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public JobApplication getApplication() {
        return application;
    }

    public void setApplication(JobApplication application) {
        this.application = application;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ApplicationStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ApplicationStatus toStatus) {
        this.toStatus = toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
