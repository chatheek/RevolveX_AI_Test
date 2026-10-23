package resolvex_triage.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Behind a proxy that hides the https scheme, redirects must stay relative and the CSRF cookie must be Secure. */
@SpringBootTest(properties = {
        "spring.ai.typesafe.api-key=test-key",
        "app.security.username=tester",
        "app.security.password=a-long-test-password-123",
        "server.servlet.session.cookie.secure=true"})
@AutoConfigureMockMvc
class BehindProxyCookieTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void loginRedirectIsRelativeSoTheBrowserKeepsItsScheme() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void csrfCookieIsMarkedSecureEvenThoughTheContainerSawPlainHttp() throws Exception {
        MvcResult result = mvc.perform(get("/login").accept(MediaType.TEXT_HTML)).andExpect(status().isOk()).andReturn();
        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("XSRF-TOKEN=") && c.contains("Secure")),
                "XSRF-TOKEN cookie should be Secure, got " + cookies);
    }
}
