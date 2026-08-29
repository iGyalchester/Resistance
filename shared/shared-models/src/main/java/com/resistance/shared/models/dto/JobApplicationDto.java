package com.resistance.shared.models.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Transport object for job application data crossing service and ETL boundaries.
 */
public class JobApplicationDto {

    @NotBlank(message = "company name is required")
    private String companyName;

    @NotBlank(message = "position title is required")
    private String positionTitle;

    @NotBlank(message = "status is required")
    private String status;

    public JobApplicationDto() {
    }

    public JobApplicationDto(String companyName, String positionTitle, String status) {
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.status = status;
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

    @Override
    public String toString() {
        return "JobApplicationDto{" +
                "companyName='" + companyName + '\'' +
                ", positionTitle='" + positionTitle + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
