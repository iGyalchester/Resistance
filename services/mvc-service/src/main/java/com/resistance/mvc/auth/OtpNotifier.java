package com.resistance.mvc.auth;

import com.resistance.shared.models.entity.UserAccount;

/**
 * Delivers a one-time login code to the user. Email is the default channel;
 * an SMS implementation (e.g. Twilio) can be added as another bean.
 */
public interface OtpNotifier {

    void sendCode(UserAccount account, String code);
}
