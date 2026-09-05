package com.resistance.intake.service;

import com.resistance.intake.dao.ContactRepository;
import com.resistance.intake.dao.JobApplicationRepository;
import com.resistance.intake.dao.StatusHistoryRepository;
import com.resistance.intake.dao.UserAccountRepository;
import com.resistance.intake.notify.StatusNotifier;
import com.resistance.intake.parser.ConfirmationEmailParser;
import com.resistance.intake.parser.ParsedApplication;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private StatusHistoryRepository history;
    private StatusNotifier notifier;
    private AuditEventClient audit;
    private ConfirmationEmailParser parser;
    private IntakeService intakeService;
    private UserAccount account;

    private static final InboundEmail EMAIL =
            new InboundEmail("boris@gmail.com", "Boris Gerard", "track@resistance.example", "Fwd: anything", "body");

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        applications = mock(JobApplicationRepository.class);
        contacts = mock(ContactRepository.class);
        history = mock(StatusHistoryRepository.class);
        notifier = mock(StatusNotifier.class);
        audit = mock(AuditEventClient.class);
        parser = mock(ConfirmationEmailParser.class);
        intakeService = new IntakeService(accounts, applications, contacts, history, parser,
                notifier, Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC), false, audit);

        account = new UserAccount("Boris Gerard", "boris@gmail.com");
        account.setId(7);
        account.setIntakeAlias("boris2k4mp9");
        when(accounts.findByEmailIgnoreCase("boris@gmail.com")).thenReturn(Optional.of(account));
        when(accounts.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
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
    void creationRecordsHistoryFromNull() {
        when(parser.parse(EMAIL)).thenReturn(Optional.of(
                new ParsedApplication("Acme Corp", "Backend Engineer")));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Acme Corp")).thenReturn(List.of());

        intakeService.process(EMAIL);

        org.mockito.ArgumentCaptor<StatusHistory> recorded =
                org.mockito.ArgumentCaptor.forClass(StatusHistory.class);
        org.mockito.Mockito.verify(history).save(recorded.capture());
        assertNull(recorded.getValue().getFromStatus());
        assertEquals(ApplicationStatus.APPLIED, recorded.getValue().getToStatus());
        assertEquals(StatusHistory.SOURCE_INTAKE, recorded.getValue().getSource());
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

        org.mockito.ArgumentCaptor<StatusHistory> recorded =
                org.mockito.ArgumentCaptor.forClass(StatusHistory.class);
        org.mockito.Mockito.verify(history).save(recorded.capture());
        assertEquals(ApplicationStatus.APPLIED, recorded.getValue().getFromStatus());
        assertEquals(ApplicationStatus.REJECTED, recorded.getValue().getToStatus());
    }

    @Test
    void recruiterEmailLinksNewContact() {
        JobApplication existing = new JobApplication("Initech", "Java Developer", ApplicationStatus.APPLIED);
        existing.setId(43);
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Initech"))
                .thenReturn(List.of(existing));
        when(contacts.findByOwnerIdAndEmailIgnoreCase(7, "dana.reyes@initech.com"))
                .thenReturn(Optional.empty());
        when(parser.parse(EMAIL)).thenReturn(Optional.of(new ParsedApplication(
                "Initech", "Java Developer", ApplicationStatus.INTERVIEW,
                "Dana Reyes", "dana.reyes@initech.com")));

        IntakeResult result = intakeService.process(EMAIL);

        assertEquals("UPDATED", result.outcome());
        assertEquals(ApplicationStatus.INTERVIEW, existing.getStatus());
        assertNotNull(existing.getContact());
        assertEquals("Dana", existing.getContact().getFirstName());
        assertEquals("Reyes", existing.getContact().getLastName());
        // the new contact lands in this account's address book, nobody else's
        assertSame(account, existing.getContact().getOwner());
    }

    @Test
    void theSameRecruiterForTwoOwnersBecomesTwoContacts() {
        UserAccount other = new UserAccount("Other Person", "other@example.com");
        other.setId(9);
        other.setIntakeAlias("otherq7m2xz");
        when(accounts.findByIntakeAlias("otherq7m2xz")).thenReturn(Optional.of(other));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(anyInt(), anyString()))
                .thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(new ParsedApplication(
                "Initech", "Java Developer", ApplicationStatus.APPLIED,
                "Dana Reyes", "dana.reyes@initech.com")));

        // account 7 already has Dana in its address book; account 9 does not
        Contact mine = new Contact("Dana", "Reyes", "dana.reyes@initech.com", account);
        mine.setId(5);
        when(contacts.findByOwnerIdAndEmailIgnoreCase(7, "dana.reyes@initech.com"))
                .thenReturn(Optional.of(mine));
        when(contacts.findByOwnerIdAndEmailIgnoreCase(9, "dana.reyes@initech.com"))
                .thenReturn(Optional.empty());

        intakeService.process(EMAIL);
        intakeService.process(new InboundEmail("someone@else.example", "Someone Else",
                "track+otherq7m2xz@resistance.example", "Fwd: anything", "body"));

        // exactly one new row: account 7 reused its own, account 9 got its own
        org.mockito.ArgumentCaptor<Contact> created =
                org.mockito.ArgumentCaptor.forClass(Contact.class);
        org.mockito.Mockito.verify(contacts).save(created.capture());
        assertEquals(9, created.getValue().getOwner().getId());
        assertEquals("dana.reyes@initech.com", created.getValue().getEmail());
    }

    @Test
    void notificationsFireOnCreateAndStatusChangeOnly() {
        // create -> notified with null fromStatus
        when(parser.parse(EMAIL)).thenReturn(Optional.of(
                new ParsedApplication("Acme Corp", "Backend Engineer")));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Acme Corp")).thenReturn(List.of());
        intakeService.process(EMAIL);
        org.mockito.Mockito.verify(notifier)
                .notifyChange(org.mockito.ArgumentMatchers.eq(account), any(JobApplication.class),
                        org.mockito.ArgumentMatchers.isNull());

        // duplicate forward (no change) -> no further notification
        JobApplication existing = new JobApplication("Acme Corp", "Backend Engineer", ApplicationStatus.APPLIED);
        existing.setId(42);
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(7, "Acme Corp"))
                .thenReturn(List.of(existing));
        intakeService.process(EMAIL);
        org.mockito.Mockito.verifyNoMoreInteractions(notifier);
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
    void aliasRoutesToItsAccountRegardlessOfSender() {
        UserAccount aliasOwner = new UserAccount("Alias Owner", "owner@example.com");
        aliasOwner.setId(9);
        aliasOwner.setIntakeAlias("a8f3k2xq99");
        when(accounts.findByIntakeAlias("a8f3k2xq99")).thenReturn(Optional.of(aliasOwner));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(9, "Acme Corp")).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(new ParsedApplication("Acme Corp", null)));

        InboundEmail spoofed = new InboundEmail("attacker@evil.example", "Attacker",
                "track+a8f3k2xq99@resistance.example", "Fwd: anything", "body");
        IntakeResult result = intakeService.process(spoofed);

        assertEquals("CREATED", result.outcome());
        assertEquals("owner@example.com", result.accountEmail());
        assertFalse(result.accountCreated());
    }

    @Test
    void unknownAliasIsIgnoredWithoutProvisioning() {
        when(accounts.findByIntakeAlias("nosuchalias")).thenReturn(Optional.empty());

        InboundEmail email = new InboundEmail("attacker@evil.example", "Attacker",
                "track+nosuchalias@resistance.example", "Fwd: anything", "body");
        IntakeResult result = intakeService.process(email);

        assertEquals("IGNORED_UNKNOWN_ALIAS", result.outcome());
        assertNull(result.accountEmail());
    }

    @Test
    void strictModeIgnoresBareAddressEmail() {
        IntakeService strict = new IntakeService(accounts, applications, contacts, history, parser,
                notifier, Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC), true, audit);

        IntakeResult result = strict.process(EMAIL);

        assertEquals("IGNORED_NO_ALIAS", result.outcome());
    }

    @Test
    void bootstrapPathAssignsAliasToNewAccount() {
        when(accounts.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(accounts.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(anyInt(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(new ParsedApplication("Acme Corp", null)));

        IntakeResult result = intakeService.process(EMAIL);

        assertNotNull(result.intakeAlias());
        assertEquals(10, result.intakeAlias().length());
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

    @Test
    void intakeEmitsAuditEventsForProvisioningAndCreation() {
        when(accounts.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(applications.findByOwnerIdAndCompanyNameIgnoreCase(anyInt(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(new ParsedApplication("Acme Corp", null)));

        intakeService.process(EMAIL);

        org.mockito.Mockito.verify(audit).emit(
                org.mockito.ArgumentMatchers.eq("AUTH_EVENT"),
                org.mockito.ArgumentMatchers.eq("ACCOUNT_PROVISIONED"),
                org.mockito.ArgumentMatchers.eq("boris@gmail.com"),
                anyString(), org.mockito.ArgumentMatchers.isNull());
        org.mockito.Mockito.verify(audit).emit(
                org.mockito.ArgumentMatchers.eq("DATABASE_QUERY"),
                org.mockito.ArgumentMatchers.eq("INTAKE_CREATE"),
                org.mockito.ArgumentMatchers.eq("boris@gmail.com"),
                anyString(), org.mockito.ArgumentMatchers.isNull());
    }
}
