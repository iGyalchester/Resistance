package com.resistance.mvc.api;

import com.resistance.shared.models.entity.UserAccount;

/** The editable profile. Email is identity and read-only; phone is decrypted on the way out. */
public record ProfileView(String fullName, String email, String phone) {

    public static ProfileView of(UserAccount account) {
        return new ProfileView(account.getFullName(), account.getEmail(), account.getPhone());
    }
}
