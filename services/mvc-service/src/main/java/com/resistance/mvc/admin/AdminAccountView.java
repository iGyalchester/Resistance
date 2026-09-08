package com.resistance.mvc.admin;

import java.time.Instant;

/**
 * One account as the admin list shows it. Deliberately thin: email and
 * name identify the person, the rest says whether the account is alive.
 * Never the phone, never encrypted fields, never the intake alias itself
 * (knowing an alias is what authorizes filing into that account).
 */
public record AdminAccountView(int id, String email, String fullName, long applicationCount,
                               Instant lastActivity, boolean hasAlias) {
}
