package resolvex_triage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    /** Lets the page show who is signed in. */
    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }
}
