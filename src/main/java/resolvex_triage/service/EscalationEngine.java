package resolvex_triage.service;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import resolvex_triage.model.Designation;
import resolvex_triage.repository.DesignationRegistry;

import java.util.*;

/**
 * Decides who a complaint is escalated to. Jev chooses the next role from the complaint text, but only among
 * roles the hierarchy allows, so the AI can never move a complaint down or skip the Tenant Admin rule.
 */
@Slf4j
@Service
public class EscalationEngine {

    private static final String Q_NEXT = "escalate_to";
    private static final String TENANT_ADMIN = "Tenant Admin";

    private final DesignationRegistry registry;
    private final TypeSafeClient typeSafeClient;

    public EscalationEngine(DesignationRegistry registry, TypeSafeClient typeSafeClient) {
        this.registry = registry;
        this.typeSafeClient = typeSafeClient;
    }

    /** One of Jev's other likely choices, as a percentage. */
    public record Alternative(String role, double probability) {}

    /**
     * @param target       the role the complaint goes to (the current role when the limit is reached)
     * @param limitReached true when there is nowhere higher to escalate
     * @param aiDecided    true when Jev picked among several options, false when only one option existed
     * @param confidence   Jev's confidence in percent, or null when Jev was not asked
     */
    public record Decision(Designation target, boolean limitReached, boolean aiDecided,
                           Double confidence, List<Alternative> alternatives) {}

    public Decision escalate(String feedback, String currentTitle, boolean directAgainstBranchManager,
                             List<String> previousTitles) {
        Designation current = registry.getByTitle(currentTitle);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + currentTitle);
        }

        List<Designation> candidates = candidatesAbove(current, directAgainstBranchManager);
        log.info("Escalating from '{}' (Level {}). Direct against Branch Manager: {}. Allowed targets: {}",
                current.getTitle(), current.getLevel(), directAgainstBranchManager,
                candidates.stream().map(Designation::getTitle).toList());

        if (candidates.isEmpty()) {
            log.info("Maximum escalation limit reached at '{}'", current.getTitle());
            return new Decision(current, true, false, null, List.of());
        }
        if (candidates.size() == 1) {
            return new Decision(candidates.get(0), false, false, null, List.of());
        }
        if (feedback == null || feedback.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'feedback' is required to escalate");
        }
        return askJev(feedback, current, candidates, previousTitles);
    }

    /** Roles strictly above the current level; Tenant Admin only for complaints about the Branch Manager. */
    private List<Designation> candidatesAbove(Designation current, boolean directAgainstBranchManager) {
        List<Designation> result = new ArrayList<>();
        registry.getAllLevels().values().forEach(list -> list.stream()
                .filter(d -> d.getLevel() > current.getLevel())
                .filter(d -> directAgainstBranchManager || !TENANT_ADMIN.equals(d.getTitle()))
                .forEach(result::add));
        result.sort(Comparator.comparingInt(Designation::getLevel));
        return result;
    }

    private Decision askJev(String feedback, Designation current, List<Designation> candidates,
                            List<String> previousTitles) {
        Choice.Builder next = Choice.builder().instructions(
                "This complaint was already assigned to the current role and needs to be escalated. Choose who should take it next. " +
                "1) Prefer the lowest-level role that owns the problem and has the authority to fix it, for example the supervisor " +
                "responsible for the same area (bar, cleaning, security, delivery, kitchen, dining floor). " +
                "2) Skip straight to Assistant Branch Manager or Branch Manager only for serious misconduct, safety or legal risk, " +
                "an unresolved complaint, or when the complaint is about the current role's own supervisor. " +
                "3) Choose Tenant Admin only if it is offered and the complaint is about the Branch Manager.");
        candidates.forEach(d -> next.option(d.getTitle(), d.getDescription()));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("complaint", feedback);
        state.put("currently_assigned_to", current.getTitle() + " (Level " + current.getLevel() + ")");
        state.put("previously_assigned_to", previousTitles == null ? List.of() : previousTitles);

        SystemOneResponse response;
        try {
            response = typeSafeClient.systemOne(state, Map.of(Q_NEXT, next.build()));
        } catch (TypeSafeException e) {
            log.error("Jev escalation call failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jev AI service call failed: " + e.getMessage(), e);
        }

        String chosen = response.choiceValue(Q_NEXT);
        Designation target = candidates.stream().filter(d -> d.getTitle().equals(chosen)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Jev returned a role that is not an allowed escalation target: " + chosen));

        ChoiceAnswer answer = response.choice(Q_NEXT);
        double confidence = Math.round(answer.confidence() * 1000.0) / 10.0;
        log.info("Jev escalation ({}): '{}' -> '{}' (confidence {}%)", response.model(), current.getTitle(),
                target.getTitle(), confidence);
        return new Decision(target, false, true, confidence, runnersUp(answer.probabilities(), chosen));
    }

    private List<Alternative> runnersUp(Map<String, Double> probabilities, String chosen) {
        return probabilities.entrySet().stream()
                .filter(e -> !e.getKey().equals(chosen))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .map(e -> new Alternative(e.getKey(), Math.round(e.getValue() * 1000.0) / 10.0))
                .toList();
    }
}
