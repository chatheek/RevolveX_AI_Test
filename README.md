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
  "escalationHistory": [
    { "step": 1, "action": "Initial AI Triage Assignment", "assignedTo": "Floor Supervisor", "level": 2, "timestamp": "..." }
  ]
}
```

- `confidence` is Jev's calibrated confidence (0-100) for the chosen role. The UI flags values under 50% as untrustworthy.
- `priority` is one of Low / Medium / High / Critical Priority.
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
meaning of each routing option, so editing them changes routing behaviour.

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
