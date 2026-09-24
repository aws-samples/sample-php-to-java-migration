---
name: php-model-discovery
description: Generically discover and extract ORM model/entity metadata from any PHP codebase — Eloquent models (fields, casts, relationships, accessors/mutators, soft deletes, visibility rules), Doctrine attribute entities (columns, ids, relationships, join tables), and Doctrine XML-mapped entities (apps like Sylius that keep domain classes framework-agnostic and map them externally via *.orm.xml) alike — via token/attribute/XML scanning, not folder convention, so the same scanner runs unmodified across many differently-structured applications. Use this whenever migrating PHP to Java at scale (a portfolio of Laravel/Symfony services, "100+ apps"), whenever php-to-java-migration's Phase 0 discovery or Phase 5 data-model translation needs a reliable model/entity inventory before hand-translating, or when the user asks to "find all models", "list Eloquent models", "inventory Doctrine entities", "generate a model manifest", or wants Java/JPA entity generation seeded from extraction instead of manual reading. Companion skill to php-to-java-migration — run this first; its manifest feeds that skill's RULEBOOK.md, GAP_INVENTORY.md, and entity generation.
---

# PHP Model Discovery

Find every ORM model in a PHP codebase and extract exactly what a Java/JPA port needs for
behavioral parity — not just field names, but casts, visibility rules, relationship wiring, and a
flag on everything that contains logic a script cannot safely translate. Runs via
`scripts/discover-models.php`, a dependency-free PHP CLI tool (core `token_get_all` only — no
Composer install, no autoloader, no booting the target app) so it can run against any app in a
100+-app portfolio regardless of that app's own health or dependency state.

This is a companion to [[php-to-java-migration]]: that skill's Phase 0 (assess and discover) and
Phase 5 (translate the data model) both need a model inventory before a human or an implementer
subagent starts writing Java. This skill produces that inventory mechanically instead of having
someone hand-read every model file across every app.

## Why inheritance/attributes, not folder convention

A scanner that assumes "models live in `app/Models/`" breaks the moment it meets a real app —
BookStack (a large, actively maintained Laravel app used to validate the Eloquent path here) keeps
its models scattered across `app/Entities/Models/`, `app/Users/Models/`, `app/Activity/Models/`,
`app/Permissions/Models/`, and half a dozen other module folders, with zero files under
`app/Models/` itself. Folder convention varies per app and per team; **inheritance doesn't**. Every
Eloquent model, however deeply wrapped in an app's own base classes, ultimately extends
`Illuminate\Database\Eloquent\Model`. So the scanner finds classes by walking the `extends` chain
to a configured base class, however many app-specific hops that takes — this is what let it find
all 37 of BookStack's models (including through a 4-level chain like
`Page extends BookChild extends Entity extends App\Model extends Model as EloquentModel`) with zero
folder assumptions and a default config.

Doctrine entities don't use a common base class at all — they're identified by carrying the
`#[ORM\Entity]` attribute, so this skill runs a second, independent strategy for that case.

A third strategy exists because attributes aren't universal either: Sylius (a large,
actively-maintained Symfony e-commerce platform) deliberately keeps its domain model classes
framework-agnostic — no Doctrine attribute on the class at all — and maps them externally via
`*.orm.xml` files instead, specifically so Components can be reused without a hard Doctrine
dependency. Running the attribute-based strategy alone against Sylius found only 5 entities (all
test fixtures); the real ~98 domain entities were invisible to it. The XML-mapping strategy finds
these independently, by walking for `*.orm.xml` files rather than reading anything in the PHP
source at all.

All three strategies run on every scan unconditionally; an app using only one mapping style simply
produces zero results from the other two. There's no `--framework` flag to get wrong.

## Workflow

### 1. Confirm or write a config (usually skip this)

Most apps need nothing — defaults cover a bare `Illuminate\Database\Eloquent\Model` hierarchy and
standard casts. Only write a `model-discovery.config.json` for an app whose base classes or cast
conventions genuinely diverge (see `references/config-schema.md`). Keep it colocated with that
app, e.g. `<app-root>/.migration/model-discovery.config.json` — never a shared config across the
portfolio, since that defeats the point of a scanner that needs no per-app code changes.

