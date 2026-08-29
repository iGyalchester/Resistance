package com.resistance.intake.service;

import com.resistance.intake.dao.JobApplicationRepository;
import com.resistance.intake.dao.UserAccountRepository;
import com.resistance.intake.parser.ConfirmationEmailParser;
import com.resistance.intake.parser.ParsedApplication;
import com.resistance.shared.models.entity.ApplicationStatus;
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
 * resolve/create the account from the forwarding sender, parse the
 * confirmation, and upsert the application.
 */
@Service
public class IntakeService {

    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);

    private final UserAccountRepository accountRepository;
    private final JobApplicationRepository applicationRepository;
    private final ConfirmationEmailParser parser;

    public IntakeService(UserAccountRepository accountRepository,
                         JobApplicationRepository applicationRepository,
                         ConfirmationEmailParser parser) {
        this.accountRepository = accountRepository;
        this.applicationRepository = applicationRepository;
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

        Optional<ParsedApplication> parsed = parser.parse(email);
        if (parsed.isEmpty()) {
            log.warn("Could not parse a company out of email '{}' from {}", email.subject(), senderAddress);
            return IntakeResult.notParsed(account.getEmail(), created[0]);
        }

        String company = parsed.get().companyName();
        String position = parsed.get().positionTitle();

        // one application per owner + company + position; a second forward is a no-op
        Optional<JobApplication> existing =
                applicationRepository.findByOwnerIdAndCompanyNameIgnoreCase(account.getId(), company).stream()
                        .filter(app -> equalsIgnoreCaseNullSafe(app.getPositionTitle(), position))
                        .findFirst();

        if (existing.isPresent()) {
            JobApplication application = existing.get();
            return new IntakeResult("ALREADY_TRACKED", application.getId(),
                    application.getCompanyName(), application.getPositionTitle(),
                    account.getEmail(), created[0]);
        }

        JobApplication application = new JobApplication(company, position, ApplicationStatus.APPLIED);
        application.setOwner(account);
        application = applicationRepository.save(application);
        log.info("Tracked new application #{} ({} - {}) for {}",
                application.getId(), company, position, senderAddress);

        return new IntakeResult("CREATED", application.getId(), company, position,
                account.getEmail(), created[0]);
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
