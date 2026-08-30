package com.resistance.intake.aws;

import java.security.PublicKey;

/**
 * Resolves the public key for an SNS SigningCertURL. Split out so the
 * signature verifier can be unit-tested with a locally generated key.
 */
@FunctionalInterface
public interface SigningKeyResolver {

    PublicKey resolve(String signingCertUrl);
}
