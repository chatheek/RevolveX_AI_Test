package resolvex_triage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.web.server.ResponseStatusException;
import resolvex_triage.repository.DesignationRegistry;
import resolvex_triage.service.EscalationEngine;
import resolvex_triage.service.EscalationEngine.Decision;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class EscalationEngineTest {

    private TypeSafeClient client;
    private EscalationEngine engine;

    @BeforeEach
    void setUp() {
        DesignationRegistry registry = new DesignationRegistry();
        registry.init();
        client = mock(TypeSafeClient.class);
        engine = new EscalationEngine(registry, client);
    }

    private void jevAnswers(String role, Map<String, Double> probabilities, double confidence) {
        Map<String, Answer> answers = Map.of("escalate_to", new ChoiceAnswer(role, probabilities, confidence));
        when(client.systemOne(anyMap(), anyMap())).thenReturn(new SystemOneResponse("jev-test", answers, null));
    }

    @Test
    void tenantAdminIsTheCeilingAndJevIsNotCalled() {
        Decision d = engine.escalate("anything", "Tenant Admin", true, List.of());
        assertTrue(d.limitReached());
        assertEquals("Tenant Admin", d.target().getTitle());
        verifyNoInteractions(client);
    }

    @Test
    void branchManagerIsTheCeilingUnlessComplaintIsAboutThem() {
        Decision stop = engine.escalate("anything", "Branch Manager", false, List.of());
        assertTrue(stop.limitReached());
        assertEquals("Branch Manager", stop.target().getTitle());

        Decision up = engine.escalate("manager shouted", "Branch Manager", true, List.of());
        assertFalse(up.limitReached());
        assertEquals("Tenant Admin", up.target().getTitle());
        verifyNoInteractions(client);
    }

    @Test
    void singleAllowedTargetNeedsNoAi() {
        Decision d = engine.escalate("anything", "Assistant Branch Manager", false, List.of());
        assertEquals("Branch Manager", d.target().getTitle());
        assertFalse(d.aiDecided());
        verifyNoInteractions(client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void jevChoosesOnlyAmongRolesAboveAndNeverTenantAdminUnlessBranchManagerComplaint() {
        jevAnswers("Bar Supervisor", Map.of("Bar Supervisor", 0.8, "Assistant Branch Manager", 0.15, "Branch Manager", 0.05), 0.8);

        Decision d = engine.escalate("Bartender ignored us", "Bartender", false, List.of("Bartender"));

        assertEquals("Bar Supervisor", d.target().getTitle());
        assertTrue(d.aiDecided());
        assertEquals(80.0, d.confidence());
        assertEquals("Assistant Branch Manager", d.alternatives().get(0).role());

        ArgumentCaptor<Map> questions = ArgumentCaptor.forClass(Map.class);
        verify(client).systemOne(anyMap(), questions.capture());
        Choice choice = (Choice) questions.getValue().get("escalate_to");
        assertTrue(choice.criteria().containsKey("Bar Supervisor"));
        assertTrue(choice.criteria().containsKey("Branch Manager"));
        assertFalse(choice.criteria().containsKey("Waiter / Waitress"), "must not offer same-level or lower roles");
        assertFalse(choice.criteria().containsKey("Bartender"), "must not offer the current role");
        assertFalse(choice.criteria().containsKey("Tenant Admin"), "Tenant Admin only for Branch Manager complaints");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenantAdminIsOfferedWhenComplaintIsAboutBranchManager() {
        jevAnswers("Tenant Admin", Map.of("Tenant Admin", 0.9, "Branch Manager", 0.1), 0.9);

        Decision d = engine.escalate("branch manager insulted us", "Floor Supervisor", true, List.of());

        assertEquals("Tenant Admin", d.target().getTitle());
        ArgumentCaptor<Map> questions = ArgumentCaptor.forClass(Map.class);
        verify(client).systemOne(anyMap(), questions.capture());
        assertTrue(((Choice) questions.getValue().get("escalate_to")).criteria().containsKey("Tenant Admin"));
    }

    @Test
    void roleOutsideTheAllowedListIsRejected() {
        jevAnswers("Waiter / Waitress", Map.of("Waiter / Waitress", 0.9), 0.9);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> engine.escalate("slow service", "Barista", false, List.of()));
        assertEquals(502, e.getStatusCode().value());
    }

    @Test
    void unknownRoleAndMissingFeedbackAreBadRequests() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> engine.escalate("x", "Janitor", false, List.of())).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> engine.escalate("  ", "Barista", false, List.of())).getStatusCode().value());
    }
}
