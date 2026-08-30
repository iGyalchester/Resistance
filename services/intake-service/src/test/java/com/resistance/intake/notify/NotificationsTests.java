package com.resistance.intake.notify;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsTests {

    private final UserAccount boris = new UserAccount("Boris Gerard", "boris@gmail.com");
    private final JobApplication acme =
            new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.INTERVIEW);

    @Test
    void newCaptureSubjectAndBody() {
        assertEquals("Now tracking: Acme Corp", Notifications.changeSubject(acme, null));

        String body = Notifications.changeBody(boris, acme, null);
        assertTrue(body.contains("Boris Gerard"));
        assertTrue(body.contains("Acme Corp"));
        assertTrue(body.contains("Backend Engineer"));
        assertTrue(body.contains("INTERVIEW"));
    }

    @Test
    void statusChangeSubjectAndBody() {
        assertEquals("Acme Corp moved to INTERVIEW",
                Notifications.changeSubject(acme, ApplicationStatus.APPLIED));

        String body = Notifications.changeBody(boris, acme, ApplicationStatus.APPLIED);
        assertTrue(body.contains("from APPLIED"));
        assertTrue(body.contains("to INTERVIEW"));
    }

    @Test
    void digestCountsAndListsApplications() {
        JobApplication globex = new JobApplication("Globex", null, ApplicationStatus.APPLIED);
        JobApplication initech = new JobApplication("Initech", "Java Developer", ApplicationStatus.APPLIED);

        String body = Notifications.digestBody(boris, List.of(acme, globex, initech));

        assertTrue(body.contains("3 tracked"));
        assertTrue(body.contains("APPLIED: 2"));
        assertTrue(body.contains("INTERVIEW: 1"));
        assertTrue(body.contains("- Globex: APPLIED"));
        assertTrue(body.contains("- Initech (Java Developer): APPLIED"));
    }
}
