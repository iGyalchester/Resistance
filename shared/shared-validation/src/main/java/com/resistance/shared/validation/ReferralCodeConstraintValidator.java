package com.resistance.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ReferralCodeConstraintValidator  implements ConstraintValidator<ReferralCode, String> {

    private String referralPrefix;

    @Override
    public void initialize(ReferralCode theReferralCode) {
        referralPrefix = theReferralCode.value();
    }

    @Override
    public boolean isValid(String theCode, ConstraintValidatorContext theConstraintValidatorContext) {

        boolean result;

        if (theCode != null) {
            result = theCode.startsWith(referralPrefix);
        }
        else {
            result = true;
        }

        return result;
    }
}






