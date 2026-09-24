# PHP App Migration Spec — bookstack

## 1. App identity

- **Name / repo**: bookstack (bookstackapp/bookstack)
- **Business owner / team**: Not yet assessed
- **Business-impact tier**: Not yet assessed
- **Current deploy target**: Not yet assessed

## 2. Source framework & version

- **Framework**: Laravel
- **Version**: `laravel/framework ^v12.26.4`
- **PHP version constraint**: `^8.2.0`
- **ORM in use**: Eloquent (37 models found, 0 Doctrine, 0 Doctrine-XML — confirms this is a
  pure-Eloquent app)

## 3. Target configuration

- **Java version**: 21
- **Spring Boot version**: 3.x
- **Migration strategy**: Rewrite
- **Structure**: structure-preserving (default; revisit after Phase 3 stress-test)
- **Cutover mechanism**: Not yet decided — LOC (~189k across 1763 non-vendor PHP files) puts
  this above the ~20k LOC big-bang guideline, so strangler-fig is the presumptive default;
  confirm with traffic/criticality data before committing.

## 4. Codebase inventory (Phase 0)

- **Files / LOC**: 1763 PHP files (excl. vendor), ~189,021 LOC
- **Extension dependencies**: `ext-curl`, `ext-dom`, `ext-fileinfo`, `ext-gd`, `ext-json`,
  `ext-mbstring`, `ext-xml`, `ext-zip`
- **Notable Composer deps needing Java equivalents**: `dompdf/dompdf` (PDF gen),
  `knplabs/knp-snappy` (wkhtmltopdf wrapper), `intervention/image` (image processing),
  `laravel/socialite` + `socialiteproviders/*` (OAuth: Discord, GitLab, Azure, Okta, Twitch),
  `onelogin/php-saml` (SAML SSO), `pragmarx/google2fa` (TOTP 2FA), `predis/predis` (Redis
  client), `league/flysystem-aws-s3-v3` (S3 storage), `league/commonmark` +
  `league/html-to-markdown` (Markdown), `ezyang/htmlpurifier` + `xemlock/htmlpurifier-html5`
  (HTML sanitization), `ssddanbrown/htmldiff` (HTML diffing for page revisions).
- **Explicit dependency graph**: Not yet generated
- **Implicit couplings found**: Not yet assessed

## 5. Model/entity inventory

- **Manifest path**: `manifests/bookstack.json`
- **Models/entities found**: 37 (all Eloquent; 0 Doctrine; 0 Doctrine-XML)
- **Flagged for review**: 55 flags across the model set, breaking down as:
  - 37 × low-confidence base-class resolution ("medium" confidence — every model in this app
    resolved via short-name heuristic rather than a fully-qualified match; needs a quick human
    confirmation pass, but is not itself evidence of a wrong extraction)
  - 8 × query-scope logic (`scopeXxx()` methods — query-building logic with no declarative JPA
    equivalent, must be ported to explicit repository query methods)
  - 10 × other, including:
    - Unresolvable `morphTo()` relationship targets (e.g. `Reference::from()`/`to()`,
      `Watch::watchable()`) — polymorphic relationships needing manual target resolution
    - Custom accessor/mutator logic (e.g. `App\Model::getRawAttribute()`) requiring manual
      re-implementation, not a plain field passthrough
- **Unresolved base classes**: 0 (no `unresolvedBaseClasses` entries — the medium-confidence
  flags above are heuristic-match warnings, not resolution failures)

## 6. Gap inventory (app-specific)

