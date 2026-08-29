package com.resistance.shared.models.entity;

import jakarta.persistence.*;

/**
 * A tracker user, auto-provisioned the first time they forward a
 * confirmation email. Passwordless: authentication happens via
 * one-time codes (see LoginCode), so there is no password column.
 */
@Entity
@Table(name="user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @Column(name="full_name")
    private String fullName;

    @Column(name="email", unique = true, nullable = false)
    private String email;

    @Column(name="phone")
    private String phone;

    public UserAccount() {
    }

    public UserAccount(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public String toString() {
        return "UserAccount{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
