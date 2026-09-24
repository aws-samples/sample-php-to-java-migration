# Sylius — PHP App Migration Spec

Filled from `.kiro/skills/php-to-java-migration/references/app-spec-template.md`. Originally
written for a discovery-only pass (see the original §11 note below); a follow-up pass then built
and validated a bounded pilot slice — `Product`/`Taxon`/`Channel`, read-only — specifically to
prove the *translation* methodology against the two hardest gaps this app surfaced: the
`ResolveTargetEntityListener` interface-relationship pattern and Gedmo `Timestampable` auditing.
Both are confirmed working end-to-end (not just compiling) — see §11.

## 1. App identity

- **Name / repo**: Sylius (`Sylius/Sylius`, MIT), cloned locally as `sylius-doctrine-sample/source-php`
- **Business owner / team**: n/a — open-source e-commerce platform used as a second, structurally
  different validation target for the migration tooling
- **Business-impact tier**: n/a
- **Current deploy target**: n/a (source-only clone)

## 2. Source framework & version

- **Framework**: Symfony (bundle/component monorepo, not a single app)
- **Version**: `^6.4.1 || ^7.4 || ^8.0`
- **PHP version constraint**: `^8.3`
- **ORM in use**: Doctrine — but predominantly via **XML mapping** (`*.orm.xml`), not PHP
  attributes. This was the core finding of this run (see §6, §10) — Sylius keeps its Component
  model classes framework-agnostic (no Doctrine coupling) specifically so they can be reused
  without pulling in Doctrine as a hard dependency; only 5 classes in the whole codebase use
  `#[ORM\Entity]` attributes, and all 5 are test fixtures, not domain entities.

## 3. Target configuration

- **Java version**: 21
- **Spring Boot version**: 3.4.1
- **Migration strategy**: Rewrite, deliberately flattened rather than structure-preserving —
  Sylius's Bundle/Component monorepo split has no natural Java equivalent for a bounded pilot, so
  `Product`/`Taxon`/`Channel` were mapped directly into one `com.sylius.entity` package. A real
  full migration would need its own decision here, separate from this pilot's shortcut.
- **Scope of the pilot slice**: `Product`, `Taxon`, `Channel` — base Component fields only, not
  CoreBundle's much larger e-commerce overlay (currencies, locales, countries, billing data,
  price-history config were all left out deliberately to keep this bounded).

## 4. Codebase inventory (Phase 0)

- **Files scanned**: 4730 PHP files (`php-model-discovery` run)
- **Classes discovered**: 3496
- **Scale note**: this file count is what surfaced a real memory-scaling bug in the scanner
  itself (see §10) — BookStack's 1603 files never came close to the same problem. Any app of
  comparable or larger scale in the real portfolio would have hit the same wall before this fix.
- **Extension dependencies**: not audited
- **Explicit/implicit dependency graph**: not generated — out of scope for a discovery-only pass

## 5. Model/entity inventory

