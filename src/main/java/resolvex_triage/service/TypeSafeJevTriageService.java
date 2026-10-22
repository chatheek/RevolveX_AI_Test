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
                "Pick the single role that should respond first. Read each role's description and choose the best match. " +
                "1) If the complaint is about the behaviour or mistake of a specific kind of staff member (a rude host, a waiter who " +
                "forgot the order, a guard who was aggressive, a bartender with an attitude, a cleaner who blocked the way), route it to " +
                "that staff role itself, even when the behaviour is rude or aggressive. The escalation process moves it to a supervisor later. " +
                "2) If the problem is systemic or about standards (supplies, equipment, training, hygiene, safety systems, dispatch or " +
                "tracking systems, timing of the whole kitchen or dining floor, policies), route it to the Level 2 supervisor or head who owns it. " +
                "3) If a manager or shift leader on duty failed to act on a complaint, route to Assistant Branch Manager. " +
                "Complaints about a Level 2 supervisor or the Head Chef go to Assistant Branch Manager, about the Assistant Branch Manager " +
                "go to Branch Manager, and about the Branch Manager go to Tenant Admin. " +
                "4) Overall branch quality, menu, prices and staffing go to Branch Manager; franchise-wide app, website or platform " +
                "issues go to Tenant Admin.");
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
