# BookStack — PHP App Migration Spec

Filled from `.kiro/skills/php-to-java-migration/references/app-spec-template.md`. This is a
**pilot slice**, not a completed migration — see §11. Written to be honest about what was
actually validated versus shortcut, since that's the whole point of tracking this per app.

## 1. App identity

- **Name / repo**: BookStack (`ssddanbrown/BookStack`, MIT), cloned locally as
  `laravel-eloquent-sample/source-php`
- **Business owner / team**: n/a — open-source app used as a real-code validation/pilot target
  for the migration tooling, not a live internal system
- **Business-impact tier**: n/a
- **Current deploy target**: n/a (source-only clone)

## 2. Source framework & version

- **Framework**: Laravel
- **Version**: `^v12.26.4`
- **PHP version constraint**: `^8.2.0`
- **ORM in use**: Eloquent exclusively — `php-model-discovery` found 0 Doctrine entities, 37
  Eloquent models

## 3. Target configuration

- **Java version**: 21
- **Spring Boot version**: 3.4.1
- **Migration strategy**: Rewrite, structure-preserving (for the slice — package layout mirrors
  the PHP namespace: `BookStack\Entities\Models\Page` → `com.bookstack.entity.Page`)
- **Cutover mechanism**: n/a for the slice (no live traffic to cut over). For a real migration of
  the full app: strangler fig, given production scale and the size of the permissions/search/
  export surface — big-bang would be too risky.

## 4. Codebase inventory (Phase 0)

- **Files**: 1603 PHP files scanned (`php-model-discovery` run)
- **Extension dependencies**: not audited for the slice (would matter for a full migration —
  BookStack likely depends on `ext-gd` or similar for image/avatar handling, `ext-ldap` for LDAP
  auth)
- **Explicit dependency graph**: not generated — out of scope for a 4-entity slice
- **Implicit couplings found**: not surveyed for the full app. One found incidentally: BookStack
  migrated off a `deleted_at` soft-delete column onto a dedicated `Deletion` entity/table in a
  2025 migration (`2025_09_15_134701_migrate_entity_data.php`) — a schema-level implicit coupling
  that a naive per-model translation would miss entirely. Logged in §10.

## 5. Model/entity inventory

- **Manifest**: generated via `php scripts/discover-models.php --root=laravel-eloquent-sample/source-php`
  (not committed as a file in this repo snapshot — regenerate on demand)
- **Models found**: 37 (all Eloquent; 0 Doctrine)
- **Flagged for review**: 55 flags across all 37 models — dominant categories: accessor/mutator
  methods needing manual logic port, `scope*` query-building methods, a handful of medium-
  confidence base-class resolutions
- **Unresolved base classes**: 0 in the last run — default `baseClasses` config was sufficient
  (BookStack's custom `BookStack\App\Model` base resolved automatically via the inheritance walk,
  no config addition needed)

## 6. Gap inventory (app-specific)

| Construct | Location | Why it can't translate directly | Refactor target | Complexity |
|---|---|---|---|---|
| `getXAttribute`/`setXAttribute` accessors | scattered across entity models | Contains arbitrary logic, not a field passthrough | Explicit Java method, logic re-read and re-implemented per case | moderate |
| `scopeVisible()` and other `scope*` methods | e.g. `Page::scopeVisible` | Query-building logic with no declarative JPA equivalent | Custom `@Query`/Specification in the repository layer | moderate |
| Soft-delete via dedicated `Deletion` entity (not `deleted_at`) | `app/Entities/Models/Deletion.php` | Not a per-model column — a shared audit-trail table referencing any entity type polymorphically | A generic `Deletion` JPA entity + a shared soft-delete service, not a `@SQLDelete` per entity | complex |
| Joint/inherited permissions system | `app/Permissions/*` | Permission resolution walks entity hierarchy + role grants at query time, not a static field | A dedicated authorization service layer, likely Spring Security ACL or a custom evaluator | complex |

## 7. Complex workflow mappings needed

