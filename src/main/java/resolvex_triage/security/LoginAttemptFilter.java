package resolvex_triage.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Rejects sign-in attempts (login form or Basic header) from an address that has failed too often. */
@Component
class LoginAttemptFilter extends OncePerRequestFilter {

    private final LoginAttemptService attempts;

    LoginAttemptFilter(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isSignInAttempt(request)) {
            long wait = attempts.secondsBlocked(request.getRemoteAddr());
            if (wait > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(wait));
                response.setContentType("text/plain;charset=UTF-8");
                response.getOutputStream().write(("Too many failed sign-in attempts. Try again in "
                        + (wait / 60 + 1) + " minute(s).").getBytes(StandardCharsets.UTF_8));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isSignInAttempt(HttpServletRequest request) {
        return ("POST".equals(request.getMethod()) && "/login".equals(request.getRequestURI()))
                || SecurityConfig.hasBasicAuthHeader(request);
    }
}
