# PHP App Migration Spec — Template

One of these per app in the portfolio. This is the consolidated, human-readable version of what
Phases 0–2 of `php-to-java-migration` would otherwise scatter across `MIGRATION_ASSESSMENT.md`,
`RULEBOOK.md`, `GAP_INVENTORY.md`, and `WORKFLOW_MAPPINGS.md` — those files can still exist
underneath (Phase 7's fan-out loop reads `RULEBOOK.md` directly), but this doc is what a human
reads to understand *this specific app's* migration, and it's the artifact that should exist
before Phase 3 (stress-test) starts.

Fill every section — write "Not yet assessed" rather than deleting a section, so it's visible
what's still open versus deliberately skipped.

---

## 1. App identity

- **Name / repo**:
- **Business owner / team**:
- **Business-impact tier** (drives priority — see Dogma classification if available):
- **Current deploy target** (on-prem, EC2, container platform, etc.):

## 2. Source framework & version

- **Framework**: (Laravel / Symfony / CodeIgniter / plain PHP — from `php-to-java-migration`'s
  detection table)
- **Version**:
- **PHP version constraint**:
- **ORM in use**: (Eloquent / Doctrine / none — from the `php-model-discovery` manifest's
  `framework` field per model)

## 3. Target configuration

- **Java version**:
- **Spring Boot version**:
- **Migration strategy**: Rewrite / Rehost / Refactor / Replace (per `php-to-java-migration`'s
  four modernization paths — this skill only supports Rewrite; note if another path was chosen
  instead and why this skill doesn't apply)
- **Structure**: structure-preserving / redesign
- **Cutover mechanism**: big-bang / strangler fig, and why

## 4. Codebase inventory (Phase 0)

- **Files / LOC**:
- **Extension dependencies** (`ext-*` in `composer.json`):
- **Explicit dependency graph**: link to the generated graph
- **Implicit couplings found** (globals, sessions, shared tables, side-effecting bootstrap):
  list each one and whether it's being preserved or deliberately broken

## 5. Model/entity inventory

Run `php-model-discovery` against this app and summarize here — don't duplicate the manifest,
link to it.

- **Manifest path**:
- **Models/entities found**: (count, split by `framework`)
- **Flagged for review**: (count) — list the *types* of flags seen (e.g. "12 accessor/mutator
  methods needing manual port", "3 low-confidence base-class resolutions")
- **Unresolved base classes**: (count, and whether any need a `baseClasses` config addition)

## 6. Gap inventory (app-specific)

Every construct here needs to be refactored into an explicit contract before translation, never
translated line-by-line (per Phase 2b's rule). Start from the skill's generic gap-inventory table
(magic methods, `$$`, `eval`, `mixed`, duck typing) and add anything this app surfaces that the
generic table doesn't cover.

| Construct | Location | Why it can't translate directly | Refactor target | Complexity |
|---|---|---|---|---|
| | | | | simple / moderate / complex |

## 7. Complex workflow mappings needed

Check off which of the skill's generic table entries (queues, scheduled tasks, events, caching,
file storage, email, WebSocket, PDF gen, search, rate limiting, sessions, background workers) this
app actually uses, and add any this app needs that the generic table doesn't have — **that
addition belongs in the skill itself, not just here** (see §10).

| Pattern | Present in this app? | Generic mapping applies? | Notes |
|---|---|---|---|

## 8. Rulebook deltas

App-specific rules layered on top of the skill's generic type-map / semantic-trap / Composer-to-
Maven seed tables. Most apps need at least a few — a generic rulebook can't know this app's
specific cast conventions or library choices.

## 9. Parity harness plan (Phase 1 — do this before Phase 3, not after)

- **Test corpus source**: real production traffic sample / hand-authored scenarios / none yet
- **Coverage**: which routes/workflows the harness actually exercises
- **Judge validation status**: has it been run against deliberately broken PHP to confirm it
  actually catches breakage? (if "no", Phase 1 is not actually done — don't mark it done anyway)

## 10. Skill feedback log

Every time this app surfaces a pattern the generic skill didn't already capture, record it here
**and** apply the actual edit to the skill file (`php-to-java-migration/SKILL.md` or
`php-model-discovery`'s extraction logic/config), then link the change here. This is what keeps
the skill improving across the portfolio instead of each app silently reinventing the same fix.

| Date | Pattern found | Skill file changed | What changed |
|---|---|---|---|

## 11. Status / phase tracker

| Phase | Status | Notes |
|---|---|---|
| 0 — Assess & discover | not started / in progress / done | |
| 1 — Build the judge | | |
| 2 — Rulebook + gap inventory | | |
| 3 — Stress-test | | |
| 4 — Scaffold | | |
| 5 — Data model | | |
| 6 — I/O layer | | |
| 7 — Business logic | | |
| 8 — Compile/run/parity/cutover | | |

## 12. Open questions / decisions log

Running log, newest first. Every ambiguous policy decision made with a stakeholder goes here with
the date and who decided — this is what Phase 2's "chat with the user to form an explicit policy"
instruction produces a record of.
