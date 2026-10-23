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
        add(new Designation("Tenant Admin", "Handles complaints about the Branch Manager's own conduct, and franchise-wide or technology problems that no single branch can fix: the mobile app, loyalty points, gift cards, online reservation portal, corporate website or email, and system updates. Sinhala terms: ජංගම යෙදුම, වෙබ් අඩවිය, පක්ෂපාතීත්ව ලකුණු, උපහාර කාඩ්පත්, පද්ධතිය.", 5));

        // Level 4
        add(new Designation("Branch Manager", "Handles complaints about the overall quality of the branch over time: atmosphere, menu choices, pricing and portion sizes, understaffing, and complaints sent to management that were never answered. Also handles complaints about the Assistant Branch Manager. Sinhala terms: ශාඛා කළමනාකරු, මිල, මෙනුව, සේවක හිඟය, ඊමේල් එකට පිළිතුරු නැත.", 4));

        // Level 3
        add(new Designation("Assistant Branch Manager", "Handles a manager or shift leader on duty who dismissed, ignored or failed to act on a complaint or on staff misbehaviour (staff arguing, blaming staff, refusing to fix noise or temperature). Also handles complaints about any Level 2 supervisor or the Head Chef. Sinhala terms: සහකාර ශාඛා කළමනාකරු, ෂිෆ්ට් කළමනාකරු.", 3));

        // Level 2
        add(new Designation("Head Waiter", "Handles the conduct of the head waiter, senior waiter or head server (arrogant, annoyed, passive-aggressive, interrupting), and waiter skill or training lapses in service standards: spills left uncleaned, dropped trays, poor wine knowledge, missing basic table-service skills. Sinhala terms: ප්‍රධාන වේටර්, ජ්‍යෙෂ්ඨ වේටර්.", 2));
        add(new Designation("Head Chef / Kitchen Manager", "Handles kitchen-wide problems: long delays or uneven timing getting food out, allergen and cross-contamination failures, food-safety failures such as raw or undercooked meat, and plating or presentation standards. Sinhala terms: ප්‍රධාන සූපවේදියා, කුස්සිය, අසාත්මිකතා.", 2));
        add(new Designation("Floor Supervisor", "Handles management of the dining floor: nobody in charge, tables or servers not assigned, staff idle or running around frantically, and the pacing and coordination of courses (starters and mains arriving together, uneven service flow). Sinhala terms: ආහාර ශාලා අධීක්ෂක, ශාලාව භාරව කිසිවෙකු නැත.", 2));
        add(new Designation("Bar Supervisor", "Handles bar operations problems: bar supplies or ingredients running out, no clean glassware, drink menu items unavailable, dirty beer lines, and an unmanaged or unsanitary bar area. Sinhala terms: බාර් අධීක්ෂක, වීදුරු, අමුද්‍රව්‍ය නැත.", 2));
        add(new Designation("Cleaning Supervisor", "Handles hygiene and safety standards for cleanliness: pests and insects, strong odours, dangerously slippery floors, neglected deep cleaning (gum, grime), and cleaning done at the wrong time. Sinhala terms: පිරිසිදු කිරීමේ අධීක්ෂක, සනීපාරක්ෂාව, කැරපොත්තන්, ලිස්සන බිම.", 2));
        add(new Designation("Security Supervisor / Chief Security Officer", "Handles safety and security systems: lighting, broken cameras, missing patrols or protocols, security ignoring disruptive patrons, and valet or vehicle incidents needing a decision. Sinhala terms: ආරක්ෂක ප්‍රධානී, ආරක්ෂක අධීක්ෂක, කැමරා, පහන්.", 2));
        add(new Designation("Delivery Supervisor", "Handles delivery and dispatch operations: broken order tracking, misleading dispatch information, unhelpful dispatch calls, delivery policies, and refunds for orders that never arrived. Sinhala terms: ඩිලිවරි අධීක්ෂක, ඩිස්පැච්, ට්‍රැකින්.", 2));

        // Level 1
        add(new Designation("Host / Hostess", "Handles the conduct of the host or greeter at the entrance, wait times quoted at the door, lost reservations, and seating problems. Sinhala terms: පිළිගන්නා, සත්කාරක, වෙන්කිරීම, බලා සිටීම.", 1));
        add(new Designation("Waiter / Waitress", "Handles the conduct and mistakes of the server at the table: forgot an order, never returned, ignored guests, wrong dish, rude attitude, slow check. Sinhala terms: වේටර්, සේවකයා, ඇණවුම අමතක කිරීම.", 1));
        add(new Designation("Busser / Table Cleaner", "Handles table clearing and cleaning: dirty or wet tables, clearing plates too early, noisy handling of dishes, and conduct of the busser. Sinhala terms: මේසය පිරිසිදු කරන්නා, පිඟන් ඉවත් කිරීම.", 1));
        add(new Designation("Cashier / POS Operator", "Handles the conduct and mistakes of the cashier or register operator: overcharging, double charges, wrong change or discount, slow voids or gift card processing, rudeness. Sinhala terms: කැෂියර්, ගෙවීම් යන්ත්‍රය, බිල.", 1));
        add(new Designation("Bartender", "Handles the conduct and mistakes of the bartender: drinks made wrongly, wrong order, ignoring guests, poor hygiene (bare hands on ice), attitude. Sinhala terms: බාර්ටෙන්ඩර්, බීම.", 1));
        add(new Designation("Barista", "Handles coffee and tea quality and the conduct of the barista: burnt, lukewarm or flat drinks, wrong milk, dirty or chipped cups and mugs, attitude. Sinhala terms: බාරිස්ටා, කෝපි, කපුචිනෝ.", 1));
        add(new Designation("Cleaner / Housekeeper", "Handles routine upkeep tasks and conduct of the cleaner: restroom supplies (toilet paper, soap), overflowing bins, water on restroom floors, dirty mirrors, blocking walkways. Sinhala terms: පිරිසිදු කරන්නා, වැසිකිළිය, කුණු බඳුන්.", 1));
        add(new Designation("Security Guard", "Handles the conduct of the security guard: rude, intimidating or aggressive, physical contact, asleep or distracted by a phone, missing ID checks, letting in the wrong people. Sinhala terms: ආරක්ෂකයා, ආරක්ෂක නිලධාරියා.", 1));
        add(new Designation("Delivery Rider / Driver", "Handles the conduct and handling of the delivery rider: wrong address, squashed or damaged food, rudeness, demanding a tip, bad smell, leaving food outside. Sinhala terms: ඩිලිවරි රයිඩර්, රියදුරු.", 1));
        add(new Designation("General Chef / Line Cook", "Handles the quality of a single dish and the conduct of the line cook: bland, salty, soggy, cold or burnt food, wrong ingredients, foreign objects such as hair, too much dressing. Sinhala terms: සූපවේදියා, කුස්සියේ සේවකයා, කෑමේ රසය.", 1));
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