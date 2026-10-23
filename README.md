# ResolveX Triage

AI-powered complaint triage prototype for a restaurant/hotel branch. A customer complaint is sent to the
**Jev** model from [Typesafe.ai](https://typesafe.ai/) (through the
[Spring AI Typesafe](https://spring-ai-community.github.io/spring-ai-typesafe/latest-snapshot/) library).
Jev decides which staff role should handle it, how urgent it is, and how confident it is. When the
**Escalate** button is pressed, Jev also decides which higher role should take the complaint next.

> **All routing and escalation decisions are made by the Jev AI model.** The earlier local keyword-matching
> analyzer, the fixed escalation ladder and the hand-written REST call have been removed. If the AI service is
> unreachable, the API returns an error (HTTP 502) instead of guessing.

## Contents

- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Setup](#setup)
- [Running](#running)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Project structure](#project-structure)
- [Staff hierarchy](#staff-hierarchy)
- [Improving accuracy](#improving-accuracy)
- [Measuring accuracy](#measuring-accuracy)
- [Testing](#testing)
- [Security notes](#security-notes)
- [Troubleshooting](#troubleshooting)

## How it works

1. `POST /api/v1/complaints/triage` receives `{ "feedback": "..." }`.
2. `TypeSafeJevTriageService` sends the text to Jev via `TypeSafeClient.systemOne(...)` with three typed questions:

   | Question ID              | Type     | Purpose                                                                 |
   |--------------------------|----------|-------------------------------------------------------------------------|
   | `assigned_route`         | `Choice` | Pick the best role. Options are built from the staff registry (title + description). |
   | `priority_score`         | `Score`  | 4 levels: Low, Medium, High, Critical.                                  |
   | `against_branch_manager` | `Noul`   | 0-1 probability that the complaint targets the Branch Manager personally. |

3. The service converts Jev's answers into the triage result (role, level, priority, confidence %).
4. `POST /api/v1/complaints/escalate` asks Jev who should take the complaint next (`EscalationEngine`). Jev gets the
   complaint text, the role it is currently with and the roles it has already been with, and answers one `Choice`
   question (`escalate_to`). Hierarchy guardrails decide *which options Jev may choose from*, so the AI can never move
   a complaint down or pick a nonsensical role:
   - only roles **above** the current level are offered;
   - **Tenant Admin** is offered only when the complaint is about the Branch Manager (`isDirectAgainstBranchManager`);
   - if there is **no** higher role, the limit is reached and Jev is not called;
   - if there is **only one** allowed role (e.g. Assistant Branch Manager -> Branch Manager), it is used without calling Jev.

   Jev is told to prefer the lowest-level role that owns the problem (for example the Bar Supervisor for a bartender
   complaint) and to skip straight to a manager only for serious misconduct, safety or legal risk, an unresolved
   complaint, or a complaint about the current role's own supervisor.

The routing instruction given to Jev ("who should respond first"): a complaint about the behaviour or mistake of a
staff role goes to that role; systemic issues go to the Level 2 supervisor who owns them; a manager who failed
to act goes to Assistant Branch Manager; complaints about a supervisor go one level up, ending at Tenant Admin.
See [Improving accuracy](#improving-accuracy).

## Requirements

| Tool  | Version | Notes                                                                        |
|-------|---------|------------------------------------------------------------------------------|
| Java  | **17 or newer** | Java 11 will **not** work (Spring Boot 4 and the Typesafe SDK need 17+). |
| Maven | 3.9+ (optional) | The included Maven wrapper (`mvnw` / `mvnw.cmd`) downloads Maven for you. |
| Typesafe API key | - | Create one at <https://console.typesafe.ai/keys>.                         |

Check your Java version:

```bash
java -version
```

If you have both Java 11 and 17 installed, make sure `JAVA_HOME` points at 17 for this project.

PowerShell (current terminal only):

```powershell
$env:JAVA_HOME = "C:\Users\MSI\.jdks\jdk-17.0.20.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Stack: Spring Boot 4.0.7, Spring AI Typesafe starter 0.4.0 (`org.springaicommunity:spring-ai-starter-typesafe`), Lombok.

## Setup

1. Clone the repository.
2. Create your local secrets file from the template:

   ```bash
   cp .env.example .env
   ```

   Windows PowerShell: `Copy-Item .env.example .env`

3. Open `.env` and set your key:

   ```properties
   TYPESAFE_API_KEY=your-typesafe-api-key-here
   ```

   The file is read automatically on startup (`spring.config.import: optional:file:.env[.properties]`
   in `application.yml`). It must be in the directory you start the app from (the project root).
   You can alternatively set a real environment variable named `TYPESAFE_API_KEY`, which works the same way.

`.env` is listed in `.gitignore`, so it is never committed. `.env.example` is the only env file that is tracked, and it contains no secret.

## Running

From the project root.

Linux / macOS / Git Bash:

```bash
./mvnw spring-boot:run
```

Windows (PowerShell / cmd):

```powershell
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080> for the web UI.

Build a runnable jar instead:

```bash
./mvnw clean package
java -jar target/triage-0.0.1-SNAPSHOT.jar
```

(Run the jar from the project root so `.env` is found, or set `TYPESAFE_API_KEY` as an environment variable.)

## API reference

Base path: `/api/v1`

### `GET /hierarchy`

Returns all staff designations grouped by level (1-5).

### `POST /complaints/triage`

Request:

```json
{ "feedback": "The waiter yelled at us and the soup was cold." }
```

Response:

```json
{
  "routedTo": "Floor Supervisor",
  "routedLevel": 2,
  "priority": "High Priority",
  "confidence": 93.4,
  "isDirectAgainstBranchManager": false,
  "needsReview": false,
  "alternatives": [
    { "role": "Waiter / Waitress", "probability": 3.0 },
    { "role": "Head Waiter", "probability": 1.0 }
  ],
  "escalationHistory": [
    { "step": 1, "action": "Initial AI Triage Assignment", "assignedTo": "Floor Supervisor", "level": 2, "timestamp": "..." }
  ]
}
```

- `confidence` is Jev's calibrated confidence (0-100) for the chosen role. The UI flags values under 50% as untrustworthy.
- `priority` is one of Low / Medium / High / Critical Priority. Jev's urgency score is 0-based
  (0 = Low ... 3 = Critical); the cut-offs are in `mapScoreToPriority`: Critical >= 2.5, High >= 1.7, Medium >= 0.8.
- `needsReview` is `true` when confidence is below 50%. Treat those results as suggestions for a human to confirm.
- `alternatives` lists the next two most likely roles according to Jev with their probability (%), useful when `needsReview` is true.
- Errors: `400` for empty feedback, `502` if the Jev service call fails (bad key, network, rate limit).

Example:

```bash
curl -X POST http://localhost:8080/api/v1/complaints/triage \
  -H "Content-Type: application/json" \
  -d '{"feedback":"The branch manager shouted at a customer"}'
```

### `POST /complaints/escalate`

Request:

```json
{
  "feedback": "The bartender ignored me for 20 minutes and was rude.",
  "currentRole": "Bartender",
  "isDirectAgainstBranchManager": false,
  "history": [ { "step": 1, "assignedTo": "Bartender", "level": 1 } ]
}
```

`feedback` is the original complaint text (the web page sends it automatically) and is required whenever Jev has
to choose. `history` is the `escalationHistory` returned by the previous call.

Response:

```json
{
  "escalatedTo": "Bar Supervisor",
  "escalatedLevel": 2,
  "isLimitReached": false,
  "decidedBy": "Jev AI",
  "confidence": 88.0,
  "needsReview": false,
  "alternatives": [ { "role": "Assistant Branch Manager", "probability": 11.0 } ],
  "escalationHistory": [ "...previous steps...", { "step": 2, "action": "AI Escalation (Jev, 88.0% confidence)", "assignedTo": "Bar Supervisor", "level": 2 } ]
}
```

- `decidedBy` is `Jev AI` when Jev picked among several roles, or `Hierarchy rule` when there was only one allowed
  role or the limit was reached (`confidence` is then `null`).
- `needsReview` is `true` when Jev's confidence is under 50%.
- Errors: `400` for an unknown role or missing `feedback`, `502` if the Jev call fails or returns a role that is not allowed.

Typical live results: Bartender -> Bar Supervisor -> Assistant Branch Manager -> Branch Manager; a complaint about the
Branch Manager is triaged straight to Tenant Admin and stays there.

## Configuration

`src/main/resources/application.yml`:

| Property                    | Default / value                    | Description                                         |
|-----------------------------|------------------------------------|-----------------------------------------------------|
| `server.port`               | `8080`                             | HTTP port.                                          |
| `spring.ai.typesafe.api-key`| `${TYPESAFE_API_KEY}`              | Typesafe API key, from `.env` or the environment.   |
| `spring.ai.typesafe.model`  | `jev-latest`                       | Jev model name or alias.                            |
| `spring.ai.typesafe.base-url`| `https://api.typesafe.ai` (library default) | API root; normally leave unset.            |
| `spring.ai.typesafe.retry.*`| library defaults (2 retries, backoff) | Retry behaviour for transient failures.          |
| `spring.web.error.include-message` | `always`                    | Shows the error text in API error responses (Spring Boot 4 name; it was `server.error.*` in Boot 3). |

The app will not start without an API key (`TYPESAFE_API_KEY`), because the Jev client is only created when a key is configured.

## Improving accuracy

Two rounds of improvement have been made. All of it is configuration of what Jev is told; the code
path is unchanged. The model is called with typed questions (`Choice`, `Score`, `Noul`) and the quality
of the answers depends mainly on the role descriptions and the routing instruction.

### Round 1 - from a 19-row sample sheet

The first version routed only about half of a 19-complaint sample sheet to the expected role. Changes:

1. Role descriptions were rewritten to say what each role *handles*, with examples (`DesignationRegistry`).
2. One clear routing instruction replaced the vague "go to the supervisor" prompt.
3. `needsReview` (confidence under 50%) and `alternatives` (Jev's next two choices) were added to the response.
4. The priority scale was fixed: Jev's scores are 0-3, but the code assumed 1-4, so everything showed "Low".
5. A repeatable accuracy test (`scripts/evaluate.py`) was added.

### Round 2 - from a 100-complaint test file

Testing on `100_restaurant_complaints_test_data.csv` scored **74%** role accuracy. The errors came in clear groups:
security guards, head waiters, kitchen-wide problems, managers who failed to act, and the pacing of courses.
Changes:

1. **Routing rule changed to "who should respond first".** Round 1 sent staff misconduct to the supervisor.
   The 100-row data expects the frontline role itself (a rude guard goes to Security Guard, a rude
   waitress to Waiter / Waitress). The new rule is:
   - a complaint about the behaviour or mistake of a staff role goes to that role; escalation moves it up later;
   - systemic issues (supplies, equipment, training, hygiene, safety systems, dispatch systems, whole-kitchen or
     whole-floor timing, policies) go to the Level 2 supervisor or head who owns it;
   - a manager or shift leader on duty who failed to act goes to Assistant Branch Manager;
   - complaints about a supervisor go one step up, ending at Tenant Admin.
2. **Role descriptions rewritten again**, e.g. Head Waiter now covers "head/senior waiter conduct and service-skill
   lapses", Head Chef covers allergen failures, food safety, plating and kitchen delays, Floor Supervisor covers
   table assignment and course pacing, Branch Manager covers unanswered complaints and staffing.

> **Rule change to be aware of.** The original prototype said "cleaner yelled -> Cleaning Supervisor". The 100-row
> test data says the opposite (frontline conduct goes to the frontline role). Round 2 follows the 100-row data.
> If the business really wants misconduct routed to supervisors, change instruction 1 in
> `TypeSafeJevTriageService` and the labels in the test CSVs.

### Results

| Test set | Before round 2 | After round 2 |
|----------|----------------|---------------|
| `eval/fresh-complaints.csv` - 34 new complaints written *before* the change (unbiased) | 79.4% (54/68) | **97.1%** (66/68) |
| `eval/restaurant-100-complaints.csv` - the 100-row file | 74% | **92-93%** |
| `eval/restaurant-250-complaints.csv` - 250 complaints not used for tuning (final check) | - | **77.6%** role (194/250), 82.4% level |
| `eval/sample-complaints.csv` - the original 19 rows | 100% | 100% |
| `eval/holdout-complaints.csv` - 12 older test rows (5 labels re-aligned to the 100-row convention) | 83% | 83% |

How to read this:

- **The 250-row result (77.6%) is the realistic estimate.** None of those complaints were used while tuning and nothing was
  changed after seeing them. The higher scores above were measured on data that influenced the changes.
  On the 250 rows, results with 80%+ confidence were right 86% of the time, and the under-50% review flag caught
  27% of the errors, so it should not be the only safeguard.
- The biggest source of 250-row misses is a business rule, not the model: when a complaint is *about* a supervisor or head
  (e.g. "the supervisor hung up on me"), the test data expects that same supervisor, while the app sends it up to the
  Assistant Branch Manager. Complaints that only say "they" also cannot tell Jev that a head waiter or security
  head was involved. Decide the rule first; capturing the staff member at intake would help more than more prompt tuning.

- The **fresh set** is the most trustworthy number. It was written and scored before round 2, then re-scored after.
  It is not perfectly independent, because the types of mistake were known by then.
- The **100-row** score is optimistic because round 2 was designed after seeing which kinds of complaint failed there.
  The rules are written as general patterns, not per-row patches, but treat 92-93% as an upper estimate.
- Runs vary by a point or two because Jev's answers are not perfectly repeatable.
- The remaining misses are mostly contradictory or ambiguous expected results. For example, the test file sends a
  complaint *about* the Delivery Supervisor to the Delivery Supervisor, whereas the hierarchy sends complaints about a
  supervisor up to the Assistant Branch Manager. Review these with the business before tuning further, to avoid
  memorising the test data.
- Results under 50% confidence are flagged `needsReview`. With the improved prompts only about 1 in 100 results is
  flagged, and results with 80%+ confidence were right 97% of the time.

A full Excel report of the 100-complaint test (expected vs actual, charts, per-role and per-level accuracy,
confidence analysis, failed cases, before vs after) was generated as `ResolveX_Test_Report_v2_100_Complaints.xlsx`.

Further ideas: write more fresh complaints from real data, separate severity and urgency in the priority question,
and agree the correct answer for the ambiguous cases with the business.

## Measuring accuracy

`scripts/evaluate.py` sends every complaint in a CSV file to the running app and reports role and level accuracy.
It uses only the Python standard library.

```bash
# 1. start the app in one terminal
./mvnw spring-boot:run

# 2. in another terminal
python scripts/evaluate.py --runs 3                                              # original 19-row sample
python scripts/evaluate.py --runs 1 --file eval/restaurant-100-complaints.csv    # 100-row file
python scripts/evaluate.py --runs 1 --file eval/restaurant-250-complaints.csv    # 250-row file (final check)
python scripts/evaluate.py --runs 2 --file eval/fresh-complaints.csv             # unbiased check
python scripts/evaluate.py --runs 2 --file eval/holdout-complaints.csv           # older held-out set
```

CSV columns: `id, complaint, expected_role, expected_level`. Add your own rows to grow the test set.
Each run calls the Jev API (billed per token), so keep `--runs` small. Jev answers can vary slightly between runs.

## Project structure

```
src/main/java/resolvex_triage/
  TriageApplication.java              Spring Boot entry point
  controller/TriageController.java    REST endpoints
  service/TypeSafeJevTriageService.java   Jev AI triage (Typesafe client)
  service/EscalationEngine.java       AI escalation (Jev) with hierarchy guardrails
  repository/DesignationRegistry.java In-memory staff roles
  model/Designation.java              Role model
src/main/resources/
  application.yml                     Configuration
  static/index.html                   Web UI
scripts/evaluate.py                   Accuracy test against the running app
eval/sample-complaints.csv            Sample sheet with expected roles
eval/holdout-complaints.csv           Held-out complaints for honest measurement
eval/restaurant-100-complaints.csv    100-row test file with expected roles
eval/restaurant-250-complaints.csv    250-row test file with expected roles
eval/fresh-complaints.csv             34 new complaints for an unbiased check
.env.example                          Template for secrets (tracked)
.env                                  Your real secrets (git-ignored)
```

## Staff hierarchy

| Level | Roles |
|-------|-------|
| 5 | Tenant Admin |
| 4 | Branch Manager |
| 3 | Assistant Branch Manager |
| 2 | Head Waiter, Head Chef / Kitchen Manager, Floor Supervisor, Bar Supervisor, Cleaning Supervisor, Security Supervisor / Chief Security Officer, Delivery Supervisor |
| 1 | Host / Hostess, Waiter / Waitress, Busser / Table Cleaner, Cashier / POS Operator, Bartender, Barista, Cleaner / Housekeeper, Security Guard, Delivery Rider / Driver, General Chef / Line Cook |

Roles and their descriptions live in `DesignationRegistry`; the descriptions are sent to Jev as the
meaning of each routing option, so editing them changes routing behaviour. Re-run the evaluation after any edit.

## Testing

```bash
./mvnw test
```

The tests use a dummy API key or a mocked Jev client and make no network calls. `EscalationEngineTest` checks the
escalation guardrails: the ceiling at Tenant Admin and Branch Manager, no AI call when only one target exists,
that Jev is only offered roles above the current level, that Tenant Admin is offered only for Branch Manager
complaints, and that a role Jev returns outside the allowed list is rejected.

## Security notes

- Never commit `.env` or paste the key into source files, issues or chat logs.
- An earlier version of this project hard-coded an API key (`xpl_...`) in `TypeSafeJevTriageService.java`
  and `application.yml`. It remains in the git history. If that key is still active, revoke it in the
  provider console.
- If your current key is ever exposed, rotate it at <https://console.typesafe.ai/keys> and update `.env`.
- `@CrossOrigin(origins = "*")` on the controller is fine for a prototype but should be restricted before any real deployment.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `UnsupportedClassVersionError` / "release version 17 not supported" | You are running Java 11. Point `JAVA_HOME` at a JDK 17+. |
| App fails with "No qualifying bean of type TypeSafeClient" or an unresolved `TYPESAFE_API_KEY` placeholder | `.env` is missing, in the wrong folder, or the key is empty. Start the app from the project root. |
| HTTP 502 "Jev AI service call failed" with an authentication error | The key is wrong, revoked or has stray spaces/line breaks. Re-copy it into `.env` on a single line. |
| 502 with a rate-limit or timeout error | Retry later; retries are configurable under `spring.ai.typesafe.retry`. |
