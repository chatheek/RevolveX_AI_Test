package resolvex_triage.service;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import resolvex_triage.model.Designation;
import resolvex_triage.repository.DesignationRegistry;

import java.util.*;

/**
 * Triages customer feedback with the Typesafe.ai Jev model (System One).
 * All routing decisions come from Jev; there is no local keyword fallback.
 */
@Slf4j
@Service
public class TypeSafeJevTriageService {

    private static final String Q_ROUTE = "assigned_route";
    private static final String Q_PRIORITY = "priority_score";
    /** Below this Jev confidence (%) the result is flagged for human review. */
    private static final double REVIEW_THRESHOLD_PERCENT = 50.0;

    private static final String Q_DIRECT_BM = "against_branch_manager";

    private final DesignationRegistry registry;
    private final TypeSafeClient typeSafeClient;

    public TypeSafeJevTriageService(DesignationRegistry registry, TypeSafeClient typeSafeClient) {
        this.registry = registry;
        this.typeSafeClient = typeSafeClient;
    }

    public Map<String, Object> analyzeAndRoute(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'feedback' must not be empty");
        }
        log.info("Initiating Jev AI triage analysis for feedback: '{}'", feedback);

        Choice.Builder route = Choice.builder().instructions(
                "The complaint may be written in English, Sinhala (සිංහල), Singlish (Sinhala typed in English letters) or a mix. " +
                "Understand its meaning in any of them, then apply the same rules. " +
                "Pick the single role that should receive this complaint FIRST. Follow these steps in order. " +
                "1) CONDUCT complaints go ONE LEVEL ABOVE the person they are about. Conduct means attitude or behaviour: rude, insulting, " +
                "aggressive, mocking, dismissive, arrogant, shouting, swearing, lying, harassing, refusing to help, ignoring guests, " +
                "failing to act on a complaint, breaking rules, or unprofessional or unhygienic behaviour. Who is above whom: " +
                "host, waiter, busser or cashier -> Floor Supervisor; bartender or barista -> Bar Supervisor; cleaner -> Cleaning Supervisor; " +
                "security guard or bouncer -> Security Supervisor / Chief Security Officer; delivery rider or driver -> Delivery Supervisor; " +
                "line cook or kitchen staff -> Head Chef / Kitchen Manager. " +
                "Level 2 people (head waiter, senior waiter, any supervisor, the bar manager, kitchen manager, dispatch manager, head of security, " +
                "security head, head guard, chief security officer, the Head Chef) -> Assistant Branch Manager. " +
                "Level 3 people (assistant branch manager, assistant manager, shift manager, duty manager, shift leader, or simply 'the manager' / " +
                "'the manager on duty') -> Branch Manager. " +
                "Level 4 people (branch manager, general manager, GM, owner) -> Tenant Admin. " +
                "If conduct is described but no one is named (for example 'they were rude'), assume the frontline employee who does that work " +
                "and send it to that person's supervisor. " +
                "2) A MISTAKE or poor QUALITY of someone's work is not conduct. A mistake is an error or carelessness (forgot, wrong item, wrong bill, " +
                "spilled, dropped, cleared too early, handed over crumpled, did not offer, left dirty, drink or dish made badly). It stays with the Level 1 " +
                "role that does that work, even when written with 'they'. Routine one-off tasks also stay at Level 1 even if no person is named " +
                "(empty soap or toilet paper, full bin, wet floor, dirty table, empty glass, a smell, a flooded restroom). " +
                "3) Problems that are area-wide, recurring or about systems go to the Level 2 supervisor or head who owns that area: pests and hygiene " +
                "standards, shortages (no clean glasses, ran out of ingredients), broken equipment, cameras or lighting, safety, kitchen or floor timing, " +
                "nobody in charge, and delivery tracking and dispatch decisions (bundling orders, routing, assigning drivers, gate codes), which belong to the " +
                "Delivery Supervisor and not to the rider. " +
                "4) Branch-wide matters (overall quality, menu, prices, portions, understaffing, decor, opening hours, unanswered messages to management) " +
                "go to Branch Manager. App, website, loyalty points, gift cards and other franchise-wide platform problems go to Tenant Admin.");
        registry.getAllLevels().values().forEach(list ->
                list.forEach(d -> route.option(d.getTitle(), d.getDescription())));

        Score priority = Score.of(
                "How urgent is this complaint? Critical covers spoiled or unsafe food, physical abuse and manager misconduct.",
                "Low", "Medium", "High", "Critical");

        Noul againstBranchManager = Noul.of(
                "The complaint is directly about the conduct of the Branch Manager (not the Assistant Branch Manager).");

        SystemOneResponse response;
        try {
            response = typeSafeClient.systemOne(feedback, Map.of(
                    Q_ROUTE, route.build(),
                    Q_PRIORITY, priority,
                    Q_DIRECT_BM, againstBranchManager));
        } catch (TypeSafeException e) {
            log.error("Jev API call failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jev AI service call failed: " + e.getMessage(), e);
        }

        String routedTitle = response.choiceValue(Q_ROUTE);
        double confidence = response.choice(Q_ROUTE).confidence() * 100.0;
        double priorityScore = response.scoreValue(Q_PRIORITY);
        boolean directAgainstBm = response.noulValue(Q_DIRECT_BM) >= 0.5;

        log.info("Jev response ({}): route='{}', confidence={}%, priority={}, againstBranchManager={}",
                response.model(), routedTitle, confidence, priorityScore, directAgainstBm);
        Map<String, Object> result = buildTriageResult(routedTitle, priorityScore, confidence, directAgainstBm);
        result.put("needsReview", confidence < REVIEW_THRESHOLD_PERCENT);
        result.put("alternatives", runnersUp(response.choice(Q_ROUTE).probabilities(), routedTitle));
        return result;
    }

    /** The next most likely roles according to Jev, so a human can pick one when confidence is low. */
    private List<Map<String, Object>> runnersUp(Map<String, Double> probabilities, String chosen) {
        return probabilities.entrySet().stream()
                .filter(e -> !e.getKey().equals(chosen))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(2)
                .map(e -> Map.<String, Object>of("role", e.getKey(),
                        "probability", Math.round(e.getValue() * 1000.0) / 10.0))
                .toList();
    }

    private Map<String, Object> buildTriageResult(String routedTitle, double priorityVal,
                                                  double confidence, boolean directAgainstBm) {
        Designation routedTo = registry.getByTitle(routedTitle);
        if (routedTo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Jev returned an unknown role: " + routedTitle);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("routedTo", routedTo.getTitle());
        result.put("routedLevel", routedTo.getLevel());
        result.put("priority", mapScoreToPriority(priorityVal));
        result.put("confidence", Math.round(confidence * 10.0) / 10.0);
        result.put("isDirectAgainstBranchManager", directAgainstBm);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> initialStep = new HashMap<>();
        initialStep.put("step", 1);
        initialStep.put("action", "Initial AI Triage Assignment");
        initialStep.put("assignedTo", routedTo.getTitle());
        initialStep.put("level", routedTo.getLevel());
        initialStep.put("timestamp", new Date().toString());
        history.add(initialStep);

        result.put("escalationHistory", history);
        return result;
    }

    /** Jev score answers are 0-based: 0=Low, 1=Medium, 2=High, 3=Critical (continuous in between). */
    private String mapScoreToPriority(double score) {
        if (score >= 2.5) return "Critical Priority";
        if (score >= 1.7) return "High Priority";
        if (score >= 0.8) return "Medium Priority";
        return "Low Priority";
    }
}
