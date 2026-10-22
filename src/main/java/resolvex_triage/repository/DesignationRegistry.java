package resolvex_triage.repository;

import resolvex_triage.model.Designation;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Component
public class DesignationRegistry {

    private final Map<String, Designation> designationMap = new LinkedHashMap<>();
    private final Map<Integer, List<Designation>> levelMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // Level 5
        add(new Designation("Tenant Admin", "Handles complaints about the Branch Manager's own conduct, and platform-wide or franchise-wide issues such as the loyalty/membership app, global points or system outages.", 5));

        // Level 4
        add(new Designation("Branch Manager", "Handles complaints about the overall quality, menu, pricing, value for money or reputation of the branch, and complaints about the Assistant Branch Manager.", 4));

        // Level 3
        add(new Designation("Assistant Branch Manager", "Handles shift-level operational complaints: a shift leader or staff who ignored a complaint and nothing was fixed, ambience problems (temperature, music volume), and complaints about any Level 2 supervisor or the Head Chef.", 3));

        // Level 2
        add(new Designation("Head Waiter", "Handles complaints about service skills and staff training in the dining room: waiters who lack basic skills (wine service, dropping cutlery, table etiquette) and need coaching.", 2));
        add(new Designation("Head Chef / Kitchen Manager", "Handles kitchen-wide problems: slow ticket times, long delays getting food out, food quality and food safety standards, and complaints about line cooks.", 2));
        add(new Designation("Floor Supervisor", "Handles chaos or poor management of the dining floor, staff arguing or misbehaving in front of guests, and misconduct complaints about waiters, hosts, bussers and cashiers.", 2));
        add(new Designation("Bar Supervisor", "Handles bar operations problems: missing or unavailable bar supplies, glassware shortages, beverage menu items unavailable, and bar staff misconduct.", 2));
        add(new Designation("Cleaning Supervisor", "Handles hygiene and sanitation standards: pests, insects, rodents, unsanitary restrooms or areas, and complaints about cleaner or janitor conduct.", 2));
        add(new Designation("Security Supervisor / Chief Security Officer", "Handles safety and security concerns: unsafe, poorly lit or unmonitored parking and premises, missing patrols, and security guard or parking attendant conduct.", 2));
        add(new Designation("Delivery Supervisor", "Handles delivery and dispatch system problems: broken order tracking, unhelpful dispatch, late deliveries as a service failure, and delivery rider conduct.", 2));

        // Level 1
        add(new Designation("Host / Hostess", "Handles a single frontline incident with the greeter at the entrance: rude or dismissive greeting, wait-time answers, reservation or seating mistakes.", 1));
        add(new Designation("Waiter / Waitress", "Handles a single frontline incident with the server at the table: ignored guests, slow order taking, missing refills, wrong orders, billing at the table.", 1));
        add(new Designation("Busser / Table Cleaner", "Handles dirty or unreset tables: dishes, syrup or napkins left from previous guests, and tables that were not cleaned before seating.", 1));
        add(new Designation("Cashier / POS Operator", "Handles register and payment errors: overcharging, wrong items billed, voids and refunds taking too long, receipts.", 1));
        add(new Designation("Bartender", "Handles a single drink made wrongly at the bar: cocktails that taste wrong, missing alcohol, wrong garnish, incorrect recipe.", 1));
        add(new Designation("Barista", "Handles a single coffee or tea made wrongly: lukewarm or stale drinks, burnt milk, wrong coffee order.", 1));
        add(new Designation("Cleaner / Housekeeper", "Handles routine cleanliness tasks needing action now: overflowing trash, wet floors, restroom supplies (toilet paper, soap) running out.", 1));
        add(new Designation("Security Guard", "Handles an immediate on-the-spot safety incident: a guard at the entrance or parking area who needs to respond to a disturbance.", 1));
        add(new Designation("Delivery Rider / Driver", "Handles a single delivery handling problem: spilled drinks, damaged packaging, wrong address or a rider's handling of the order.", 1));
        add(new Designation("General Chef / Line Cook", "Handles a single dish prepared wrongly: wrong ingredients (allergens, meat in vegetarian dishes), undercooked, cold or incorrect food.", 1));
    }

    private void add(Designation d) {
        designationMap.put(d.getTitle(), d);
        levelMap.computeIfAbsent(d.getLevel(), k -> new ArrayList<>()).add(d);
    }

    public Designation getByTitle(String title) {
        return designationMap.get(title);
    }

    public List<Designation> getByLevel(int level) {
        return levelMap.getOrDefault(level, Collections.emptyList());
    }

    public Map<Integer, List<Designation>> getAllLevels() {
        return levelMap;
    }
}