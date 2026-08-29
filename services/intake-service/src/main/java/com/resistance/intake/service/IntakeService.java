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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserAccountRepository accountRepository;
    private final JobApplicationRepository applicationRepository;
    private final ContactRepository contactRepository;
    private final ConfirmationEmailParser parser;

    public IntakeService(UserAccountRepository accountRepository,
                         JobApplicationRepository applicationRepository,
                         ContactRepository contactRepository,
                         ConfirmationEmailParser parser) {
        this.accountRepository = accountRepository;
        this.applicationRepository = applicationRepository;
        this.contactRepository = contactRepository;
        this.parser = parser;
    }

    @Transactional
    public IntakeResult process(InboundEmail email) {
        String senderAddress = normalizeAddress(email.fromAddress());

        boolean[] created = {false};
        UserAccount account = accountRepository.findByEmailIgnoreCase(senderAddress)
                .orElseGet(() -> {
                    created[0] = true;
                    UserAccount newAccount = new UserAccount(displayName(email), senderAddress);
                    log.info("Provisioning account for {}", senderAddress);
                    return accountRepository.save(newAccount);
                });

        Optional<ParsedApplication> parsedResult = parser.parse(email);
        if (parsedResult.isEmpty()) {
            log.warn("Could not parse a company out of email '{}' from {}", email.subject(), senderAddress);
            return IntakeResult.notParsed(account.getEmail(), created[0]);
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
                application.setStatus(parsed.status());
                changed = true;
            }
            if (linkContact(application, parsed)) {
                changed = true;
            }
            if (changed) {
                application = applicationRepository.save(application);
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
            outcome = "CREATED";
            log.info("Tracked new application #{} ({} - {}, {}) for {}", application.getId(),
                    parsed.companyName(), parsed.positionTitle(), application.getStatus(), senderAddress);
        }

        return new IntakeResult(outcome, application.getId(), application.getCompanyName(),
                application.getPositionTitle(), application.getStatus(),
                account.getEmail(), created[0]);
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
