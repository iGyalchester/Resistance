package com.resistance.intake.service;

import com.resistance.intake.dao.ContactRepository;
import com.resistance.intake.dao.JobApplicationRepository;
import com.resistance.intake.dao.StatusHistoryRepository;
import com.resistance.intake.notify.StatusNotifier;
import com.resistance.intake.dao.UserAccountRepository;
import com.resistance.intake.parser.ConfirmationEmailParser;
import com.resistance.intake.parser.ParsedApplication;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole "forward an email, get a tracked application" flow:
 * resolve/create the account from the forwarding sender, parse the email,
 * then create the application (confirmations) or move its status forward
 * (rejections, interview invites, offers), linking the recruiter as a
 * Contact when a human sent it.
 */
@Service
public class IntakeService {

    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);

    // unambiguous lowercase alphabet for intake aliases
    private static final char[] ALIAS_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private static final int ALIAS_LENGTH = 10;

    private final UserAccountRepository accountRepository;
    private final JobApplicationRepository applicationRepository;
    private final ContactRepository contactRepository;
    private final StatusHistoryRepository historyRepository;
    private final ConfirmationEmailParser parser;
    private final StatusNotifier notifier;
    private final Clock clock;
    private final boolean requireAlias;
    private final AuditEventClient audit;
    private final SecureRandom random = new SecureRandom();

    public IntakeService(UserAccountRepository accountRepository,
                         JobApplicationRepository applicationRepository,
                         ContactRepository contactRepository,
                         StatusHistoryRepository historyRepository,
                         ConfirmationEmailParser parser,
                         StatusNotifier notifier,
                         Clock clock,
                         @Value("${intake.require-alias:false}") boolean requireAlias,
                         AuditEventClient auditEventClient) {
        this.accountRepository = accountRepository;
        this.applicationRepository = applicationRepository;
        this.contactRepository = contactRepository;
        this.historyRepository = historyRepository;
        this.parser = parser;
        this.notifier = notifier;
        this.clock = clock;
        this.requireAlias = requireAlias;
        this.audit = auditEventClient;
    }

    @Transactional
    public IntakeResult process(InboundEmail email) {
        String senderAddress = normalizeAddress(email.fromAddress());

        // The +alias tag in the recipient address is the trust anchor: only
        // someone who knows the account's personal intake address can file
        // into it, so a spoofed From header buys an attacker nothing.
        Optional<String> alias = extractAlias(email.toAddress());

        boolean[] created = {false};
        UserAccount account;

        if (alias.isPresent()) {
            Optional<UserAccount> byAlias = accountRepository.findByIntakeAlias(alias.get());
            if (byAlias.isEmpty()) {
                log.warn("Ignoring email to unknown intake alias '{}'", alias.get());
                return IntakeResult.ignored("IGNORED_UNKNOWN_ALIAS");
            }
            account = byAlias.get();
        } else if (requireAlias) {
            // strict mode (qa/prod): the bare intake address provisions nothing
            log.warn("Ignoring email without an intake alias from {}", senderAddress);
            return IntakeResult.ignored("IGNORED_NO_ALIAS");
        } else {
            // bootstrap path: mail to the bare intake address resolves (or
            // provisions) the account by sender and hands out an alias
            account = accountRepository.findByEmailIgnoreCase(senderAddress)
                    .orElseGet(() -> {
                        created[0] = true;
                        UserAccount newAccount = new UserAccount(displayName(email), senderAddress);
                        log.info("Provisioning account for {}", senderAddress);
                        return newAccount;
                    });
            if (account.getIntakeAlias() == null) {
                account.setIntakeAlias(generateAlias());
                account = accountRepository.save(account);
            }
            if (created[0]) {
                audit.emit("AUTH_EVENT", "ACCOUNT_PROVISIONED", account.getEmail(), "user_account", null);
            }
        }

        Optional<ParsedApplication> parsedResult = parser.parse(email);
        if (parsedResult.isEmpty()) {
            log.warn("Could not parse a company out of email '{}' from {}", email.subject(), senderAddress);
            return IntakeResult.notParsed(account.getEmail(), created[0], account.getIntakeAlias());
        }
        ParsedApplication parsed = parsedResult.get();

        // one application per owner + company + position; later emails about
        // the same application move its status forward
        Optional<JobApplication> existing =
                applicationRepository.findByOwnerIdAndCompanyNameIgnoreCase(account.getId(), parsed.companyName()).stream()
                        .filter(app -> parsed.positionTitle() == null
                                || equalsIgnoreCaseNullSafe(app.getPositionTitle(), parsed.positionTitle()))
                        .findFirst();

        JobApplication application;
        String outcome;

        if (existing.isPresent()) {
            application = existing.get();
            boolean changed = false;

            if (parsed.status() != application.getStatus()) {
                log.info("Application #{} status {} -> {}", application.getId(),
                        application.getStatus(), parsed.status());
                ApplicationStatus previous = application.getStatus();
                historyRepository.save(new StatusHistory(application, previous,
                        parsed.status(), clock.instant(), StatusHistory.SOURCE_INTAKE));
                application.setStatus(parsed.status());
                notifyQuietly(account, application, previous);
                changed = true;
            }
            if (linkContact(application, parsed)) {
                changed = true;
            }
            if (changed) {
                application = applicationRepository.save(application);
                audit.emit("DATABASE_QUERY", "INTAKE_UPDATE", account.getEmail(),
                        "job_application:" + application.getId(), null);
                outcome = "UPDATED";
            } else {
                outcome = "ALREADY_TRACKED";
            }
        } else {
            application = new JobApplication(parsed.companyName(), parsed.positionTitle(),
                    parsed.status() == null ? ApplicationStatus.APPLIED : parsed.status());
            application.setOwner(account);
            linkContact(application, parsed);
            application = applicationRepository.save(application);
            historyRepository.save(new StatusHistory(application, null,
                    application.getStatus(), clock.instant(), StatusHistory.SOURCE_INTAKE));
            notifyQuietly(account, application, null);
            audit.emit("DATABASE_QUERY", "INTAKE_CREATE", account.getEmail(),
                    "job_application:" + application.getId(), null);
            outcome = "CREATED";
            log.info("Tracked new application #{} ({} - {}, {}) for {}", application.getId(),
                    parsed.companyName(), parsed.positionTitle(), application.getStatus(), senderAddress);
        }

        return new IntakeResult(outcome, application.getId(), application.getCompanyName(),
                application.getPositionTitle(), application.getStatus(),
                account.getEmail(), created[0], account.getIntakeAlias());
    }

    /** A notification failure must never fail the intake transaction. */
    private void notifyQuietly(UserAccount account, JobApplication application,
                               ApplicationStatus fromStatus) {
        try {
            notifier.notifyChange(account, application, fromStatus);
        } catch (Exception e) {
            log.warn("Failed to notify {} about application #{}", account.getEmail(),
                    application.getId(), e);
        }
    }

    /**
     * "track+a8f3k2xq@domain" -> "a8f3k2xq"; empty when the recipient is
     * unknown or has no plus tag.
     */
    static Optional<String> extractAlias(String toAddress) {
        if (toAddress == null) {
            return Optional.empty();
        }
        String address = toAddress.trim().toLowerCase();
        int at = address.indexOf('@');
        int plus = address.indexOf('+');
        if (at < 0 || plus < 0 || plus > at) {
            return Optional.empty();
        }
        String alias = address.substring(plus + 1, at);
        return alias.isBlank() ? Optional.empty() : Optional.of(alias);
    }

    private String generateAlias() {
        StringBuilder alias = new StringBuilder(ALIAS_LENGTH);
        for (int i = 0; i < ALIAS_LENGTH; i++) {
            alias.append(ALIAS_ALPHABET[random.nextInt(ALIAS_ALPHABET.length)]);
        }
        return alias.toString();
    }

    /**
     * Attaches the parsed recruiter as the application's contact (creating
     * the Contact row once, matched by email). Never overwrites an existing
     * link. Returns whether the application changed.
     */
    private boolean linkContact(JobApplication application, ParsedApplication parsed) {
        if (!parsed.hasContact() || application.getContact() != null) {
            return false;
        }

        Contact contact = contactRepository.findByEmailIgnoreCase(parsed.contactEmail())
                .orElseGet(() -> {
                    String name = parsed.contactName() == null ? "" : parsed.contactName().trim();
                    int split = name.indexOf(' ');
                    String firstName = split < 0 ? name : name.substring(0, split);
                    String lastName = split < 0 ? "" : name.substring(split + 1);
                    log.info("Creating contact {} <{}>", name, parsed.contactEmail());
                    return contactRepository.save(new Contact(firstName, lastName, parsed.contactEmail()));
                });

        application.setContact(contact);
        return true;
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("inbound email has no sender address");
        }
        return address.trim().toLowerCase();
    }

    private String displayName(InboundEmail email) {
        if (email.fromName() != null && !email.fromName().isBlank()) {
            return email.fromName().trim();
        }
        String address = email.fromAddress().trim();
        return address.substring(0, address.indexOf('@'));
    }

    private boolean equalsIgnoreCaseNullSafe(String a, String b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.equalsIgnoreCase(b);
    }
}
