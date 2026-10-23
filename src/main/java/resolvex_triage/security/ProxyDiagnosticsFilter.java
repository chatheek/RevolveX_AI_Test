package resolvex_triage.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs how the first few page requests look to the app (scheme, client address and the usual proxy headers) so a
 * deployment behind a reverse proxy can be diagnosed from the container log. Passwords, cookies and tokens are never logged.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProxyDiagnosticsFilter extends OncePerRequestFilter {

    private static final int MAX_LOGGED = 8;
    private final AtomicInteger logged = new AtomicInteger();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (("/".equals(path) || "/login".equals(path)) && logged.incrementAndGet() <= MAX_LOGGED) {
            log.info("Proxy check #{}: {} {} scheme={} secure={} remote={} Host={} X-Forwarded-Proto={} X-Forwarded-For={} "
                            + "X-Forwarded-Host={} X-Forwarded-Port={} Forwarded={} CF-Visitor={}",
                    logged.get(), request.getMethod(), path, request.getScheme(), request.isSecure(), request.getRemoteAddr(),
                    request.getHeader("Host"), request.getHeader("X-Forwarded-Proto"), request.getHeader("X-Forwarded-For"),
                    request.getHeader("X-Forwarded-Host"), request.getHeader("X-Forwarded-Port"),
                    request.getHeader("Forwarded"), request.getHeader("CF-Visitor"));
        }
        chain.doFilter(request, response);
    }
}
