# PHP App Migration Spec — sylius

## 1. App identity

- **Name / repo**: sylius (sylius/sylius) — e-commerce platform, monorepo of Sylius bundles/components
- **Business owner / team**: Not yet assessed
- **Business-impact tier**: Not yet assessed
- **Current deploy target**: Not yet assessed

## 2. Source framework & version

- **Framework**: Symfony (`symfony/framework-bundle ^6.4.1 || ^7.4 || ^8.0`)
- **Version**: `sylius/sylius v2.2.7-dev`
- **PHP version constraint**: `^8.3`
- **ORM in use**: Doctrine — both PHP-attribute mapping (5 entities found) and external
  `*.orm.xml` mapping (98 entities found). This is the dominant style: Sylius's domain model
  classes are largely framework-agnostic and mapped externally so components can be reused
  without a hard Doctrine dependency, per the manifest-schema notes.

## 3. Target configuration

- **Java version**: 21
- **Spring Boot version**: 3.x
- **Migration strategy**: Rewrite
- **Structure**: Not yet decided — this is a monorepo of ~30 Sylius bundles/components (Core,
  Order, Payment, Shipping, Taxation, Promotion, Product, Customer, Channel, etc.). A
  structure-preserving port would mirror this into a multi-module Maven/Gradle build; a redesign
  could consolidate. Recommend structure-preserving given the scale, to keep the existing
  component boundaries as a translation unit of work.
- **Cutover mechanism**: Not yet decided — at ~391k LOC across 4851 files this is by far the
  largest of the three apps and strangler-fig is effectively mandatory; a route-by-route or
  bundle-by-bundle proxy approach should be assumed unless told otherwise.

## 4. Codebase inventory (Phase 0)

- **Files / LOC**: 4851 PHP files (excl. vendor), ~391,009 LOC
- **Extension dependencies**: `ext-dom`, `ext-exif`, `ext-fileinfo`, `ext-filter`, `ext-gd`,
  `ext-hash`, `ext-intl`, `ext-json`, `ext-simplexml`, `ext-sodium`
- **Notable Composer deps needing Java equivalents**: `api-platform/*` (REST/GraphQL API
  generation — likely maps to Spring HATEOAS/Data REST or a hand-rolled controller layer, needs
  a dedicated decision), `gedmo/doctrine-extensions` + `stof/doctrine-extensions-bundle`
  (Timestampable/Sluggable/Tree extensions — see SKILL.md §2c mapping), `payum/*` (payment
  gateway abstraction), `lexik/jwt-authentication-bundle` (JWT auth → Spring Security JWT),
  `liip/imagine-bundle` (image processing pipeline), `sylius/grid` + `sylius/grid-bundle`
  (admin grid/listing abstraction — likely needs a bespoke Java equivalent, no direct Spring
  analog), `symfony/workflow` (state machine — Order/Payment/Shipment states; maps to Spring
  Statemachine or a hand-rolled state enum + transition table), `symfony/messenger` +
  `doctrine/doctrine-messenger` (async messaging → Spring's `@Async`/JMS/RabbitMQ depending on
  transport config, needs to be confirmed).
- **Explicit dependency graph**: Not yet generated (30+ internal bundles/components — worth
  generating early given the scale)
- **Implicit couplings found**: Not yet assessed

## 5. Model/entity inventory

- **Manifest path**: `manifests/sylius.json`
- **Models/entities found**: 103 total — 5 Doctrine-attribute entities, 98 Doctrine-XML-mapped
  entities (0 Eloquent, as expected for a Symfony app)
- **Flagged for review**: 118 flags (was 127 before the scanner fix below), breaking down as:
  - 117 × interface-targeted relationship (`ResolveTargetEntityListener` pattern) — by far the
    dominant flag type in this app. XML mappings deliberately target an interface
    (e.g. `CurrencyInterface`, `OrderItemInterface`, `AdjustmentInterface`, `OrderInterface`)
    and Symfony config (`doctrine.orm.resolve_target_entities`) resolves it to a concrete class
    at runtime. **This is the single biggest gap-inventory item for this app** — Java/JPA has no
    equivalent runtime relationship-type resolution, so every one of these 117 needs its
    concrete implementation looked up in Symfony bundle config before a JPA relationship type
    can be chosen.
  - 1 × other
  - (Resolved) 9 × false-positive "unknown Doctrine type" flags for `'integer'`/`'string'` — this
    was a real scanner bug (`DOCTRINE_TYPE_MAP` used uppercase keys, but attribute-mapped
    `#[ORM\Column(type: 'integer')]` uses lowercase), fixed in `discover-models.php` and the
    manifest regenerated. See §10.
- **Unresolved base classes**: 0

## 6. Gap inventory (app-specific)

