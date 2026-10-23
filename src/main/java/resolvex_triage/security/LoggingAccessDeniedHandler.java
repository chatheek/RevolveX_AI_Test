package resolvex_triage.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Sends the normal 403 but first logs why, so a rejected sign-in or CSRF check is visible in the server log. */
@Slf4j
@Component
class LoggingAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandlerImpl delegate = new AccessDeniedHandlerImpl();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, jakarta.servlet.ServletException {
        log.warn("Access denied (403): {} {} - {} | xsrfCookie={} csrfHeader={} csrfParam={} scheme={} secure={} remote={}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName(),
                hasCookie(request, "XSRF-TOKEN"), request.getHeader("X-XSRF-TOKEN") != null,
                request.getParameter("_csrf") != null, request.getScheme(), request.isSecure(), request.getRemoteAddr());
        delegate.handle(request, response, exception);
    }

    private static boolean hasCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
