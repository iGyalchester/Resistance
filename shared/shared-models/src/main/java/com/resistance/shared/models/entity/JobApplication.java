package com.resistance.shared.models.entity;

import jakarta.persistence.*;

@Entity
@Table(name="job_application")
public class JobApplication {

    // define fields
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="company_name")
    private String companyName;

    @Column(name="position_title")
    private String positionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private ApplicationStatus status;

    // the contact (recruiter/referral) this application came through, if any.
    // No cascade: Contact rows have their own lifecycle, this only sets the FK.
    @ManyToOne
    @JoinColumn(name="contact_id")
    private Contact contact;

    // the tracker user this application belongs to; set by email intake,
    // nullable so manually entered rows keep working
    @ManyToOne
    @JoinColumn(name="owner_account_id")
    private UserAccount owner;

    @Column(name="applied_at")
    private java.time.Instant appliedAt;

    @Column(name="updated_at")
    private java.time.Instant updatedAt;

    @PrePersist
    void onCreate() {
        java.time.Instant now = java.time.Instant.now();
        if (appliedAt == null) {
            appliedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = java.time.Instant.now();
    }


    // define constructors
    public JobApplication() {

    }

    public JobApplication(String companyName, String positionTitle, ApplicationStatus status) {
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.status = status;
    }

    // define getter/setter

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public java.time.Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(java.time.Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public java.time.Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // display helper for templates ("2026-08-30" or blank)
    @Transient
    public java.time.LocalDate getAppliedOn() {
        return appliedAt == null ? null
                : java.time.LocalDate.ofInstant(appliedAt, java.time.ZoneOffset.UTC);
    }

    // define toString
    @Override
    public String toString() {
        return "JobApplication{" +
                "id=" + id +
                ", companyName='" + companyName + '\'' +
                ", positionTitle='" + positionTitle + '\'' +
                ", status=" + status +
                '}';
    }
}








