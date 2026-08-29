package com.resistance.shared.models.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One-time login code for passwordless authentication. Only the SHA-256
 * hash of the code is stored; the plain code goes to the user's inbox.
 */
@Entity
@Table(name="login_code")
public class LoginCode {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private int id;

    @ManyToOne
    @JoinColumn(name="account_id", nullable = false)
    private UserAccount account;

    @Column(name="code_hash", nullable = false)
    private String codeHash;

    @Column(name="expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name="consumed", nullable = false)
    private boolean consumed;

    @Column(name="attempts", nullable = false)
    private int attempts;

    public LoginCode() {
    }

    public LoginCode(UserAccount account, String codeHash, Instant expiresAt) {
        this.account = account;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return !consumed && attempts < MAX_ATTEMPTS && now.isBefore(expiresAt);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UserAccount getAccount() {
        return account;
    }

    public void setAccount(UserAccount account) {
        this.account = account;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }
}