| Construct | Location | Why it can't translate directly | Refactor target | Complexity |
|---|---|---|---|---|
| `scopeVisible()` and 7 other query scopes | Various models | Query-building logic, no declarative JPA equivalent | Explicit `@Query`/Specification methods on the Spring Data repository | moderate |
| `morphTo()` on `Reference::from/to`, `Watch::watchable` | `References\Reference`, `Activity\Models\Watch` | Polymorphic relationship target not statically resolvable | Confirm concrete target types by reading usage sites; model as a discriminator-based single-table or explicit interface + `@DiscriminatorColumn` in JPA | complex |
| `App\Model::getRawAttribute()` | `app/App/Model.php` | Accessor with custom logic, not a field passthrough | Read method body; re-implement equivalent transform as a plain method or computed getter | simple–moderate (pending read) |
| SAML SSO (`onelogin/php-saml`), OAuth via Socialite | Access/* | Framework-specific auth libraries | Spring Security SAML2 (`spring-security-saml2-service-provider`) + OAuth2 client | complex |
| TOTP 2FA (`pragmarx/google2fa`) | `Access/Mfa/*` | No direct Spring equivalent library assumed yet | Evaluate `dev.samstevens.totp` or similar; confirm algorithm/params match | moderate |
| PDF generation (`dompdf`, `knp-snappy`) | Various export flows | Different rendering engines | OpenHTMLtoPDF or Flying Saucer for dompdf-equivalent; confirm visual parity | moderate |

## 7. Complex workflow mappings needed

| Pattern | Present in this app? | Generic mapping applies? | Notes |
|---|---|---|---|
| Queues | Not yet assessed | — | Laravel queue usage not yet grepped |
| Scheduled tasks | Not yet assessed | — | Check `app/Console` for Artisan scheduled commands |
| Events | Not yet assessed | — | |
| Caching | Likely (predis/predis present) | Yes (Laravel cache → Spring Cache) | Confirm cache driver config |
| File storage | Yes (`league/flysystem-aws-s3-v3`) | Yes (Flysystem → Spring Cloud AWS S3 or `software.amazon.awssdk`) | |
| Email | Not yet assessed | — | |
| PDF generation | Yes (`dompdf`, `knp-snappy`) | Yes (see gap inventory) | |
| Search | Not yet assessed | — | |
| Sessions | Not yet assessed | — | Confirm session driver (Redis-backed via predis?) |
| External auth providers | Yes (SAML, OAuth via Socialite: Discord/GitLab/Azure/Okta/Twitch) | Yes | See gap inventory |
| Fine-grained permissions/ACL | Likely (documentation platform — page/book/chapter/shelf permissions are core to BookStack) | Needs dedicated mapping | Read `app/Permissions` before assuming a generic RBAC mapping suffices |
| Soft-delete-via-dedicated-entity | Uses Eloquent's built-in `SoftDeletes` trait, not a dedicated entity pattern | Standard mapping applies | |

## 8. Rulebook deltas

Not yet written — pending Phase 3 stress-test on 2-3 representative files.

## 9. Parity harness plan (Phase 1)

- **Test corpus source**: Not yet determined — no production traffic sample confirmed available;
  likely hand-authored scenarios per route, supplemented by the app's existing PHPUnit test suite
  (`phpunit.xml` present, `phpunit/phpunit ^11.5` in require-dev) as a source of known-good
  input/output pairs.
- **Coverage**: Not yet built
- **Judge validation status**: Not started — harness does not exist yet, so it has not been
  validated against deliberately broken PHP. Per Step 2 of the transformation, this must happen
  before any Java is written.

## 10. Skill feedback log

| Date | Pattern found | Skill file changed | What changed |
|---|---|---|---|
| | | | |

## 11. Status / phase tracker

| Phase | Status | Notes |
|---|---|---|
| 0 — Assess & discover | in progress | Composer/framework confirmed; codebase inventory partially filled |
| 1 — Build the judge | not started | |
| 2 — Rulebook + gap inventory | in progress | Seed gap inventory drafted above from manifest flags |
| 3 — Stress-test | not started | |
| 4 — Scaffold | not started | |
| 5 — Data model | not started | Manifest ready (`manifests/bookstack.json`) |
| 6 — I/O layer | not started | |
| 7 — Business logic | not started | |
| 8 — Compile/run/parity/cutover | not started | |

## 12. Open questions / decisions log

- 2026-07-26: Model-discovery scanner run against `apps/bookstack` — 37 Eloquent models, 55
  flags, 0 Doctrine/XML entities (expected for a pure-Laravel app). Manifest at
  `manifests/bookstack.json`.
