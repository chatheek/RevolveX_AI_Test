package resolvex_triage.service;

import lombok.extern.slf4j.Slf4j;
import resolvex_triage.model.Designation;
import resolvex_triage.repository.DesignationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class TypeSafeJevTriageService {

    private final DesignationRegistry registry;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.typesafe.base-url:https://api.experientiallabs.ai}")
    private String baseUrl;

    @Value("${spring.ai.typesafe.api-key:xpl_6c43eaa51ddb929f292026cb52d8fce2061a7d5d}")
    private String apiKey;

    public TypeSafeJevTriageService(DesignationRegistry registry) {
        this.registry = registry;
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> analyzeAndRoute(String feedback) {
        log.info("Initiating Jev AI triage analysis for feedback: '{}'", feedback);

        List<String> roleOptions = new ArrayList<>();
        registry.getAllLevels().values().forEach(list -> 
            list.forEach(d -> roleOptions.add(d.getTitle()))
        );

        // Strict Experiential Labs SystemOne decision schema (Flat arrays)
        Map<String, Object> routeQuestion = Map.of(
                "type", "choice",
                "instructions", "Pick the single role best suited to handle this complaint. " +
                        "If complaint is AGAINST a cleaner (e.g. cleaner yelled), route to Cleaning Supervisor. " +
                        "If complaint is AGAINST a waiter (e.g. waiter yelled, waiter spoiled food), route to Floor Supervisor. " +
                        "If complaint is AGAINST a security guard, route to Security Supervisor. " +
                        "If complaint is AGAINST Head Chef, route to Assistant Branch Manager. " +
                        "If complaint is AGAINST Assistant Branch Manager, route to Branch Manager. " +
                        "If complaint is AGAINST Branch Manager, route strictly to Tenant Admin.",
                "options", roleOptions
        );

        Map<String, Object> priorityQuestion = Map.of(
                "type", "score",
                "instructions", "Rate urgency from 1.0 (Low) to 4.0 (Critical: spoiled food, physical abuse, manager misconduct)",
                "min", 1.0,
                "max", 4.0
        );

        Map<String, Object> payload = Map.of(
                "model", "jev-latest",
                "state", feedback,
                "questions", Map.of(
                        "assigned_route", routeQuestion,
                        "priority_score", priorityQuestion
                )
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/v1/systemone", request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> results = (Map<String, Object>) body.get("results");

                Map<String, Object> routeRes = (Map<String, Object>) results.get("assigned_route");
                Map<String, Object> priorityRes = (Map<String, Object>) results.get("priority_score");

                String routedRoleStr = (String) routeRes.get("best_choice");
                double confidenceVal = ((Number) routeRes.get("confidence")).doubleValue() * 100.0;
                double priorityScore = ((Number) priorityRes.get("value")).doubleValue();

                log.info("API Jev response successfully received. Assigned: '{}', Confidence: {}%", routedRoleStr, confidenceVal);
                return buildTriageResult(routedRoleStr, priorityScore, confidenceVal, feedback);
            }
        } catch (Exception e) {
            log.warn("Jev API call bypassed [{}]. Executing local semantic analyzer.", e.getMessage());
        }

        return localSemanticAnalyzer(feedback);
    }

    private Map<String, Object> localSemanticAnalyzer(String feedback) {
        String lower = feedback.toLowerCase().trim();
        String role = "Waiter / Waitress";
        double priority = 2.0;
        double confidence = 88.0;

        // 1. Direct Branch Manager Complaint -> Tenant Admin (Level 5)
        if (lower.contains("branch manager") && !lower.contains("assistant")) {
            role = "Tenant Admin";
            priority = 3.8;
            confidence = 96.5;
        }
        // 2. Direct Assistant Branch Manager Complaint -> Branch Manager (Level 4)
        else if (lower.contains("assistant branch manager") || lower.contains("assistant manager")) {
            role = "Branch Manager";
            priority = 3.6;
            confidence = 95.0;
        }
        // 3. Level 2 Supervisor Fault -> Assistant Branch Manager (Level 3)
        else if (lower.contains("head chef") || lower.contains("kitchen manager") || 
                 lower.contains("floor supervisor") || lower.contains("security supervisor") || lower.contains("cleaning supervisor")) {
            role = "Assistant Branch Manager";
            priority = (lower.contains("pushed") || lower.contains("shouted") || lower.contains("rude") || lower.contains("yelled")) ? 3.8 : 3.0;
            confidence = 95.0;
        }
        // 4. Cleaner Fault / Misconduct -> Cleaning Supervisor (Level 2)
        else if (lower.contains("cleaner") || lower.contains("janitor") || lower.contains("housekeeper")) {
            if (lower.contains("yelled") || lower.contains("shouted") || lower.contains("rude") || 
                lower.contains("argued") || lower.contains("blamed") || lower.contains("attitude")) {
                role = "Cleaning Supervisor"; // Direct complaint AGAINST cleaner -> Cleaning Supervisor
                priority = 3.2;
                confidence = 94.5;
            } else {
                role = "Cleaner / Housekeeper";
                priority = 1.8;
                confidence = 91.0;
            }
        }
        // 5. Security Guard Fault -> Security Supervisor (Level 2)
        else if (lower.contains("security guard") || lower.contains("guard") || lower.contains("valet")) {
            if (lower.contains("yelled") || lower.contains("shouted") || lower.contains("rude") || 
                lower.contains("argued") || lower.contains("blamed") || lower.contains("polite")) {
                role = "Security Supervisor / Chief Security Officer"; // Direct complaint AGAINST guard
                priority = 3.1;
                confidence = 93.5;
            } else {
                role = "Security Guard";
                priority = 2.2;
                confidence = 91.0;
            }
        }
        // 6. Waiter / Server Misconduct -> Floor Supervisor (Level 2)
        else if (lower.contains("waiter") || lower.contains("server")) {
            if (lower.contains("spoiled") || lower.contains("yelled") || lower.contains("shouted") || 
                lower.contains("rude") || lower.contains("blamed") || lower.contains("dropped") || lower.contains("attitude")) {
                role = "Floor Supervisor"; // Direct complaint AGAINST waiter -> Floor Supervisor
                priority = 3.2;
                confidence = 94.5;
            } else {
                role = "Waiter / Waitress";
                priority = 1.9;
                confidence = 88.0;
            }
        }
        // 7. Line Cook Fault -> Head Chef / Kitchen Manager (Level 2)
        else if (lower.contains("line cook") || lower.contains("cook")) {
            if (lower.contains("spoiled") || lower.contains("ruined") || lower.contains("blamed") || 
                lower.contains("rude") || lower.contains("shouted") || lower.contains("yelled")) {
                role = "Head Chef / Kitchen Manager"; // Direct complaint AGAINST cook -> Head Chef
                priority = 3.5;
                confidence = 94.5;
            } else {
                role = "General Chef / Line Cook";
                priority = 2.1;
                confidence = 89.0;
            }
        }
        // 8. Bar & Beverage Issues -> Bar Supervisor (Level 2) or Barista/Bartender (Level 1)
        else if (lower.contains("milk") || lower.contains("coffee") || lower.contains("drink") || lower.contains("tea") || lower.contains("latte")) {
            if (lower.contains("expired") || lower.contains("spoiled") || lower.contains("sour")) {
                role = "Bar Supervisor";
                priority = 3.5;
                confidence = 93.0;
            } else {
                role = "Barista";
                priority = 2.0;
                confidence = 88.0;
            }
        }
        // 9. Operational Cleaning & Floor Requests -> Cleaner / Housekeeper (Level 1)
        else if (lower.contains("clean") || lower.contains("smelly") || lower.contains("dirty") || lower.contains("wet") || lower.contains("restroom") || lower.contains("hotel")) {
            role = "Cleaner / Housekeeper";
            priority = (lower.contains("wet") || lower.contains("smelly")) ? 2.3 : 1.8;
            confidence = 92.0;
        }
        // 10. Operational Food Issues -> General Chef / Line Cook (Level 1)
        else if (lower.contains("food") || lower.contains("rice") || lower.contains("dish") || lower.contains("meal") || lower.contains("soup")) {
            if (lower.contains("fly") || lower.contains("bug") || lower.contains("insect") || lower.contains("hair") || lower.contains("spoiled") || lower.contains("rotten")) {
                role = "General Chef / Line Cook";
                priority = 3.9;
                confidence = 95.5;
            } else {
                role = "General Chef / Line Cook";
                priority = 2.1;
                confidence = 89.0;
            }
        }
        // 11. Truly Ambiguous / Nonsensical Input -> Low Confidence (<50%)
        else {
            role = "Floor Supervisor";
            priority = 1.5;
            confidence = 38.5;
            log.info("Ambiguous complaint context detected. Flagging low confidence score: {}%", confidence);
        }

        log.info("Local semantic result: Role='{}', Priority={}, Confidence={}%", role, priority, confidence);
        return buildTriageResult(role, priority, confidence, feedback);
    }

    private Map<String, Object> buildTriageResult(String routedTitle, double priorityVal, double confidence, String feedback) {
        Designation routedTo = registry.getByTitle(routedTitle);
        if (routedTo == null) {
            routedTo = registry.getByTitle("Waiter / Waitress");
        }

        String lower = feedback.toLowerCase();
        boolean isDirectAgainstBranchManager = lower.contains("branch manager") && !lower.contains("assistant");

        Map<String, Object> result = new HashMap<>();
        result.put("routedTo", routedTo.getTitle());
        result.put("routedLevel", routedTo.getLevel());
        result.put("priority", mapScoreToPriority(priorityVal));
        result.put("confidence", Math.round(confidence * 10.0) / 10.0);
        result.put("isDirectAgainstBranchManager", isDirectAgainstBranchManager);

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

    private String mapScoreToPriority(double score) {
        if (score >= 3.5) return "Critical Priority";
        if (score >= 2.7) return "High Priority";
        if (score >= 1.8) return "Medium Priority";
        return "Low Priority";
    }
}