- **Manifest**: generated via `php scripts/discover-models.php --root=sylius-doctrine-sample/source-php`
- **Eloquent models found**: 0 (expected — Sylius has no Laravel/Eloquent anywhere)
- **Doctrine attribute entities found**: 5 — all test fixtures (`Foo`, `Bar`, `FooSyliusResource`,
  plus `Taxon`/`PromotionSubject` under `ApiBundle`'s own test namespace), **not real domain
  entities**. Trusting this number alone would have produced a badly wrong picture of the app.
- **Doctrine XML-mapped entities found**: 98 — the real domain model: `Product`, `Order`,
  `Customer`, `Taxon`, `Channel`, `Payment`, `Shipment`, `Promotion`, and many more, matching the
  98 `*.orm.xml` files in the codebase 1:1.
- **Flagged for review**: 127 flags total. Dominant category by far: relationships whose
  `target-entity` is an interface rather than a concrete class (the `ResolveTargetEntityListener`
  pattern — see §6).

## 6. Gap inventory (app-specific)

| Construct | Location | Why it can't translate directly | Refactor target | Complexity |
|---|---|---|---|---|
| Doctrine relationships mapped to an interface, resolved to a concrete class via Symfony service config (`ResolveTargetEntityListener`) | Nearly every relationship in every `*.orm.xml` file — e.g. `Product.mainTaxon` targets `TaxonInterface`, not a concrete `Taxon` class | Java/JPA relationship annotations need a concrete target type; there's no equivalent of resolving an interface to an implementation at the ORM-mapping level in JPA | For each flagged relationship, find the app's `doctrine.orm.resolve_target_entities` config (per-bundle, e.g. in `CoreBundle`'s service config) to identify the real concrete class before writing the JPA annotation | moderate (mechanical once the config is found, but must be done per relationship — dozens of them) |
| Gedmo `Timestampable` extension on nearly every entity | `<gedmo:timestampable on="create"/>` / `on="update"` inside `<field>` elements (seen in `Locale.orm.xml` and likely most others) | Not a plain column — value is set by a Doctrine event listener, not application code | Spring Data JPA auditing (`@CreatedDate`/`@LastModifiedDate` + `AuditingEntityListener`) — added as a generic mapping in the skill, see §10 | moderate |
| `<mapped-superclass>` as the dominant pattern (not `<entity>`) | 90%+ of the 98 XML files use `mapped-superclass`, not `entity` — the concrete, table-owning `entity` for each is defined at the application/bundle-implementation layer, not in the Component itself | Not a translation problem per se, but means a naive 1:1 "one XML file = one JPA `@Entity`" approach is wrong — most of these need `@MappedSuperclass`, and the actual concrete entity (with its `@Entity`/`@Table`) lives elsewhere or is only defined once a concrete app (like `Sylius/SyliusStandard`) wires the Component together | Confirm each `mapped-superclass` really has a concrete `entity` extending it somewhere in the target app before assuming a 1:1 file-to-JPA-class mapping | complex (architectural, not mechanical) |

## 7. Complex workflow mappings needed

| Pattern | Present in this app? | Generic mapping applies? | Notes |
|---|---|---|---|
| Auditing (Gedmo Timestampable/Blameable) | Yes, pervasive | **No generic entry existed before this run** | Added to skill's Complex Workflow Mappings table, see §10 |
| Payment processing | Yes — Payum integration (`GatewayConfig`, `PaymentSecurityToken` entities found) | Not assessed — Payum has no PHP-agnostic equivalent noted yet; would need its own research pass | Not added to the skill this round — flagged as a follow-up, not fabricated |
| Search/indexing | Not assessed this pass | — | Sylius's search integration wasn't inventoried in this discovery-only run |

## 8. Rulebook deltas

Not written — no `RULEBOOK.md` was started for this app, since no translation work followed the
discovery pass. If pursued later, start from `references/laravel-to-spring.md`/
`symfony-to-spring.md` plus the Doctrine-XML entry notes in `php-model-discovery`'s
`manifest-schema.md`.

## 9. Parity harness plan

Not applicable — no Java target exists to prove parity against.

## 10. Skill feedback log

| Date | Pattern found | Skill file changed | What changed |
|---|---|---|---|
| 2026-07-25 | Scanning a monorepo-scale codebase (4730 files) exhausted PHP's default 128MB memory limit — the Eloquent path held every scanned file's full tokenized source in memory for the entire run | `php-model-discovery/scripts/discover-models.php` | Rearchitected: `classIndex` now stores only lightweight structural facts (namespace/extends/uses) for the whole run; full token data is reloaded on demand, per file, only for confirmed models and their ancestors, via a new `loadFullClassInfo()` with a per-file cache |
| 2026-07-25 | Attribute-based Doctrine detection alone found only 5 entities (all test fixtures) against an app with ~98 real domain entities — Sylius maps its Components via external `*.orm.xml` files specifically to keep model classes framework-agnostic | `php-model-discovery/scripts/discover-models.php`, `SKILL.md`, `references/manifest-schema.md` | Added a third, fully independent discovery strategy: `collectXmlMappingFiles()` + `scanXmlMappingFile()`, parsing `<entity>`/`<mapped-superclass>` elements (fields, id, all four relationship types, join tables/columns, order-by direction) via SimpleXML. Wired into `main()` as `doctrineXmlEntitiesFound`. Documented in `SKILL.md`'s "why inheritance/attributes, not folder convention" section and validated-against list, and as a new manifest entry type in `manifest-schema.md` |
| 2026-07-25 | Doctrine relationships in Sylius's XML mapping target interfaces (e.g. `TaxonInterface`), resolved to concrete classes via Symfony's `ResolveTargetEntityListener` service config — invisible to any static PHP/XML scan alone | `php-model-discovery/scripts/discover-models.php` (flag logic), `php-to-java-migration/SKILL.md` §2b | Scanner flags every interface-targeted relationship rather than guessing; added a row to the Gap Inventory table describing the pattern and refactor target |
| 2026-07-25 | Gedmo Doctrine extensions (`Timestampable` seen directly; `Sluggable`/`Blameable`/`Translatable`/`Loggable` are the same family and common alongside it in Symfony apps using `stof/doctrine-extensions-bundle`) had no generic workflow mapping | `php-to-java-migration/SKILL.md` §2c | Added "Automatic auditing fields via a Doctrine extension library" row to Complex Workflow Mappings, mapping each Gedmo behavior to its Spring/JPA equivalent |
| 2026-07-25 | Gedmo's nested-set `tree` extension (`tree-left`/`tree-right`/`tree-level`, auto-maintained by a Doctrine listener) on `Taxon` has no JPA equivalent | `sylius-doctrine-sample/MIGRATION_SPEC.md` (this doc), not the skill itself | Deliberately **not** added as a generic mapping — this is a redesign decision (adjacency list instead of nested set), not a mechanical translation, and it's app/query-pattern-dependent whether that's safe. Documented as a redesign call in `Taxon.java`'s javadoc rather than generalized into the skill |
| 2026-07-25 | Building the pilot slice: a `@ManyToMany` relationship (`Product.channels`) compiled cleanly but threw `LazyInitializationException` on first real request, because `open-in-view: false` closes the Hibernate session before Jackson serializes a `LAZY`-by-default collection | `php-to-java-migration/SKILL.md` Phase 6 | Added a step: any JPA collection reachable from a serialized REST response needs an explicit fetch/serialization decision during Phase 6, not a runtime discovery — this is a Java-side operational gotcha independent of the PHP source, but real enough to burn a real migration if not called out explicitly |

## 11. Status / phase tracker

| Phase | Status | Notes |
|---|---|---|
| 0 — Assess & discover | partial | File/class inventory done via manifest; explicit/implicit dependency graph not generated |
| 1 — Build the judge | not started | No parity harness exists — same honest gap as BookStack; this pilot validated the *methodology*, not parity against real Sylius traffic |
| 2 — Rulebook + gap inventory | partial | This doc *is* the gap inventory for what was found; no separate `RULEBOOK.md` written |
| 3 — Stress-test | not started | Skipped, same as BookStack |
| 4 — Scaffold | done | `pom.xml`, Spring Boot 3.4.1, compiles |
| 5 — Data model | done (3 entities) | `Product`/`Taxon`/`Channel` — both interface-resolved relationship types (`@ManyToOne`, `@ManyToMany`) and JPA auditing confirmed working, not just compiling |
| 6 — I/O layer | done (read-only) | List/get endpoints for all 3 entities, plus `/taxons/{id}/children` |
| 7 — Business logic | not applicable at this scope | No business logic beyond CRUD in the 3 entities |
| 8 — Compile/run/parity/cutover | partial | Compiled, ran locally and on real EC2 infrastructure (same instance as BookStack, port 8081), manually verified from outside the instance — including catching and fixing a real `LazyInitializationException` (§10). No formal parity proof against real Sylius traffic |

## 12. Open questions / decisions log

- **2026-07-25** — Decided to stop at the discovery/spec stage for Sylius rather than also
  building a pilot Java slice (unlike BookStack). The value of this app was stress-testing the
  *scanner* against a monorepo with a different Doctrine mapping style; it did that, and surfaced
  three real, now-fixed gaps. Building a runnable slice would be a reasonable next step if this
  app is picked up again, but wasn't the point of this pass.
