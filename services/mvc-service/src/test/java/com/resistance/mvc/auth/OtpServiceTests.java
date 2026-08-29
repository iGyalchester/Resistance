package com.resistance.mvc.auth;

import com.resistance.mvc.dao.LoginCodeRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.LoginCode;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    private UserAccountRepository accounts;
    private LoginCodeRepository codes;
    private AtomicReference<String> sentCode;
    private OtpService otpService;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        codes = mock(LoginCodeRepository.class);
        sentCode = new AtomicReference<>();
        otpService = new OtpService(accounts, codes,
                (acct, code) -> sentCode.set(code),
                Clock.fixed(NOW, ZoneOffset.UTC));

        account = new UserAccount("Boris Gerard", "boris@gmail.com");
        account.setId(7);
        when(accounts.findByEmailIgnoreCase("boris@gmail.com")).thenReturn(Optional.of(account));
        when(codes.save(any(LoginCode.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private LoginCode issueCode() {
        when(codes.findByAccountIdAndConsumedFalse(7)).thenReturn(List.of());
        otpService.requestCode("boris@gmail.com");

        ArgumentCaptor<LoginCode> saved = ArgumentCaptor.forClass(LoginCode.class);
        verify(codes).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void requestStoresHashNotPlainCode() {
        LoginCode stored = issueCode();

        assertNotNull(sentCode.get());
        assertEquals(6, sentCode.get().length());
        assertNotEquals(sentCode.get(), stored.getCodeHash());
        assertEquals(OtpService.hash(sentCode.get()), stored.getCodeHash());
        assertEquals(NOW.plus(OtpService.CODE_TTL), stored.getExpiresAt());
    }

    @Test
    void correctCodeVerifiesAndConsumes() {
        LoginCode stored = issueCode();
        when(codes.findByAccountIdAndConsumedFalse(7)).thenReturn(List.of(stored));

        Optional<UserAccount> result = otpService.verify("boris@gmail.com", sentCode.get());

        assertTrue(result.isPresent());
        assertEquals(7, result.get().getId());
        assertTrue(stored.isConsumed());
    }

    @Test
    void wrongCodeFailsAndCountsAttempt() {
        LoginCode stored = issueCode();
        when(codes.findByAccountIdAndConsumedFalse(7)).thenReturn(List.of(stored));

        assertTrue(otpService.verify("boris@gmail.com", "000001").isEmpty());
        assertEquals(1, stored.getAttempts());
        assertFalse(stored.isConsumed());
    }

    @Test
    void codeUnusableAfterMaxAttempts() {
        LoginCode stored = issueCode();
        stored.setAttempts(LoginCode.MAX_ATTEMPTS);
        when(codes.findByAccountIdAndConsumedFalse(7)).thenReturn(List.of(stored));

        assertTrue(otpService.verify("boris@gmail.com", sentCode.get()).isEmpty());
    }

    @Test
    void expiredCodeIsRejected() {
        LoginCode stored = issueCode();
        stored.setExpiresAt(NOW.minus(Duration.ofSeconds(1)));
        when(codes.findByAccountIdAndConsumedFalse(7)).thenReturn(List.of(stored));

        assertTrue(otpService.verify("boris@gmail.com", sentCode.get()).isEmpty());
    }

    @Test
    void unknownEmailIsSilentlyIgnored() {
        when(accounts.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        otpService.requestCode("nobody@example.com");
        assertNull(sentCode.get());
        assertTrue(otpService.verify("nobody@example.com", "123456").isEmpty());
    }

    @Test
    void newRequestRetiresPreviousCode() {
        LoginCode first = issueCode();
        when(codes.findByAccountIdAndConsumedFalse(7)).thenReturn(List.of(first));

        otpService.requestCode("boris@gmail.com");
        assertTrue(first.isConsumed());
    }
}
