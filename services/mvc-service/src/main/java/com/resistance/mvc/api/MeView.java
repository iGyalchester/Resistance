package com.resistance.mvc.api;

import com.resistance.mvc.util.IntakeAddresses;
import com.resistance.shared.models.entity.UserAccount;

/**
 * The logged-in user as the React app sees them. A dedicated record
 * rather than the entity itself, so the wire format is explicit and
 * fields like the encrypted phone or the raw alias never leak by
 * accident.
 */
public record MeView(String fullName, String email, String intakeAddress) {

    public static MeView of(UserAccount account, String intakeBaseAddress) {
        return new MeView(account.getFullName(), account.getEmail(),
                IntakeAddresses.personal(intakeBaseAddress, account.getIntakeAlias()));
    }
}
