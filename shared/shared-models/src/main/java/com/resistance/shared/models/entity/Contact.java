package com.resistance.shared.models.entity;

import jakarta.persistence.*;

@Entity
@Table(name="contact")
public class Contact {

    // define fields
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

    // the tracker user this contact belongs to. Contacts are per-user: two
    // people who both hear from the same recruiter each get their own row,
    // and neither can see the other's. Nullable so rows written before
    // owners existed still load - the service layer treats an ownerless
    // contact as invisible, the same rule JobApplication follows.
    @ManyToOne
    @JoinColumn(name="owner_account_id")
    private UserAccount owner;

    // define constructors
    public Contact() {

    }

    public Contact(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public Contact(String firstName, String lastName, String email, UserAccount owner) {
        this(firstName, lastName, email);
        this.owner = owner;
    }

    // define getters/setters

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

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }


    // define toString() method

    @Override
    public String toString() {
        return "Contact{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
