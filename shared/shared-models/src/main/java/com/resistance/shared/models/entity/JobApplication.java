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

    @Column(name="status")
    private String status;


    // define constructors
    public JobApplication() {

    }

    public JobApplication(String companyName, String positionTitle, String status) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // define toString
    @Override
    public String toString() {
        return "JobApplication{" +
                "id=" + id +
                ", companyName='" + companyName + '\'' +
                ", positionTitle='" + positionTitle + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}