| Pattern | Present in this app? | Generic mapping applies? | Notes |
|---|---|---|---|
| Search | Yes — custom `search_terms`/full-text index tables, not Laravel Scout | Partially — the skill's generic "Search → Spring Data Elasticsearch" mapping assumes Scout; BookStack rolled its own indexing, needs its own mapping | See §10 — added as a skill gap |
| Webhooks | Yes — `Activity\Models\Webhook`, `WebhookTrackedEvent` | **No generic entry existed** | Added to skill's Complex Workflow Mappings table, see §10 |
| External auth (LDAP/SAML/OIDC) | Yes — `app/Access/{Ldap,Saml2,Oidc}Service.php` | **No generic entry existed** | Added to skill's Complex Workflow Mappings table, see §10 |
| File storage | Yes — image/attachment uploads | Yes — generic Flysystem → Spring `Resource` + S3 mapping applies | |
| Caching | Not deeply used in the slice's scope; not audited further | Yes, generic mapping applies if used | |

## 8. Rulebook deltas

- Money/currency: not applicable — BookStack has no monetary fields
- Timestamps: BookStack's original `nullableTimestamps()` migration convention means
  `created_at`/`updated_at` can be `null` on some legacy rows — map to `LocalDateTime` (nullable),
  not a non-null default, to avoid a false parity claim
- `$hidden` on `Page` (`html`, `markdown`, `text`) must map to `@JsonIgnore` on the default entity
  serialization — confirmed and applied in the slice's `Page.java`

## 9. Parity harness plan

- **Test corpus source**: none — **not built**. This is the single biggest gap in the slice: no
  real BookStack traffic or hand-authored scenario set was ever diffed against the Java output.
  What was actually done instead was a manual field-by-field comparison against the manifest and
  a local/EC2 smoke test of 3 endpoints — real signal, but not a parity harness.
- **Judge validation status**: not applicable — no judge exists yet for this app.
- **Before any further work on this app**: build this per Phase 1 before touching Phase 3+ again.

## 10. Skill feedback log

| Date | Pattern found | Skill file changed | What changed |
|---|---|---|---|
| 2026-07-25 | Webhooks (`Webhook`, `WebhookTrackedEvent` models + dispatch) had no generic workflow mapping | `php-to-java-migration/SKILL.md` §2c | Added "Webhooks" row to Complex Workflow Mappings table |
| 2026-07-25 | External auth providers (LDAP/SAML/OIDC) had no generic workflow mapping | `php-to-java-migration/SKILL.md` §2c | Added "External auth providers" row to Complex Workflow Mappings table |
| 2026-07-25 | Fine-grained/inherited permission systems (joint permissions walking entity hierarchy) had no generic mapping | `php-to-java-migration/SKILL.md` §2c | Added "Fine-grained permissions / ACL" row to Complex Workflow Mappings table |
| 2026-07-25 | Soft-delete implemented via a dedicated polymorphic `Deletion` entity rather than a `deleted_at` column is a real, non-obvious pattern the generic semantic-trap table didn't cover | `php-to-java-migration/SKILL.md` §2c | Added "Soft delete via dedicated entity" row |

## 11. Status / phase tracker

| Phase | Status | Notes |
|---|---|---|
| 0 — Assess & discover | partial | Model inventory done via manifest; full dependency graph and implicit-coupling survey not done for the whole app |
| 1 — Build the judge | **not started** | No parity harness exists — see §9 |
| 2 — Rulebook + gap inventory | partial | This doc is the gap inventory; not yet split into formal `RULEBOOK.md`/`GAP_INVENTORY.md` files |
| 3 — Stress-test | not started | Correctly skipped — no point stress-testing a rulebook that doesn't exist yet |
| 4 — Scaffold | done | `pom.xml`, Spring Boot 3.4.1 project compiles |
| 5 — Data model | done (4 entities only) | `Book`/`Chapter`/`Page`/`User` — 33 other models not ported |
| 6 — I/O layer | done (4 read endpoints only) | No auth, no writes, no other controllers |
| 7 — Business logic | not applicable at this scope | No business logic beyond CRUD in the 4 entities |
| 8 — Compile/run/parity/cutover | partial | Compiled, ran locally and on EC2, manually smoke-tested. No real parity proof (see §9). No cutover — nothing to cut over from |

## 12. Open questions / decisions log

- **2026-07-25** — Decided to scope the pilot to `Book`/`Chapter`/`Page`/`User` read-only rather
  than attempt the full app, given the size of the permissions/search/export/auth surface. Full
  migration would need Phase 1 built first, then re-run Phase 3's stress test against the gap
  items in §6 before fanning out Phase 7.
