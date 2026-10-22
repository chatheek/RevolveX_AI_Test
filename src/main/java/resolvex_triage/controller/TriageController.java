package resolvex_triage.controller;

import lombok.extern.slf4j.Slf4j;
import resolvex_triage.model.Designation;
import resolvex_triage.repository.DesignationRegistry;
import resolvex_triage.service.EscalationEngine;
import resolvex_triage.service.TypeSafeJevTriageService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class TriageController {

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
    public Map<String, Object> escalateComplaint(@RequestBody Map<String, Object> payload) {
        String currentRole = (String) payload.get("currentRole");
        boolean isDirectAgainstBranchManager = Boolean.parseBoolean(payload.getOrDefault("isDirectAgainstBranchManager", "false").toString());
        List<Map<String, Object>> history = (List<Map<String, Object>>) payload.getOrDefault("history", new ArrayList<>());

        log.info("REST Endpoint: Received escalation request from '{}'", currentRole);

        Designation escalated = escalationEngine.escalate(currentRole, isDirectAgainstBranchManager);

        boolean limitReached = currentRole.equals(escalated.getTitle());

        Map<String, Object> newStep = new HashMap<>();
        newStep.put("step", history.size() + 1);
        newStep.put("action", limitReached ? "Maximum Escalation Limit Reached" : "Manual/SLA Escalation");
        newStep.put("assignedTo", escalated.getTitle());
        newStep.put("level", escalated.getLevel());
        newStep.put("timestamp", new Date().toString());
        history.add(newStep);

        Map<String, Object> response = new HashMap<>();
        response.put("escalatedTo", escalated.getTitle());
        response.put("escalatedLevel", escalated.getLevel());
        response.put("escalationHistory", history);
        response.put("isLimitReached", limitReached);

        return response;
    }
}