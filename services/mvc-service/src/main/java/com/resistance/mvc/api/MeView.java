package com.resistance.mvc.api;

import com.resistance.mvc.util.IntakeAddresses;
import com.resistance.shared.models.entity.UserAccount;

import java.util.List;

/**
 * The logged-in user as the React app sees them. A dedicated record
 * rather than the entity itself, so the wire format is explicit and
 * fields like the encrypted phone or the raw alias never leak by
 * accident. roles drives what the shell shows (Admin link); features
 * tells it which optional capabilities this deployment has switched on.
 */
public record MeView(String fullName, String email, String intakeAddress,
                     List<String> roles, Features features) {

    public record Features(boolean assistant) {
    }

    public static MeView of(UserAccount account, String intakeBaseAddress,
                            List<String> roles, Features features) {
        return new MeView(account.getFullName(), account.getEmail(),
                IntakeAddresses.personal(intakeBaseAddress, account.getIntakeAlias()),
                roles, features);
    }
}
