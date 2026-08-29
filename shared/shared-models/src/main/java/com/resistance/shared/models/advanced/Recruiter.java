package com.resistance.shared.models.advanced;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="recruiter")
public class Recruiter {

    // annotate the class as an entity and map to db table

    // define the fields

    // annotate the fields with db column names

    // ** set up mapping to RecruiterDetail entity

    // create constructors

    // generate getter/setter methods

    // generate toString() method

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="first_name")
    private String firstName;

    @Column(name="last_name")
    private String lastName;

    @Column(name="email")
    private String email;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "recruiter_detail_id")
    private RecruiterDetail recruiterDetail;

    @OneToMany(mappedBy = "recruiter",
               fetch = FetchType.LAZY,
               cascade = {CascadeType.PERSIST, CascadeType.MERGE,
                          CascadeType.DETACH, CascadeType.REFRESH})
    private List<JobPosting> jobPostings;

    public Recruiter() {

    }

    public Recruiter(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RecruiterDetail getRecruiterDetail() {
        return recruiterDetail;
    }

    public void setRecruiterDetail(RecruiterDetail recruiterDetail) {
        this.recruiterDetail = recruiterDetail;
    }

    @Override
    public String toString() {
        return "Recruiter{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", recruiterDetail=" + recruiterDetail +
                '}';
    }

    public List<JobPosting> getJobPostings() {
        return jobPostings;
    }

    public void setJobPostings(List<JobPosting> jobPostings) {
        this.jobPostings = jobPostings;
    }

    // add convenience methods for bi-directional relationship

    public void add(JobPosting tempJobPosting) {

        if (jobPostings == null) {
            jobPostings = new ArrayList<>();
        }

        jobPostings.add(tempJobPosting);

        tempJobPosting.setRecruiter(this);
    }
}








