# ResolveX Triage

AI-powered complaint triage prototype for a restaurant/hotel branch. A customer complaint is sent to the
**Jev** model from [Typesafe.ai](https://typesafe.ai/) (through the
[Spring AI Typesafe](https://spring-ai-community.github.io/spring-ai-typesafe/latest-snapshot/) library).
Jev decides which staff role should handle it, how urgent it is, and how confident it is. A small
escalation engine then moves the complaint up the staff hierarchy on demand.

> **All routing decisions are made by the Jev AI model.** The earlier local keyword-matching analyzer
> and the hand-written REST call have been removed completely. If the AI service is unreachable, the
> API returns an error (HTTP 502) instead of guessing with keywords.

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
4. `POST /api/v1/complaints/escalate` walks the complaint up the hierarchy using `EscalationEngine`.
   Tenant Admin (Level 5) is only reachable when the complaint is directly against the Branch Manager.

The routing instructions given to Jev: complaints *against* a person go to that person's supervisor
(cleaner -> Cleaning Supervisor, waiter -> Floor Supervisor, security guard -> Security Supervisor,
Level 2 supervisors -> Assistant Branch Manager, Assistant Branch Manager -> Branch Manager,
Branch Manager -> Tenant Admin). Operational issues go to the role that does that work.

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
  "currentRole": "Floor Supervisor",
  "isDirectAgainstBranchManager": false,
  "history": []
}
```

Response: `escalatedTo`, `escalatedLevel`, `escalationHistory`, `isLimitReached`.

## Configuration

`src/main/resources/application.yml`:

| Property                    | Default / value                    | Description                                         |
|-----------------------------|------------------------------------|-----------------------------------------------------|
| `server.port`               | `8080`                             | HTTP port.                                          |
| `spring.ai.typesafe.api-key`| `${TYPESAFE_API_KEY}`              | Typesafe API key, from `.env` or the environment.   |
| `spring.ai.typesafe.model`  | `jev-latest`                       | Jev model name or alias.                            |
| `spring.ai.typesafe.base-url`| `https://api.typesafe.ai` (library default) | API root; normally leave unset.            |
| `spring.ai.typesafe.retry.*`| library defaults (2 retries, backoff) | Retry behaviour for transient failures.          |

The app will not start without an API key (`TYPESAFE_API_KEY`), because the Jev client is only created when a key is configured.

## Improving accuracy

The first version routed only about half of a 19-complaint sample sheet to the expected role (9-10 of 19,
and one answer changed between runs). The model itself was fine; it was being told too little. These
changes were made, in order of impact:

1. **Role descriptions rewritten** (`DesignationRegistry`). Jev picks a role mainly by reading its description,
   and the old ones were generic ("Directly supervises Bartenders and Baristas"). Each is now phrased as what
   the role *handles*, with concrete examples. For example, Bar Supervisor covers glassware and supply
   shortages, Head Waiter covers service skills and training, Branch Manager covers overall quality, menu
   and pricing, and Tenant Admin covers franchise-wide app issues.
2. **One clear routing rule** (the `Choice` instructions in `TypeSafeJevTriageService`). The old prompt said
   "a complaint against a person goes to their supervisor", which pushed one-off frontline mistakes up to
   Floor Supervisor. The rule is now:
   - a one-off mistake by a frontline employee goes to that frontline role;
   - systemic issues (standards, supplies, training, hygiene, safety, dispatch systems) go to the responsible Level 2 supervisor/head;
   - serious misconduct (yelling, shouting, abuse) goes to the person's direct supervisor;
   - complaints about a supervisor, Assistant Branch Manager or Branch Manager go one step up, ending at Tenant Admin.

   **This rule is an interpretation** of the expected results in the sample sheet. If your business rule
   differs, edit that instruction text and re-run the evaluation.
3. **Low-confidence flag and runner-up roles.** The response now has `needsReview` (confidence < 50%) and
   `alternatives` (Jev's next two choices), so uncertain cases go to a person instead of being trusted.
4. **Priority scale fixed.** Jev's scores are 0-3, but the code assumed 1-4, so every complaint came out
   "Low Priority". The cut-offs now match the real scale.
5. **A repeatable accuracy test** (see below), so each change can be measured.

Results (each complaint sent 3 times):

| Set | Before | After |
|-----|--------|-------|
| `eval/sample-complaints.csv` (the 19-row sample sheet) | about 50% (9-10 of 19) | 100% (57/57) |
| `eval/holdout-complaints.csv` (12 new complaints not used while tuning) | not measured | 83% role (30/36), 92% level (33/36) |

The sample-sheet score is optimistic because the descriptions were written while looking at those exact
complaints. **The held-out figure is the realistic one.** Its two misses show the remaining weak spots:
a raw steak was sent to the cook rather than the waiter (arguably a valid answer), and an espresso-machine
complaint got only 39% confidence, so `needsReview` is `true` for it.

Further ideas: add more held-out complaints from real data, split priority into separate severity and
urgency questions, and widen role descriptions wherever the evaluation shows repeated misses.

## Measuring accuracy

`scripts/evaluate.py` sends every complaint in a CSV file to the running app and reports role and level accuracy.
It uses only the Python standard library.

```bash
# 1. start the app in one terminal
./mvnw spring-boot:run

# 2. in another terminal
python scripts/evaluate.py --runs 3                                        # sample sheet
python scripts/evaluate.py --runs 3 --file eval/holdout-complaints.csv     # held-out set
```

CSV columns: `id, complaint, expected_role, expected_level`. Add your own rows to grow the test set.
Each run calls the Jev API (billed per token), so keep `--runs` small. Jev answers can vary slightly between runs.

## Project structure

```
src/main/java/resolvex_triage/
  TriageApplication.java              Spring Boot entry point
  controller/TriageController.java    REST endpoints
  service/TypeSafeJevTriageService.java   Jev AI triage (Typesafe client)
  service/EscalationEngine.java       Hierarchy escalation rules
  repository/DesignationRegistry.java In-memory staff roles
  model/Designation.java              Role model
src/main/resources/
  application.yml                     Configuration
  static/index.html                   Web UI
scripts/evaluate.py                   Accuracy test against the running app
eval/sample-complaints.csv            Sample sheet with expected roles
eval/holdout-complaints.csv           Held-out complaints for honest measurement
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

The context-load test uses a dummy API key and makes no network calls.

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
