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
        add(new Designation("Tenant Admin", "Receives complaints about the conduct of the Branch Manager or general manager, and franchise-wide or technology problems that no single branch can fix: the mobile app, loyalty points, gift cards, online reservation portal, corporate website or email, and system updates. Sinhala terms: ජංගම යෙදුම, වෙබ් අඩවිය, පක්ෂපාතීත්ව ලකුණු, උපහාර කාඩ්පත්, පද්ධතිය, ශාඛා කළමනාකරු/සාමාන්‍ය කළමනාකරු ගැන පැමිණිලි.", 5));

        // Level 4
        add(new Designation("Branch Manager", "Receives complaints about the conduct of the Assistant Branch Manager, shift manager, duty manager or shift leader (rude, dismissive, shouted, lied, refused a refund, ignored a complaint). Also handles branch-wide problems: overall quality over time, menu, prices and portions, understaffing, decor, opening hours, social media, and unanswered messages to management. Sinhala terms: ශාඛා කළමනාකරු, මිල, මෙනුව, සේවක හිඟය, ඊමේල් එකට පිළිතුරු නැත, සහකාර කළමනාකරු/ෂිෆ්ට් කළමනාකරු ගැන පැමිණිලි.", 4));

        // Level 3
        add(new Designation("Assistant Branch Manager", "Receives complaints about the conduct of any Level 2 supervisor or head: the head waiter or senior waiter, the Floor, Bar, Cleaning, Security and Delivery Supervisors, and the Head Chef (rude, arrogant, shouted, ignored a complaint, refused to help, drunk on duty). Sinhala terms: සහකාර ශාඛා කළමනාකරු, අධීක්ෂක, ප්‍රධාන වේටර්, ප්‍රධාන සූපවේදියා ගැන පැමිණිලි.", 3));

        // Level 2
        add(new Designation("Head Waiter", "Receives complaints about the conduct of waiters and waitresses (rude, attitude, condescending, rolled eyes, ignored guests, lied, unhygienic habits, walked away after an accident). Also handles mistakes and service-skill lapses of the head or senior waiter (wrong advice, poor wine knowledge, dropped tray) and training problems in the dining room. Conduct complaints about the head waiter or senior waiter (rude, arrogant, intoxicated, interrupting) go to the Assistant Branch Manager, not here. Sinhala terms: ප්‍රධාන වේටර්, ජ්‍යෙෂ්ඨ වේටර්, වේටර් ගැන පැමිණිලි.", 2));
        add(new Designation("Head Chef / Kitchen Manager", "Handles kitchen-wide problems: long delays or uneven timing getting food out, running out of ingredients, allergen and food-safety standards, quality control (mould), kitchen noise and equipment. Receives complaints about the conduct of line cooks and other kitchen staff (shouting, rude, unprofessional). Conduct complaints about the Head Chef go to the Assistant Branch Manager. Sinhala terms: ප්‍රධාන සූපවේදියා, කුස්සිය, අසාත්මිකතා.", 2));
        add(new Designation("Floor Supervisor", "Handles floor-wide problems: nobody in charge of the dining room, tables or servers not assigned, staff idle or running around, pacing of courses, cramped layout. Receives complaints about the conduct of hosts, bussers and cashiers (rude, rolled eyes, ignored guests, argued, accused customers, lied, slammed dishes, unhygienic habits). Conduct complaints about waiters go to the Head Waiter. Conduct complaints about the floor supervisor go to the Assistant Branch Manager. Sinhala terms: ආහාර ශාලා අධීක්ෂක, ශාලාව භාරව කිසිවෙකු නැත, සත්කාරක/කැෂියර්/මේසය පිරිසිදු කරන්නා ගැන පැමිණිලි.", 2));
        add(new Designation("Bar Supervisor", "Handles bar-wide problems: supplies, glassware, ingredients, equipment, dirty beer lines, menu items unavailable, serving intoxicated guests. Receives complaints about the conduct of bartenders and baristas (ignored guests, rude, mocked, argued, unprofessional, slammed drinks). Conduct complaints about the bar supervisor go to the Assistant Branch Manager. Sinhala terms: බාර් අධීක්ෂක, වීදුරු, අමුද්‍රව්‍ය නැත, බාර්ටෙන්ඩර්/බාරිස්ටා ගැන පැමිණිලි.", 2));
        add(new Designation("Cleaning Supervisor", "Handles hygiene and safety standards: pests, insects, odours, mildew, dangerous floors, neglected deep cleaning, dust, sanitisation. Receives complaints about the conduct of cleaners and housekeepers (aggressive, rude, resting on duty, loud). Conduct complaints about the cleaning supervisor go to the Assistant Branch Manager. Sinhala terms: පිරිසිදු කිරීමේ අධීක්ෂක, සනීපාරක්ෂාව, කැරපොත්තන්, ලිස්සන බිම, පිරිසිදු කරන්නා ගැන පැමිණිලි.", 2));
        add(new Designation("Security Supervisor / Chief Security Officer", "Handles security systems and protocols: lighting, cameras, patrols, chained exits, bag checks, unsecured areas, guards not responding. Receives complaints about the conduct of security guards and bouncers (rude, aggressive, pushed, profiled, asleep, on a phone, smoking, staring, swearing, unfair). Conduct complaints about the security supervisor or chief go to the Assistant Branch Manager. Sinhala terms: ආරක්ෂක ප්‍රධානී, ආරක්ෂක අධීක්ෂක, කැමරා, පහන්, ආරක්ෂකයා ගැන පැමිණිලි.", 2));
        add(new Designation("Delivery Supervisor", "Handles delivery and dispatch operations: broken tracking, misleading dispatch information, delivery times, policies, packaging, refunds for undelivered orders. Receives complaints about the conduct of delivery riders and drivers (rude, abusive, demanded a tip, refused to climb stairs, ate food, loud music, smells). Conduct complaints about the delivery supervisor go to the Assistant Branch Manager. Sinhala terms: ඩිලිවරි අධීක්ෂක, ඩිස්පැච්, ට්‍රැකින්, රයිඩර් ගැන පැමිණිලි.", 2));

        // Level 1
        add(new Designation("Host / Hostess", "Handles mistakes in the host's work: lost or wrong reservations, wrong wait times, poor seating choices, untidy host stand. Conduct complaints about the host (rude, ignored guests, rolled eyes) go to the Floor Supervisor. Sinhala terms: පිළිගන්නා, සත්කාරක, වෙන්කිරීම, බලා සිටීම.", 1));
        add(new Designation("Waiter / Waitress", "Handles mistakes in the server's work: forgot an order or drink, wrong dish, wrong check, never came back, dropped something. Conduct complaints about the waiter (rude, attitude, condescending, unhygienic) go to the Head Waiter. Sinhala terms: වේටර්, සේවකයා, ඇණවුම අමතක කිරීම.", 1));
        add(new Designation("Busser / Table Cleaner", "Handles mistakes in table clearing and cleaning: dirty or wet tables, leftovers, clearing too early, broken glass left behind, dirty cloth. Conduct complaints about the busser go to the Floor Supervisor. Sinhala terms: මේසය පිරිසිදු කරන්නා, පිඟන් ඉවත් කිරීම.", 1));
        add(new Designation("Cashier / POS Operator", "Handles mistakes at the register: overcharging, double charges, wrong change or discount, slow voids or gift card processing, receipts. Conduct complaints about the cashier go to the Floor Supervisor. Sinhala terms: කැෂියර්, ගෙවීම් යන්ත්‍රය, බිල.", 1));
        add(new Designation("Bartender", "Handles mistakes in drinks: wrong or weak drink, too much ice, stale garnish, unclean glass, wrong or forgotten order, bare hands on ice. Conduct complaints about the bartender go to the Bar Supervisor. Sinhala terms: බාර්ටෙන්ඩර්, බීම.", 1));
        add(new Designation("Barista", "Handles mistakes in coffee and tea: burnt milk, lukewarm drinks, wrong milk, ignored request, dirty cup or machine, wrong name. Conduct complaints about the barista go to the Bar Supervisor. Sinhala terms: බාරිස්ටා, කෝපි, කපුචිනෝ.", 1));
        add(new Designation("Cleaner / Housekeeper", "Handles routine cleaning tasks that need action now: restroom supplies, overflowing bins, wet floors, dirty mirrors, smells, locked restrooms. Conduct complaints about the cleaner go to the Cleaning Supervisor. Sinhala terms: පිරිසිදු කරන්නා, වැසිකිළිය, කුණු බඳුන්.", 1));
        add(new Designation("Security Guard", "Handles mistakes in a guard's checks: missing ID or ticket check, letting in underage guests. Conduct complaints about the guard go to the Security Supervisor. Sinhala terms: ආරක්ෂකයා, ආරක්ෂක නිලධාරියා.", 1));
        add(new Designation("Delivery Rider / Driver", "Handles mistakes in delivery handling: wrong address or building, squashed or cold food, no thermal bag, left outside, late. Conduct complaints about the rider go to the Delivery Supervisor. Sinhala terms: ඩිලිවරි රයිඩර්, රියදුරු.", 1));
        add(new Designation("General Chef / Line Cook", "Handles mistakes and quality of a dish: raw, burnt, cold, bland, salty, soggy, wrong ingredient, hair or foreign object, ignored modification, bad plating. Conduct complaints about cooks go to the Head Chef. Sinhala terms: සූපවේදියා, කුස්සියේ සේවකයා, කෑමේ රසය.", 1));
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