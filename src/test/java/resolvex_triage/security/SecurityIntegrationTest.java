package resolvex_triage.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.ai.typesafe.api-key=test-key",
        "app.security.username=tester",
        "app.security.password=a-long-test-password-123"})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String ESCALATE_BODY = "{\"currentRole\":\"Tenant Admin\",\"isDirectAgainstBranchManager\":true,\"history\":[]}";

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor fromAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @Test
    void apiWithoutLoginIs401WithoutABrowserPopup() throws Exception {
        mvc.perform(get("/api/v1/hierarchy"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void pageWithoutLoginRedirectsToTheLoginPage() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageIsPublicAndCarriesSecurityHeaders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void correctBasicCredentialsOpenTheApi() throws Exception {
        mvc.perform(get("/api/v1/hierarchy").with(httpBasic("tester", "a-long-test-password-123")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").with(httpBasic("tester", "a-long-test-password-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(get("/api/v1/hierarchy").with(httpBasic("tester", "not-the-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInBrowserSessionNeedsACsrfTokenToChangeThings() throws Exception {
        mvc.perform(post("/api/v1/complaints/escalate").with(user("tester")).contentType(MediaType.APPLICATION_JSON)
                        .content(ESCALATE_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/complaints/escalate").with(user("tester")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ESCALATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void scriptsUsingBasicAuthDoNotNeedACsrfToken() throws Exception {
        mvc.perform(post("/api/v1/complaints/escalate").with(httpBasic("tester", "a-long-test-password-123"))
                        .contentType(MediaType.APPLICATION_JSON).content(ESCALATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedPostIsRejected() throws Exception {
        mvc.perform(post("/api/v1/complaints/triage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"x\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void repeatedWrongPasswordsAreBlockedWith429() throws Exception {
        RequestPostProcessor attacker = fromAddress("10.9.9.9");
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            mvc.perform(get("/api/v1/hierarchy").with(attacker).with(httpBasic("tester", "guess-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        // even the correct password is refused while blocked
        mvc.perform(get("/api/v1/hierarchy").with(attacker).with(httpBasic("tester", "a-long-test-password-123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // another address is not affected
        mvc.perform(get("/api/v1/hierarchy").with(fromAddress("10.8.8.8")).with(httpBasic("tester", "a-long-test-password-123")))
                .andExpect(status().isOk());
    }
}