### 2. Run the scanner

```bash
php scripts/discover-models.php --root=/path/to/app [--config=path] --pretty --out=manifest.json
```

Requires PHP 8.0+ **on the machine running this scanner** — irrelevant to the target app's own PHP
version, since the script only tokenizes source text and never executes the app's code. Stderr
reports file/class counts as a sanity check; stdout (or `--out`) gets the JSON manifest. See
`references/manifest-schema.md` for the full shape of `models[]`, `unresolvedBaseClasses[]`, and
what a non-empty `flags[]` means for a given entry.

### 3. Review flags with the user before trusting anything

Every model/entity entry carries `flags[]` — plain-English notes on whatever the scanner could not
resolve mechanically: a `$fillable` built from a runtime expression instead of a literal array, a
relationship whose target class it couldn't pin down, every accessor/mutator (always flagged,
since these contain arbitrary logic that must be read, never assumed to be a plain passthrough), an
unmapped cast/column type. Walk these with the user before generating anything — this list *is*
the manual-review queue, not an afterthought.

### 4. Hand off to php-to-java-migration

The manifest seeds three deliverables in that skill's workflow:

- **Phase 0 (`MIGRATION_ASSESSMENT.md`)** — `summary.eloquentModelsFound` /
  `doctrineEntitiesFound` and `unresolvedBaseClasses[]` feed the inventory and the implicit-coupling
  survey (a model with a low `confidence` score is exactly the kind of implicit coupling that
  static import-graph analysis misses).
- **Phase 2b (`GAP_INVENTORY.md`)** — every `flags[]` entry that describes a refactor-not-translate
  construct (a dynamic `$fillable`, an unresolved relationship target) becomes a gap-inventory line
  with its own complexity rating, per that skill's rule that these constructs get refactored into
  an explicit contract, never translated line-by-line.
- **Phase 5 (data model translation)** — `columns`/`casts` seed the type-map lookups in
  `RULEBOOK.md`; `relationships` seed JPA `@OneToMany`/`@ManyToOne`/etc. generation directly;
  `accessorsAndMutators`/`scopes` become `// TODO(port)` markers rather than guessed
  translations, per Phase 7's implement/review/fix loop.

### 5. At 100+-app scale: run per-app, don't try to unify the scan

Run the scanner once per app (each app is an independent `--root`), collect manifests into a
per-app directory (e.g. `<app>/MIGRATION/model-manifest.json`), and only aggregate *after* — a
lightweight rollup across manifests (total models, common flag patterns, apps sharing a base-class
convention) is useful for spotting portfolio-wide patterns worth a shared rulebook rule, but the
scan itself must stay per-app so one app's health/size never blocks another's.

## What gets flagged instead of guessed (read before trusting a clean manifest)

| Situation | What the manifest shows |
|---|---|
| Property value built from a runtime expression (function call, variable, non-literal array item) | `flags[]` entry; the field itself is `null`/`[]` rather than a guess |
| Relationship call composed through another local method (e.g. `revisions()` returning `allRevisions()->where(...)`) | Silently absent — no literal relationship call exists in that method body to find. Not a bug; resolving it needs call-graph analysis this tool deliberately doesn't attempt. Cross-check relationship-light classes against real usage |
| `getXAttribute`/`setXAttribute`/`Attribute::make` accessor or mutator | Always flagged, always listed — contains logic that must be read and re-implemented, never assumed to be a field passthrough |
| Extends-chain resolved by short-name heuristic rather than a confirmed FQCN match | `confidence: "medium"` or lower, plus a `flags[]` entry — verify by hand before trusting the rest of that entry |
| Doctrine relationship attribute with no resolvable `targetEntity` | `target: null` plus a `flags[]` entry citing the field and line |
| Cast/column type with no built-in or config-overridden mapping | `javaType` starts with `FLAG:`, paired with a `flags[]` entry |
| Doctrine XML relationship's `target-entity` is an interface (Sylius's `ResolveTargetEntityListener` pattern — maps to an interface in XML, resolved to a concrete class via Symfony service config at runtime) | `target` is reported as-is (the interface FQCN), plus a `flags[]` entry — the concrete implementation must be found in that app's Symfony config before the Java relationship's target type can be chosen |
| `<entity>` in XML mapping with no `<id>` of its own | `id: null` plus a `flags[]` entry — likely inherits one from a `<mapped-superclass>`; not flagged for `<mapped-superclass>` itself, since those commonly omit `id` by design |

