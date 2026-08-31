package com.resistance.intake.notify;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;

/**
 * Tells the user their tracker changed: a new application was captured or
 * an email moved one's status. fromStatus is null for a fresh capture.
 */
public interface StatusNotifier {

    void notifyChange(UserAccount account, JobApplication application, ApplicationStatus fromStatus);
}
