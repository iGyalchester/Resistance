package com.resistance.shared.models.advanced;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="job_posting")
public class JobPosting {

    // define our fields

    // define constructors

    // define getter setters

    // define toString

    // annotate fields

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="title")
    private String title;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE,
                          CascadeType.DETACH, CascadeType.REFRESH})
    @JoinColumn(name="recruiter_id")
    private Recruiter recruiter;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "job_posting_id")
    private List<Note> notes;


    @ManyToMany(fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE,
            CascadeType.DETACH, CascadeType.REFRESH})
    @JoinTable(
            name = "job_posting_candidate",
            joinColumns = @JoinColumn(name = "job_posting_id"),
            inverseJoinColumns = @JoinColumn(name = "candidate_id")
    )
    private List<Candidate> candidates;

    public JobPosting() {

    }

    public JobPosting(String title) {
        this.title = title;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Recruiter getRecruiter() {
        return recruiter;
    }

    public void setRecruiter(Recruiter recruiter) {
        this.recruiter = recruiter;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }

    // add a convenience method

    public void addNote(Note theNote) {

        if (notes == null) {
            notes = new ArrayList<>();
        }

        notes.add(theNote);
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    // add a convenience method

    public void addCandidate(Candidate theCandidate) {

        if (candidates == null) {
            candidates = new ArrayList<>();
        }

        candidates.add(theCandidate);
    }

    @Override
    public String toString() {
        return "JobPosting{" +
                "id=" + id +
                ", title='" + title + '\'' +
                '}';
    }
}






