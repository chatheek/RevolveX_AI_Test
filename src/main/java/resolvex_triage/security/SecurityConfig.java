package resolvex_triage.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Protects the whole app. Browsers sign in with a form (session cookie + CSRF token); scripts and curl use
 * HTTP Basic on /api. Nothing is reachable without a login except the login page itself.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final String BCRYPT_PREFIX = "{bcrypt}$2";

    /** The page loads React, Babel and Tailwind from CDNs, so scripts need those hosts plus inline/eval. */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://unpkg.com https://cdn.tailwindcss.com; "
                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; "
                    + "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Single account from configuration. There is deliberately no default username or password: the app
     * refuses to start without them. APP_PASSWORD may also be a ready-made "{bcrypt}$2..." hash.
     */
    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
                                          @Value("${app.security.username}") String username,
                                          @Value("${app.security.password}") String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("APP_USERNAME must not be empty");
        }
        boolean alreadyHashed = password != null && password.startsWith(BCRYPT_PREFIX);
        if (!alreadyHashed && (password == null || password.length() < MIN_PASSWORD_LENGTH)) {
            throw new IllegalStateException("APP_PASSWORD must be at least " + MIN_PASSWORD_LENGTH
                    + " characters (or a {bcrypt} hash)");
        }
        String stored = alreadyHashed ? password : encoder.encode(password);
        return new InMemoryUserDetailsManager(User.withUsername(username.trim()).password(stored).roles("ADMIN").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptFilter loginAttemptFilter,
                                            LoggingAccessDeniedHandler accessDeniedHandler,
                                            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> {
            if (secureCookies) {
                cookie.secure(true); // do not depend on the proxy telling the app the request was https
            }
            cookie.sameSite("Lax");
        });
        RequestMatcher api = request -> request.getRequestURI().startsWith("/api/");
        RequestMatcher basicAuthHeader = SecurityConfig::hasBasicAuthHeader;

        // API calls get a plain 401 (no WWW-Authenticate, so browsers never pop up a login box); pages go to /login.
        HttpStatusEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.defaultSuccessUrl("/", true))
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                // Only /api gets the plain 401. Do not set a global entry point: that would switch off the generated login page.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(unauthorized, api)
                        .accessDeniedHandler(accessDeniedHandler))
                // Browser sessions are protected by a CSRF token (cookie read by the page's script and sent as a header).
                // Requests that carry an explicit Basic header are not sent automatically by browsers, so they skip it.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(basicAuthHeader))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(Customizer.withDefaults()));
        return http.build();
    }

    static boolean hasBasicAuthHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
    }
}
