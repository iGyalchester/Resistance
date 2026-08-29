package com.resistance.mvc.auth;

import com.resistance.mvc.dao.LoginCodeRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.LoginCode;
import com.resistance.shared.models.entity.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Passwordless login: issues 6-digit one-time codes (only the SHA-256 hash
 * is stored) and verifies them with expiry and attempt limits.
 */
@Service
public class OtpService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final UserAccountRepository accountRepository;
    private final LoginCodeRepository codeRepository;
    private final OtpNotifier notifier;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OtpService(UserAccountRepository accountRepository,
                      LoginCodeRepository codeRepository,
                      OtpNotifier notifier,
                      Clock clock) {
        this.accountRepository = accountRepository;
        this.codeRepository = codeRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Issues and sends a fresh code. Deliberately reveals nothing about
     * whether the email is registered - callers show the same message
     * either way (no account enumeration).
     */
    @Transactional
    public void requestCode(String email) {
        Optional<UserAccount> account = accountRepository.findByEmailIgnoreCase(email.trim());
        if (account.isEmpty()) {
            log.debug("Login code requested for unknown email");
            return;
        }

        // one active code per account: retire the previous ones
        codeRepository.findByAccountIdAndConsumedFalse(account.get().getId())
                .forEach(oldCode -> {
                    oldCode.setConsumed(true);
                    codeRepository.save(oldCode);
                });

        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expiresAt = clock.instant().plus(CODE_TTL);
        codeRepository.save(new LoginCode(account.get(), hash(code), expiresAt));

        notifier.sendCode(account.get(), code);
    }

    /**
     * Verifies a submitted code; on success the code is consumed and the
     * account returned.
     */
    @Transactional
    public Optional<UserAccount> verify(String email, String submittedCode) {
        Optional<UserAccount> account = accountRepository.findByEmailIgnoreCase(email.trim());
        if (account.isEmpty()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        Optional<LoginCode> activeCode =
                codeRepository.findByAccountIdAndConsumedFalse(account.get().getId()).stream()
                        .filter(code -> code.isUsable(now))
                        .max(Comparator.comparing(LoginCode::getExpiresAt));

        if (activeCode.isEmpty()) {
            return Optional.empty();
        }

        LoginCode code = activeCode.get();
        if (MessageDigest.isEqual(
                code.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(submittedCode == null ? "" : submittedCode.trim()).getBytes(StandardCharsets.UTF_8))) {
            code.setConsumed(true);
            codeRepository.save(code);
            return account;
        }

        code.setAttempts(code.getAttempts() + 1);
        codeRepository.save(code);
        return Optional.empty();
    }

    static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
