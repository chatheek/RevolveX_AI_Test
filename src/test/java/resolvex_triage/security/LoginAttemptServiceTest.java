package resolvex_triage.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void blocksAfterTooManyFailuresAndUnblocksAfterTheLockTime() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("1.2.3.4");
        }
        assertEquals(0, service.secondsBlocked("1.2.3.4"), "not blocked before the limit");

        service.recordFailure("1.2.3.4");
        assertTrue(service.secondsBlocked("1.2.3.4") > 0, "blocked at the limit");
        assertEquals(0, service.secondsBlocked("5.6.7.8"), "other addresses unaffected");

        now.set(now.get().plus(LoginAttemptService.LOCK_TIME).plusSeconds(1));
        assertEquals(0, service.secondsBlocked("1.2.3.4"), "unblocked after the lock time");
    }

    @Test
    void oldFailuresDoNotAccumulateForever() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.recordFailure("1.2.3.4");
        }
        now.set(now.get().plus(LoginAttemptService.LOCK_TIME).plusSeconds(1));
        service.recordFailure("1.2.3.4");
        assertEquals(0, service.secondsBlocked("1.2.3.4"));
    }

    @Test
    void appRefusesToStartWithAWeakOrMissingPassword() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        assertThrows(IllegalStateException.class, () -> config.userDetailsService(encoder, "admin", "short"));
        assertThrows(IllegalStateException.class, () -> config.userDetailsService(encoder, " ", "a-long-test-password-123"));
        assertDoesNotThrow(() -> config.userDetailsService(encoder, "admin", "a-long-test-password-123"));
    }

    @Test
    void acceptsAReadyMadeBcryptHash() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        String hash = "{bcrypt}" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("whatever-it-is-123");
        var users = config.userDetailsService(encoder, "admin", hash);
        assertTrue(encoder.matches("whatever-it-is-123", users.loadUserByUsername("admin").getPassword()));
    }
}
