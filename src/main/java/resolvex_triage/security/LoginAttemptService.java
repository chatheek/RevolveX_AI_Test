package resolvex_triage.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@link #MAX_FAILURES} wrong passwords from one address, that address is
 * blocked for {@link #LOCK_TIME}. State is in memory, so it resets when the app restarts.
 */
@Slf4j
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record State(int failures, Instant windowStart, Instant blockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    @EventListener
    void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String address = addressOf(event.getAuthentication());
        if (address != null) {
            recordFailure(address);
        }
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        String address = addressOf(event.getAuthentication());
        if (address != null) {
            states.remove(address);
        }
    }

    void recordFailure(String address) {
        Instant now = clock.instant();
        if (states.size() > CLEANUP_THRESHOLD) {
            states.values().removeIf(s -> expired(s, now));
        }
        states.compute(address, (key, old) -> {
            if (old == null || expired(old, now)) {
                return new State(1, now, null);
            }
            int failures = old.failures() + 1;
            if (failures >= MAX_FAILURES) {
                log.warn("Too many failed logins from {}; blocking for {}", address, LOCK_TIME);
                return new State(failures, old.windowStart(), now.plus(LOCK_TIME));
            }
            return new State(failures, old.windowStart(), old.blockedUntil());
        });
    }

    /** Seconds until the address may try again, or 0 when it is not blocked. */
    long secondsBlocked(String address) {
        State state = states.get(address);
        Instant now = clock.instant();
        if (state == null || state.blockedUntil() == null || !state.blockedUntil().isAfter(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, state.blockedUntil()).toSeconds());
    }

    private boolean expired(State state, Instant now) {
        Instant end = state.blockedUntil() != null ? state.blockedUntil() : state.windowStart().plus(LOCK_TIME);
        return !end.isAfter(now);
    }

    private static String addressOf(Authentication authentication) {
        return authentication != null && authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress() : null;
    }
}
