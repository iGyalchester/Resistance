package com.resistance.shared.models.advanced;

import jakarta.persistence.*;

@Entity
@Table(name="recruiter_detail")
public class RecruiterDetail {

    // annotate the class as an entity and map to db table

    // define the fields

    // annotate the fields with db column names

    // create constructors

    // generate getter/setter methods

    // generate toString() method

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="linkedin_url")
    private String linkedinUrl;

    @Column(name="agency")
    private String agency;

    // add @OneToOne annotation
    @OneToOne(mappedBy = "recruiterDetail",
            cascade = {CascadeType.DETACH, CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REFRESH})
    private Recruiter recruiter;

    public RecruiterDetail() {

    }

    public RecruiterDetail(String linkedinUrl, String agency) {
        this.linkedinUrl = linkedinUrl;
        this.agency = agency;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public Recruiter getRecruiter() {
        return recruiter;
    }

    public void setRecruiter(Recruiter recruiter) {
        this.recruiter = recruiter;
    }

    @Override
    public String toString() {
        return "RecruiterDetail{" +
                "id=" + id +
                ", linkedinUrl='" + linkedinUrl + '\'' +
                ", agency='" + agency + '\'' +
                '}';
    }
}










