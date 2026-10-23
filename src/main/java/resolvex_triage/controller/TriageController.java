package resolvex_triage.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import resolvex_triage.model.Designation;
import resolvex_triage.repository.DesignationRegistry;
import resolvex_triage.service.EscalationEngine;
import resolvex_triage.service.TypeSafeJevTriageService;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class TriageController {

    /** Below this Jev confidence (%) a decision is flagged for human review. */
    private static final double REVIEW_THRESHOLD_PERCENT = 50.0;

    private final DesignationRegistry registry;
    private final TypeSafeJevTriageService triageService;
    private final EscalationEngine escalationEngine;

    public TriageController(DesignationRegistry registry, TypeSafeJevTriageService triageService, EscalationEngine escalationEngine) {
        this.registry = registry;
        this.triageService = triageService;
        this.escalationEngine = escalationEngine;
    }

    @GetMapping("/hierarchy")
    public Map<Integer, List<Designation>> getHierarchy() {
        return registry.getAllLevels();
    }

    @PostMapping("/complaints/triage")
    public Map<String, Object> triageComplaint(@RequestBody Map<String, String> payload) {
        String feedback = payload.get("feedback");
        log.info("REST Endpoint: Received triage request for feedback: '{}'", feedback);
        return triageService.analyzeAndRoute(feedback);
    }

    @PostMapping("/complaints/escalate")
    @SuppressWarnings("unchecked")
    public Map<String, Object> escalateComplaint(@RequestBody Map<String, Object> payload) {
        String currentRole = (String) payload.get("currentRole");
        if (currentRole == null || currentRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field 'currentRole' must not be empty");
        }
        String feedback = (String) payload.get("feedback");
        boolean isDirectAgainstBranchManager = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("isDirectAgainstBranchManager", "false")));
        List<Map<String, Object>> history = new ArrayList<>(
                (List<Map<String, Object>>) payload.getOrDefault("history", new ArrayList<>()));
        List<String> previousTitles = history.stream()
                .map(step -> String.valueOf(step.get("assignedTo"))).toList();

        log.info("REST Endpoint: Received escalation request from '{}'", currentRole);

        EscalationEngine.Decision decision =
                escalationEngine.escalate(feedback, currentRole, isDirectAgainstBranchManager, previousTitles);
        Designation escalated = decision.target();

        String action;
        if (decision.limitReached()) {
            action = "Maximum Escalation Limit Reached";
        } else if (decision.aiDecided()) {
            action = "AI Escalation (Jev, " + decision.confidence() + "% confidence)";
        } else {
            action = "Escalation (only one allowed target)";
        }

        Map<String, Object> newStep = new HashMap<>();
        newStep.put("step", history.size() + 1);
        newStep.put("action", action);
        newStep.put("assignedTo", escalated.getTitle());
        newStep.put("level", escalated.getLevel());
        newStep.put("timestamp", new Date().toString());
        history.add(newStep);

        Map<String, Object> response = new HashMap<>();
        response.put("escalatedTo", escalated.getTitle());
        response.put("escalatedLevel", escalated.getLevel());
        response.put("escalationHistory", history);
        response.put("isLimitReached", decision.limitReached());
        response.put("decidedBy", decision.aiDecided() ? "Jev AI" : "Hierarchy rule");
        response.put("confidence", decision.confidence());
        response.put("needsReview", decision.confidence() != null && decision.confidence() < REVIEW_THRESHOLD_PERCENT);
        response.put("alternatives", decision.alternatives());
        return response;
    }
}
