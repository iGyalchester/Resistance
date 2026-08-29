package com.resistance.intake.service;

import com.resistance.intake.dao.ContactRepository;
import com.resistance.intake.dao.JobApplicationRepository;
import com.resistance.intake.dao.UserAccountRepository;
import com.resistance.intake.parser.ConfirmationEmailParser;
import com.resistance.intake.parser.ParsedApplication;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeServiceTests {

    private UserAccountRepository accounts;
    private JobApplicationRepository applications;
    private ContactRepository contacts;
    private ConfirmationEmailParser parser;
    private IntakeService intakeService;
    private UserAccount account;

    private static final InboundEmail EMAIL =
            new InboundEmail("boris@gmail.com", "Boris Gerard", "Fwd: anything", "body");

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        applications = mock(JobApplicationRepository.class);
        contacts = mock(ContactRepository.class);
        parser = mock(ConfirmationEmailParser.class);
        intakeService = new IntakeService(accounts, applications, contacts, parser);

        account = new UserAccount("Boris Gerard", "boris@gmail.com");
        account.setId(7);
        when(accounts.findByEmailIgnoreCase("boris@gmail.com")).thenReturn(Optional.of(account));
        when(applications.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contacts.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void confirmationCreatesAppliedApplication() {
        when(parser.parse(EMAIL)).thenReturn(Optional.of(
                new ParsedApplication("Acme Corp", "Backend Engineer")));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Acme Corp")).thenReturn(List.of());

        IntakeResult result = intakeService.process(EMAIL);

        assertEquals("CREATED", result.outcome());
        assertEquals(ApplicationStatus.APPLIED, result.status());
    }

    @Test
    void rejectionEmailMovesExistingApplicationToRejected() {
        JobApplication existing = new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.APPLIED);
        existing.setId(42);
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Acme Corp"))
                .thenReturn(List.of(existing));
        when(parser.parse(EMAIL)).thenReturn(Optional.of(new ParsedApplication(
                "Acme Corp", "Backend Engineer", ApplicationStatus.REJECTED, null, null)));

        IntakeResult result = intakeService.process(EMAIL);

        assertEquals("UPDATED", result.outcome());
        assertEquals(ApplicationStatus.REJECTED, existing.getStatus());
    }

    @Test
    void recruiterEmailLinksNewContact() {
        JobApplication existing = new JobApplication("Initech", "Java Developer", ApplicationStatus.APPLIED);
        existing.setId(43);
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Initech"))
                .thenReturn(List.of(existing));
        when(contacts.findByEmailIgnoreCase("dana.reyes@initech.com")).thenReturn(Optional.empty());
        when(parser.parse(EMAIL)).thenReturn(Optional.of(new ParsedApplication(
                "Initech", "Java Developer", ApplicationStatus.INTERVIEW,
                "Dana Reyes", "dana.reyes@initech.com")));

        IntakeResult result = intakeService.process(EMAIL);

        assertEquals("UPDATED", result.outcome());
        assertEquals(ApplicationStatus.INTERVIEW, existing.getStatus());
        assertNotNull(existing.getContact());
        assertEquals("Dana", existing.getContact().getFirstName());
        assertEquals("Reyes", existing.getContact().getLastName());
    }

    @Test
    void existingContactLinkIsNeverOverwritten() {
        Contact original = new Contact("Existing", "Person", "existing@initech.com");
        JobApplication existing = new JobApplication("Initech", "Java Developer", ApplicationStatus.INTERVIEW);
        existing.setContact(original);
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Initech"))
                .thenReturn(List.of(existing));
        when(parser.parse(EMAIL)).thenReturn(Optional.of(new ParsedApplication(
                "Initech", "Java Developer", ApplicationStatus.INTERVIEW,
                "Somebody Else", "somebody@initech.com")));

        IntakeResult result = intakeService.process(EMAIL);

        assertEquals("ALREADY_TRACKED", result.outcome());
        assertSame(original, existing.getContact());
    }

    @Test
    void unknownSenderIsAutoProvisioned() {
        when(accounts.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(accounts.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(anyInt(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(new ParsedApplication("Acme Corp", null)));

        IntakeResult result = intakeService.process(EMAIL);

        assertTrue(result.accountCreated());
        assertEquals("CREATED", result.outcome());
    }
}
