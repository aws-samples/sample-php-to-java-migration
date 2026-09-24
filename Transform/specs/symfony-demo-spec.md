# PHP App Migration Spec — symfony-demo

## 1. App identity

- **Name / repo**: symfony-demo (symfony/symfony-demo)
- **Business owner / team**: Not yet assessed — this is the official Symfony reference/demo app
  (blog-style app with posts, comments, tags, users), likely low business-impact if used
  internally as a reference rather than a live product.
- **Business-impact tier**: Not yet assessed
- **Current deploy target**: Not yet assessed

## 2. Source framework & version

- **Framework**: Symfony (`symfony/framework-bundle ^8`)
- **Version**: Symfony 8.x demo
- **PHP version constraint**: `>=8.4`
- **ORM in use**: Doctrine, PHP-attribute mapping (4 entities found; 0 Eloquent, 0 XML-mapped —
  the simplest/cleanest of the three apps' model layers)

## 3. Target configuration

- **Java version**: 21
- **Spring Boot version**: 3.x
- **Migration strategy**: Rewrite
- **Structure**: structure-preserving (small app, no reason to redesign)
- **Cutover mechanism**: big-bang is appropriate here — at ~4,625 LOC across 51 files this is
  well under the ~20k LOC guideline for a low-traffic/reference application. Confirm existing
  test coverage before committing, per Exit Criteria's guidance that big-bang requires adequate
  coverage.

## 4. Codebase inventory (Phase 0)

- **Files / LOC**: 51 PHP files (excl. vendor), ~4,625 LOC — by far the smallest of the three
  apps, a good candidate for the Phase 3 stress-test files.
- **Extension dependencies**: `ext-ctype`, `ext-iconv`, `ext-pdo_sqlite`
- **Notable Composer deps needing Java equivalents**: `league/commonmark` (Markdown rendering for
  blog posts, via `twig/markdown-extra`), `symfony/html-sanitizer` (HTML sanitization),
  `symfony/security-bundle` (auth), `symfony/mailer` (email), `symfony/asset-mapper` +
  `symfonycasts/sass-bundle` (frontend asset pipeline — likely out of scope for a Spring Boot
  backend port; confirm whether frontend assets are in scope at all or if this becomes an
  API-only backend).
- **Explicit dependency graph**: Not yet generated (small enough this may not be necessary)
- **Implicit couplings found**: Not yet assessed

## 5. Model/entity inventory

- **Manifest path**: `manifests/symfony-demo.json`
- **Models/entities found**: 4 Doctrine-attribute entities (`Post`, `User`, `Comment`, and one
  more — see manifest for full list; 0 Eloquent, 0 XML-mapped)
- **Flagged for review**: 3 flags total:
  - `Post.content` / `Comment.content`: `String` fields recommended for `@Lob` (long-text
    columns mapped from Doctrine's `TEXT` type)
  - `User.roles`: reported as `Map/List — shape must be inferred from usage` (the `roles` column
    is a JSON-typed array; needs a read of usage to confirm shape, e.g. `List<String>` for role
    names vs. something more structured)
- **Unresolved base classes**: 0

## 6. Gap inventory (app-specific)

| Construct | Location | Why it can't translate directly | Refactor target | Complexity |
|---|---|---|---|---|
| `User.roles` (JSON column) | `src/Entity/User.php` | Shape not statically knowable from the column type alone | Read usage in `security.yaml`/`UserInterface::getRoles()` to confirm it's a flat list of role-name strings, then map to `List<String>` (or `Set<String>`) | simple (pending read) |
| `Post.content`, `Comment.content` (TEXT columns) | `src/Entity/Post.php`, `src/Entity/Comment.php` | Not a gap, just needs `@Lob` annotation in JPA | Mark as `@Lob private String content;` | trivial |

No magic methods, `$$var`, `eval`, or duck-typed constructs expected in this app given its size
and reference-implementation purpose — confirm during Phase 3 stress-test rather than assuming.

## 7. Complex workflow mappings needed

| Pattern | Present in this app? | Generic mapping applies? | Notes |
|---|---|---|---|
| Queues | No (not in composer.json) | — | |
| Scheduled tasks | No | — | |
| Events | Likely minimal (Symfony's own kernel events only) | Yes if used | |
| Caching | Not yet assessed | — | |
| File storage | No dedicated flysystem dependency | — | |
| Email | Yes (`symfony/mailer`) | Yes | Likely just contact-form or notification email, low complexity |
| Search | No | — | |
| Sessions | Likely (standard Symfony session-based auth) | Yes | |
| External auth providers | Not yet assessed | — | Check `security.yaml` — likely just Symfony's built-in form login, no OAuth/SAML |
| Fine-grained permissions/ACL | Likely simple (blog app — probably just `ROLE_ADMIN` vs anonymous) | Standard Spring Security role-based mapping | |

## 8. Rulebook deltas

None expected beyond the base Symfony-to-Spring seed table — this app is small and clean enough
that it's a good Phase 3 stress-test candidate rather than needing its own deltas up front.

## 9. Parity harness plan (Phase 1)

- **Test corpus source**: `symfony/symfony-demo` ships PHPUnit functional tests
  (`phpunit/phpunit ^11.5.50` in require-dev) that exercise routes at the HTTP level — a strong,
  ready-made source for the parity harness's scenario corpus.
- **Coverage**: Not yet built
- **Judge validation status**: Not started

## 10. Skill feedback log

| Date | Pattern found | Skill file changed | What changed |
|---|---|---|---|
| | | | |

## 11. Status / phase tracker

| Phase | Status | Notes |
|---|---|---|
| 0 — Assess & discover | in progress | Composer/framework confirmed |
| 1 — Build the judge | not started | Existing PHPUnit functional tests identified as harness source |
| 2 — Rulebook + gap inventory | in progress | Minimal gap inventory drafted above |
| 3 — Stress-test | not started | Good candidate app for this phase given its small size |
| 4 — Scaffold | not started | |
| 5 — Data model | not started | Manifest ready (`manifests/symfony-demo.json`) |
| 6 — I/O layer | not started | |
| 7 — Business logic | not started | |
| 8 — Compile/run/parity/cutover | not started | |

## 12. Open questions / decisions log

- 2026-07-26: Model-discovery scanner run against `apps/symfony-demo` — 4 Doctrine-attribute
  entities, 3 flags, 0 Eloquent/XML entities (expected). Manifest at
  `manifests/symfony-demo.json`.
- 2026-07-26: Confirmed this app's flags/mappings were unaffected by the `DOCTRINE_TYPE_MAP`
  scanner fix applied while investigating sylius's false-positive flags (re-ran scanner, `Long`/
  `String` mappings unchanged).
