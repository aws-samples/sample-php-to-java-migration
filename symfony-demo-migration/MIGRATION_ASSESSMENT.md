# MIGRATION_ASSESSMENT.md — Symfony Demo → Java/Spring Boot

Phase 0 deliverable of the PHP → Java/Spring Boot migration skill.
Source: `symfony-demo-migration/source-php/` (cloned from https://github.com/symfony/demo).

_Satisfies the skill's Phase 0 gate: inventory, explicit + implicit dependency graph,
fan-in ranking, and cutover mechanism — for user approval before Phase 1._

## 1. Inventory

| Metric | Value |
|---|---|
| PHP files (excl. vendor) | **51** |
| PHP LOC (excl. vendor) | **~4,625** |
| Twig templates | **32** |
| HTTP routes (`#[Route]`) | **18** |
| Framework | **Symfony 8.x** (`symfony/framework-bundle ^8`; `extra.symfony.require: 8.1.*`) |
| PHP version | **>= 8.4** |
| Database | **SQLite** via `ext-pdo_sqlite` (Doctrine DBAL 4 / ORM 3.5) |
| ORM | **Doctrine ORM 3.5** (attribute-mapped entities) |
| Templating | **Twig** (+ twig/intl-extra, markdown-extra) |
| Frontend | AssetMapper + Stimulus + Bootstrap 5 + Sass (server-rendered; no SPA) |

**Read of the codebase:** a well-structured, **strongly-typed modern Symfony app** — attribute
routing, typed properties, PHP 8 attributes throughout. This is close to a best-case migration
source.

### Application domain (a blog + admin)
- **Entities (4):** `Post`, `Comment`, `Tag`, `User`
- **Controllers:** `BlogController`, `SecurityController`, `UserController`, `Controller/Admin/*` (blog admin CRUD)
- **Repositories (3):** `PostRepository`, `TagRepository`, `UserRepository`
- **Forms (4):** `PostType`, `CommentType`, `UserType`, `ChangePasswordType` (+ a DataTransformer, custom Type)
- **Security:** form login + `PostVoter` (authorization), role-based admin
- **Console commands (3):** `AddUserCommand`, `DeleteUserCommand`, `ListUsersCommand`
- **Event subscribers (4):** comment notification (email), locale redirect, controller checks, requirements check
- **Cross-cutting:** `Paginator`, `Validator` util, two Twig extensions (`AppExtension`, `SourceCodeExtension`), Markdown rendering (league/commonmark), i18n/translations, UX live component(s)

## 2. Dynamic-typing & implicit-coupling hotspots

The risky, static-analysis-defeating constructs the skill greps for are **effectively absent**:

| Construct | Hits | Note |
|---|---|---|
| `__get/__set/__call`, `$$var`, `extract()`, `compact()`, `eval()`, `call_user_func*` | **0** | None in `src/` |
| `$_SESSION` / `$_REQUEST` / `$GLOBALS` superglobals | **0** | Framework-managed (Session, Request objects) |
| `mixed` | 1 | Single occurrence; benign (typed elsewhere) |

**Implicit couplings to preserve deliberately** (not code-reference visible):
- **Doctrine metadata & migrations** — entity attributes + schema; the Java side maps to JPA entities + Flyway with the same table shape.
- **Security context** — `PostVoter` + role hierarchy couple controllers to the authenticated user; map to Spring Security `@PreAuthorize` / `AuthorizationManager`.
- **Event dispatch** — `CommentCreatedEvent` → `CommentNotificationSubscriber` (sends email) is an implicit runtime link; map to Spring `ApplicationEventPublisher` + `@EventListener`.
- **Locale** — `RedirectToPreferredLocaleSubscriber` couples every request to locale negotiation; map to a Spring `LocaleResolver` + interceptor.
- **Fixtures/seed data** — `AppFixtures` seeds the demo DB; map to a Flyway seed or a dev-profile seeder for parity data.

## 3. Fan-in ranking (leaf-first migration order)

| Tier | Modules | Rationale |
|---|---|---|
| Leaf (first) | `Entity/*`, `Pagination/Paginator`, `Utils/Validator`, Twig extensions | zero internal dependents' deps |
| Mid | `Repository/*`, `Security/PostVoter`, `Form/*`, `Event*` | depend on entities |
| High fan-in (last) | `Controller/*`, `Controller/Admin/*`, `Command/*` | orchestrate everything above |

## 4. Framework mapping (Symfony → Spring) — headline items

Detailed rules will be written in Phase 2 (`references/symfony-to-spring.md` seeds this):

| Symfony | Spring |
|---|---|
| `#[Route]` controllers | `@Controller` / `@GetMapping` etc. (preserve exact paths) |
| Doctrine ORM entities + repositories | Spring Data JPA `@Entity` + repositories |
| Twig templates | Thymeleaf |
| Security bundle (form login, `PostVoter`, roles) | Spring Security (form login, method security / `AuthorizationManager`) |
| Form + Validator | Spring MVC form binding + Jakarta Bean Validation |
| Console commands | Spring `CommandLineRunner` / Picocli, or `@ShellComponent` |
| EventSubscriber + custom Events | `ApplicationEventPublisher` + `@EventListener` |
| Mailer (comment notifications) | Spring Mail + Thymeleaf email templates |
| Translations / i18n | Spring `MessageSource` + `LocaleResolver` |
| league/commonmark (Markdown) | CommonMark Java (`org.commonmark`) |
| AssetMapper/Stimulus/Bootstrap/Sass | Serve compiled static assets (frontend out of scope for parity; keep rendered HTML equivalent) |

## 5. Cutover mechanism — recommendation

**Big-bang rewrite.** Rationale (maps to the skill's decision criteria):
- **Size:** ~4.6k LOC, 51 files — far under the ~20k big-bang threshold.
- **Risk/traffic:** demo app, no production traffic; no need for route-by-route strangler.
- **Tests exist:** the repo ships a PHPUnit suite (functional + unit) under `tests/`, which seeds the Phase 1 parity harness.
- **Target:** Java 21 + Spring Boot 3.2+, structure-preserving (Java packages mirror `App\` namespace), refactor only after parity.

## 6. Proposed next steps (phases after this gate)

1. **Phase 1 — Build the judge:** stand up the PHP app (SQLite), categorize the PHPUnit tests into portable (HTTP/functional) vs internal, and build a parity harness that replays the blog + admin + auth flows against both PHP and Java. Validate it catches deliberately broken code.
2. **Phase 2 — Rulebook + gap inventory:** finalize Symfony→Spring rules (load `references/symfony-to-spring.md`), Doctrine→JPA, Twig→Thymeleaf, and the (small) gap inventory.
3. **Phase 3 — Stress-test** the rulebook on ~3 representative files (an entity, a repository, a controller).
4. **Phases 4–8** — scaffold, translate data model → I/O → business logic, then compile/run/prove parity/cut over.

## 7. Note on skill updates

At the assessment stage there is **nothing to change in the shared skill** — it already covers
Symfony detection and mapping. Skill refinements (new rules) are expected to emerge in **Phase 2/3**
when real Symfony idioms are stress-tested, and per the skill's "fix the loop, not the code"
principle they'll be folded into `.kiro/skills/php-to-java-migration/` (and
`references/symfony-to-spring.md`) so all future projects benefit.

---

**GATE:** Approve this assessment and the big-bang strategy to proceed to **Phase 1 (build the
parity harness)**, or request changes.
