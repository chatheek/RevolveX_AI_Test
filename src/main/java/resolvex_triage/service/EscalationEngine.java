package resolvex_triage.service;

import lombok.extern.slf4j.Slf4j;
import resolvex_triage.model.Designation;
import resolvex_triage.repository.DesignationRegistry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EscalationEngine {

    private final DesignationRegistry registry;

    public EscalationEngine(DesignationRegistry registry) {
        this.registry = registry;
    }

    public Designation escalate(String currentTitle, boolean isDirectAgainstBranchManager) {
        Designation current = registry.getByTitle(currentTitle);
        if (current == null) return registry.getByTitle("Floor Supervisor");

        log.info("Escalating from '{}' (Level {}). Direct against Branch Manager: {}", 
                current.getTitle(), current.getLevel(), isDirectAgainstBranchManager);

        // Rule 1: Tenant Admin is reached ONLY if the complaint is directly against the Branch Manager
        if (current.getLevel() == 4) {
            if (isDirectAgainstBranchManager) {
                log.info("Direct complaint against Branch Manager. Escalating to Tenant Admin (Level 5)");
                return registry.getByTitle("Tenant Admin");
            } else {
                log.info("Maximum escalation limit reached at Branch Manager (Level 4)");
                return registry.getByTitle("Branch Manager");
            }
        }

        // Rule 2: Escalations for Level 5 remain at Tenant Admin
        if (current.getLevel() == 5) {
            log.info("Maximum escalation limit reached at Tenant Admin (Level 5)");
            return registry.getByTitle("Tenant Admin");
        }

        // Standard hierarchy escalation path
        Designation next = switch (current.getLevel()) {
            case 1 -> getL1Supervisor(current.getTitle());
            case 2 -> registry.getByTitle("Assistant Branch Manager");
            case 3 -> registry.getByTitle("Branch Manager");
            default -> current;
        };

        log.info("Escalated to '{}' (Level {})", next.getTitle(), next.getLevel());
        return next;
    }

    private Designation getL1Supervisor(String title) {
        return switch (title) {
            case "Bartender", "Barista" -> registry.getByTitle("Bar Supervisor");
            case "Cleaner / Housekeeper" -> registry.getByTitle("Cleaning Supervisor");
            case "Security Guard" -> registry.getByTitle("Security Supervisor / Chief Security Officer");
            case "Delivery Rider / Driver" -> registry.getByTitle("Delivery Supervisor");
            case "General Chef / Line Cook" -> registry.getByTitle("Head Chef / Kitchen Manager");
            default -> registry.getByTitle("Floor Supervisor");
        };
    }
}