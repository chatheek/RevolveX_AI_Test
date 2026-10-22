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
        add(new Designation("Tenant Admin", "System Super Admin / Tenant Admin managing entire tenant infrastructure.", 5));

        // Level 4
        add(new Designation("Branch Manager", "Responsible for overall branch operations, financial performance, staff management, and ultimate resolution of escalated customer complaints.", 4));

        // Level 3
        add(new Designation("Assistant Branch Manager", "Directly supervises shift operations, manager-level escalations, and administrative tasks.", 3));

        // Level 2
        add(new Designation("Head Waiter", "Senior service staff member responsible for assigned dining sections, guiding waiters, and handling complex customer table service requests.", 2));
        add(new Designation("Head Chef / Kitchen Manager", "Leads overall kitchen operations, manages kitchen staff, oversees food quality, food safety standards, and kitchen inventory.", 2));
        add(new Designation("Floor Supervisor", "Directly supervises Head Waiters, Waiters, Bussers, Cashiers, Hosts, and Valets.", 2));
        add(new Designation("Bar Supervisor", "Directly supervises Bartenders and Baristas.", 2));
        add(new Designation("Cleaning Supervisor", "Directly supervises Cleaners, Janitors, and Restroom Attendants.", 2));
        add(new Designation("Security Supervisor / Chief Security Officer", "Directly supervises Security Guards and Parking Attendants.", 2));
        add(new Designation("Delivery Supervisor", "Oversees delivery operations, manages order dispatching, coordinates delivery riders/drivers, tracks delivery timelines, and handles customer issues or delays related to takeaway and delivery orders.", 2));

        // Level 1
        add(new Designation("Host / Hostess", "Greets arriving guests, manages table reservations, handles floor seating arrangements, and serves as the initial point of contact at the branch entrance.", 1));
        add(new Designation("Waiter / Waitress", "Takes customer orders, serves food and beverages, handles table billing, and directly attends to customer requests throughout their meal.", 1));
        add(new Designation("Busser / Table Cleaner", "Clears used dishes, cleans and sanitizes dining tables, resets seating areas, and supports waiters to maintain floor efficiency.", 1));
        add(new Designation("Cashier / POS Operator", "Processes customer payments, manages checkout billing, issues receipts, and handles cash register balancing at the front counter.", 1));
        add(new Designation("Bartender", "Prepares and serves alcoholic and non-alcoholic beverages, manages bar inventory, and maintains cleanliness in the bar service area.", 1));
        add(new Designation("Barista", "Specializes in preparing and serving coffee, tea, and specialized cafe beverages according to established branch standards.", 1));
        add(new Designation("Cleaner / Housekeeper", "Maintains cleanliness across public dining areas, restrooms, corridors, and staff quarters throughout operational hours.", 1));
        add(new Designation("Security Guard", "Ensures safety and security for customers, staff, and branch property, managing parking flow and handling disruptive incidents.", 1));
        add(new Designation("Delivery Rider / Driver", "Transports food orders from the branch directly to customers for takeaway and delivery orders while ensuring timely transit.", 1));
        add(new Designation("General Chef / Line Cook", "Prepares and cooks menu items at kitchen according to standardized recipes, ensuring food quality, speed, and strict hygiene standards.", 1));
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