A model/entity with an empty `flags[]` array means nothing *mechanical* looked ambiguous — it is
not a substitute for reading the business logic in its relationship/scope/accessor methods.

## Validated against

- **Eloquent**: BookStack (`ssddanbrown/BookStack`, MIT), a large actively-maintained Laravel app —
  37 models found across a dozen module folders, including 4-level custom-base-class inheritance
  chains, trait inheritance through abstract ancestors (a `SoftDeletes` used only on an abstract
  `Entity` base correctly propagates to every concrete subclass), and accurate relationship/cast/
  visibility extraction confirmed by direct comparison against the source files.
- **Doctrine (attributes)**: the Symfony Demo application's `Post`/`User`/`Comment`/`Tag` entities,
  cross-checked field-by-field against an independently hand-migrated `Post.java` (JPA) already
  present in that project — column nullability, `@Lob` flagging, `@OneToMany`/`@ManyToOne`/
  `@ManyToMany` wiring including `mappedBy`/`cascade`/`orphanRemoval`, and `@OrderBy` direction all
  matched.
- **Doctrine (XML mapping)**: Sylius (`Sylius/Sylius`, MIT) — 98 entities/mapped-superclasses found
  across the monorepo (e.g. `Product`, `Order`, `Customer`, `Taxon`), with fields, relationships
  (including `join-table`/`join-column`/`order-by` direction), and the interface-target-entity
  pattern all extracted correctly, cross-checked directly against the raw XML. Running against
  this monorepo (4730 files) is also what surfaced a real memory-scaling bug in the Eloquent path
  — see Rules below.

## Rules

- Discover by inheritance (Eloquent), attribute (Doctrine), or external mapping file (Doctrine
  XML), never by folder path — folder convention varies per app; this is the property that makes
  one scanner script work unmodified across a 100+-app portfolio.
- Never hold every scanned file's full tokenized source in memory for the whole run — a
  monorepo-scale codebase (Sylius: 4730 files) will exhaust PHP's default memory limit. Keep the
  persistent class index lightweight (structural facts only: namespace, extends, uses-imports);
  reload full token data on demand, per file, only for the small subset of files that turn out to
  contain a confirmed model or one of its ancestors.
- Flag ambiguity, never guess: any non-literal value, unresolved relationship target, or logic-
  bearing method becomes a `flags[]` entry, not a best-effort translation.
- Config lives per-app, not in the script. The script itself never changes between apps; only an
  optional, app-local `model-discovery.config.json` does.
- Both extraction strategies always run; there is no framework flag to set or get wrong.
- Property and trait values inherit down the extends chain like real PHP semantics (nearest-
  declared-wins for properties, union-across-the-chain for traits) — reading only the leaf class
  misses real behavior, which is the opposite of the parity this tool exists to establish.
- A clean manifest (no flags) is not a substitute for reading relationship/scope/accessor method
  bodies — it only means nothing *mechanical* looked ambiguous.
- This scanner requires PHP 8.0+ to run, independent of the target app's own PHP version, since it
  only tokenizes source text and never executes target-app code.

## References (load on demand)

- `scripts/discover-models.php` — the scanner; run directly, read when debugging an unexpected
  extraction result
- `references/manifest-schema.md` — full JSON shape for Eloquent, Doctrine-attribute, and
  Doctrine-XML entries, field-by-field notes on what `null`/default values actually mean
- `references/config-schema.md` — `model-discovery.config.json` schema and the per-app-override
  convention for a large portfolio