| Construct | Location | Why it can't translate directly | Refactor target | Complexity |
|---|---|---|---|---|
| Interface-targeted relationships (117 occurrences) | Across nearly every `*.orm.xml`-mapped component (Order, Payment, Shipping, Taxation, Currency, etc.) | `ResolveTargetEntityListener` resolves relationship target types at runtime via Symfony config, not statically in the XML | For each interface, find the concrete implementation registered in `config/packages/*/doctrine.yaml` or a bundle's `resolve_target_entities` config, then map the JPA relationship directly to that concrete entity type | complex — high volume, mechanical once the resolution-list is built |
| `symfony/workflow`-based state machines (Order/Payment/Shipment states) | Core order-processing components | Symfony's declarative YAML workflow config has no JPA/Spring equivalent | Spring Statemachine, or an explicit enum + transition-validation service | complex |
| `sylius/grid` admin grid abstraction | Admin bundles | Bespoke Sylius abstraction, no Spring analog | Hand-roll a paginated/filterable query + view-model layer per grid | complex |
| Gedmo extensions (Timestampable, Sluggable, Tree — via `stof/doctrine-extensions-bundle`) | Wherever these traits/behaviors are attached in XML mapping | Doctrine event-listener-driven behavior, not declared in the entity class itself | JPA `@CreationTimestamp`/`@UpdateTimestamp` (Timestampable), a slug-generation service (Sluggable), and an adjacency-list or closure-table pattern (Tree) — check SKILL.md §2c before improvising | moderate–complex |
| ~~Unmapped Doctrine types~~ (resolved) | Various | Was a scanner type-map bug (uppercase keys vs. lowercase Doctrine type strings), not a real gap | Fixed in `discover-models.php`; no longer needs manual review | resolved |

## 7. Complex workflow mappings needed

| Pattern | Present in this app? | Generic mapping applies? | Notes |
|---|---|---|---|
| Queues / async messaging | Yes (`symfony/messenger`, `doctrine/doctrine-messenger`) | Needs dedicated mapping | Confirm transport (Doctrine DBAL transport vs AMQP) before choosing Spring equivalent |
| Scheduled tasks | Not yet assessed | — | |
| Events | Yes (Symfony event dispatcher is pervasive in Sylius's extensibility model) | Yes (→ Spring `ApplicationEvent`) | High volume — Sylius's plugin architecture relies heavily on events |
| Caching | Not yet assessed | — | |
| File storage | Yes (`league/flysystem-bundle`) | Yes | |
| Email | Yes (`sylius/mailer`, `sylius/mailer-bundle`, `symfony/mailer`) | Yes | |
| Search | Not yet assessed | — | Check for Elasticsearch/Sylius search integration |
| Fine-grained permissions/ACL | Likely (multi-channel, multi-locale e-commerce admin) | Needs dedicated mapping | |
| Soft-delete-via-dedicated-entity | Not yet assessed | — | |
| Gedmo extensions | Yes (Timestampable/Sluggable/Tree per `gedmo/doctrine-extensions`) | Needs dedicated mapping | See gap inventory above |
| State machines (order/payment/shipment workflow) | Yes (`symfony/workflow`) | Needs dedicated mapping | See gap inventory above |
| Payment gateway abstraction | Yes (`payum/*`) | Needs dedicated mapping | |

## 8. Rulebook deltas

Not yet written — pending Phase 3 stress-test. Given the volume of interface-targeted
relationships, the rulebook should include a lookup table of
interface → concrete-implementation before Phase 5 (data model translation) starts, built by
grepping every `resolve_target_entities` config entry across all bundles.

## 9. Parity harness plan (Phase 1)

- **Test corpus source**: Not yet determined. Sylius ships an extensive Behat test suite
  (`behat/behat`, `friends-of-behat/*` in require-dev) which is a strong candidate source for
  externally-observable scenarios, since Behat scenarios are already written against
  HTTP/browser-level behavior rather than internals.
- **Coverage**: Not yet built
- **Judge validation status**: Not started

## 10. Skill feedback log

| Date | Pattern found | Skill file changed | What changed |
|---|---|---|---|
| 2026-07-26 | 9 flags citing "unknown Doctrine type 'integer'/'string'" on attribute-mapped entities — confirmed real scanner bug, not an app-specific gap | `document_references/scripts/discover-models.php` and its synced copy at `document_references/php-model-discovery/scripts/discover-models.php` | `DOCTRINE_TYPE_MAP` keys changed from uppercase (`'INTEGER'`, `'STRING'`, ...) to lowercase (matching Doctrine's actual DBAL type identifiers); `mapDoctrineType()` now normalizes the lookup key via `strtolower()` so a `Types::INTEGER` constant capture (yields `"INTEGER"`) still resolves. Manifest regenerated: flags dropped 127 → 118, `symfony-demo`'s existing correct mappings (`javaType: "Long"`/`"String"`) confirmed unchanged. |

## 11. Status / phase tracker

| Phase | Status | Notes |
|---|---|---|
| 0 — Assess & discover | in progress | Composer/framework confirmed; dependency graph not yet generated |
| 1 — Build the judge | not started | |
| 2 — Rulebook + gap inventory | in progress | Seed gap inventory drafted above, dominated by interface-target relationships |
| 3 — Stress-test | not started | |
| 4 — Scaffold | not started | |
| 5 — Data model | not started | Manifest ready (`manifests/sylius.json`); interface-resolution lookup table needed first |
| 6 — I/O layer | not started | |
| 7 — Business logic | not started | |
| 8 — Compile/run/parity/cutover | not started | |

## 12. Open questions / decisions log

- 2026-07-26: Model-discovery scanner run against `apps/sylius` — 103 entities (5 attribute + 98
  XML), 127 flags, dominated by `ResolveTargetEntityListener` interface-target relationships.
  Manifest at `manifests/sylius.json`.
- 2026-07-26: Flagged possible scanner type-map gap for `integer`/`string` Doctrine types — needs
  resolution before trusting the "9 unmapped type" count as a real app-specific issue.
