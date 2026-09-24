# PHP (Laravel/Symfony) to Java Spring Boot Migration — Discovery-Driven, Parity-First

## Objective
Migrate a Laravel (Eloquent) or Symfony (Doctrine — either PHP attribute or external XML
mapping) PHP application to Java 21 / Spring Boot with behavioral parity, by mechanically
discovering the model/entity layer before translating (never guessing at PHP structure from a
single read-through), and by proving equivalence against the original PHP application's real
behavior rather than assuming a structurally-similar Java port is a correct one.

## Summary
**Hard constraint, applies before anything else in this document: do not read, list, or dispatch
any exploration task against model/entity files (anything under an `Entity`/`Models` directory,
or any file containing `#[ORM\Entity]`, `class X extends Model`, or covered by a `*.orm.xml`
mapping) until the scanner in Step 1 has been run and its manifest produced.** This is not a
style preference — reading those files by hand is redundant work that the scanner already does
mechanically and more reliably, and skipping it re-introduces exactly the guessing this
transformation exists to eliminate. Controllers, routes, config, security, and other non-model
files may be read freely at any time; the constraint applies only to model/entity files.

The old PHP code is the spec; a parity harness diffing real request/response pairs (or
hand-authored scenarios where no traffic sample exists) against the PHP original is the referee.
This transformation runs a bundled, dependency-free PHP model-discovery scanner
(`document_references/scripts/discover-models.php`) first, to produce a JSON
manifest of every Eloquent model or Doctrine entity — found by walking the class inheritance
chain or reading `#[ORM\Entity]` attributes or `*.orm.xml` mapping files, never by assuming a
folder convention. Every PHP construct the scanner cannot resolve mechanically (a dynamic
property value, an unresolved relationship target, an accessor/mutator containing logic, a
magic method, a Doctrine relationship mapped to an interface) is flagged rather than guessed, and
gets refactored into an explicit Java contract during translation — never translated line-by-line.
The parity harness is built and validated (it must pass against the current PHP, and it must FAIL
against deliberately broken PHP) before any Java is written. Recurring mistakes found during
translation are fixed by amending the rulebook and regenerating the affected files, never by
hand-patching individual files against the rule.

## Entry Criteria
1. The application is written in PHP 8.0+ and uses Laravel or Symfony (confirm via `composer.json`:
   `laravel/framework` or `symfony/framework-bundle` in `require`).
2. A `composer.json` exists at the repository root (or is locatable) with a `php` version
   constraint.
3. If the app uses an ORM, it is Eloquent (Laravel) or Doctrine (Symfony) — either PHP-attribute
   mapping or external `*.orm.xml` mapping. Plain PHP apps with no ORM are still in scope for
   route/business-logic translation, but the model-discovery step will correctly report zero
   models — do not treat that as a scanner failure.
4. PHP 8.0+ must be available on the machine running the transformation (the bundled scanner only
   tokenizes source text and never executes the target app's code, so this is independent of the
   target app's own PHP version).
5. Ideally, a sample of real request/response traffic exists for the application (for the parity
   harness). If none exists, do not block — hand-author representative scenarios per route
   instead (see Step 2).

## Implementation Steps

1. **MANDATORY FIRST ACTION, before any other exploration of the codebase: detect the framework
   and run the bundled model-discovery scanner.** Do not dispatch a general "explore the
   codebase" task, and do not read the contents of any file under an Entity/Models directory (or
   any file containing `#[ORM\Entity]`, `class X extends Model`, or mapped via `*.orm.xml`)
   before this step is complete. It is fine — and expected, for later steps — to read
   controllers, routes, config, and security files at any time; only model/entity files are
   gated behind this step.

   a. Read `composer.json` to confirm the framework (`laravel/framework` vs
      `symfony/framework-bundle`), its version, and the `php` constraint. Do not assume
      framework/version from folder names — confirm from this manifest file.
   b. Run the scanner:
      ```
      php document_references/scripts/discover-models.php \
        --root=<path-to-app> --pretty --out=model-manifest.json
      ```
      This produces a manifest of every Eloquent model (via inheritance-chain walk) and every
      Doctrine entity (via `#[ORM\Entity]` attribute or `*.orm.xml` mapping file) — see
      `document_references/php-model-discovery/references/manifest-schema.md` for the full shape.
   c. Treat every non-empty `flags[]` entry on a model as a required-review item, not an edge
      case: it means the scanner found something it could not resolve mechanically (a dynamic
      property value, an unresolved relationship target, a magic accessor/mutator, a
      low-confidence base-class resolution, a Doctrine relationship targeting an interface). Read
      *that specific flagged file* by hand before deciding how to translate it — this is the one
      case where reading a model file directly, after the scanner has run, is expected.

2. **Build and validate the parity harness before writing any Java.** Categorize the PHP
   application's existing tests into externally-observable (HTTP request/response, CLI I/O) versus
   internals-dependent. Build the harness from real production/staging traffic samples if
   available; otherwise hand-author scenarios covering every route and critical workflow. The
   harness must run against both the PHP original and the eventual Java port on equal terms.
   **Validate the harness itself**: run it against the current, working PHP — it must pass. Then
   deliberately break the PHP (flip a comparison, drop a field) and run it again — it must fail. A
   harness that does not catch injected breakage is not a real referee and must not be trusted.

3. **Build the rulebook and gap inventory from the manifest, not from scratch.** Seed the
   rulebook from `document_references/php-to-java-migration/references/laravel-to-spring.md` or
   `symfony-to-spring.md` (framework idioms), `semantic-traps.md` (PHP behaviors that silently
   differ in Java: loose `==` comparison, `empty()`, integer overflow, `substr` negative offsets,
   `foreach` by reference, `usort` stability, `strtotime` leniency, string/number juggling, PCRE
   vs `java.util.regex`), and `composer-to-maven.md` (dependency mapping). For every flagged
   manifest item and for constructs like magic methods (`__get`/`__set`/`__call`), variable
   variables (`$$var`), `eval`/dynamic `include`, `mixed` types, and duck-typed arguments: these
   cannot be translated line-by-line — refactor each into an explicit Java contract (a declared
   interface, a typed field, an explicit dispatch map) and record the decision. Complex
   infrastructure patterns (queues, scheduled tasks, events, caching, webhooks, external auth
   providers, fine-grained permissions/ACL, soft-delete-via-dedicated-entity, Doctrine relationships
   mapped to an interface via `ResolveTargetEntityListener`, Gedmo extensions like Timestampable/
   Sluggable/Tree) get a dedicated mapping — check
   `document_references/php-to-java-migration/SKILL.md` §2c before improvising one.

4. **Stress-test the rulebook on 2-3 representative files before fanning out across the whole
   app.** Translate them strictly by the rulebook; separately, translate the same files freely (as
   a senior Java engineer would, ignoring the rulebook); diff the two and fold accepted gaps back
   into the rulebook. Discard both translations afterward — the goal is a hardened rulebook, not
   progress on real files yet.

5. **Scaffold the Spring Boot project** (Java 21, Spring Boot 3.x). Map `composer.json`
   dependencies to Maven/Gradle per the bundled `composer-to-maven.md` table, adding dependencies
   only as later steps actually need them.

6. **Translate the data model from the manifest, not from re-reading PHP source.** Each
   manifest entry maps to one JPA entity: `fillable`/`hidden`/`casts` (Eloquent) or
   `columns`/`id`/relationships (Doctrine) seed the field list and type mapping directly.
   Apply the type map (`string→String`, `int→long` with overflow awareness, money casts→
   `BigDecimal` with no exceptions, `array`(list)→`List<T>`, `array`(hash)→`LinkedHashMap<K,V>`
   preserving insertion order, dates→`java.time.*`). Any cast/column type with no mapping in the
   manifest (`javaType` starting with `FLAG:`) needs a rulebook decision, not a guess.

7. **Translate the I/O layer.** Routes → `@RestController`, preserving URL paths, HTTP methods,
   and status codes exactly (the parity harness depends on this). Middleware →
   `OncePerRequestFilter`/`HandlerInterceptor`. Form/request validation → `@Valid` DTOs. Auth
   (Laravel guards / Symfony security) → Spring Security, keeping session/token format compatible
   during any strangler-fig cutover period.

8. **Convert business logic in batches, checking every semantic-trap table row against the
   code being translated.** Where a translation is not confident, add a `// TODO(port): <reason>`
   marker rather than guessing — the compiler and the parity harness will surface these for
   follow-up, and a marker is always safer than a silent wrong translation.

9. **Compile, run, and prove parity — fix the loop, not the code.** Run the parity harness from
    Step 2 against both PHP and Java for every translated module; a 100% match or an explicitly
    logged, approved deviation is required per module. When one mistake repeats across several
    files, amend the rulebook (Step 3) and regenerate every affected file — never hand-patch files
    individually against a rule that is now known to be wrong.

10. **Cut over.** Strangler-fig (route-by-route behind a proxy) is the default for any
    production system; big-bang is only appropriate for small (roughly under 20k LOC), low-traffic
    applications with adequate existing test coverage. Keep the PHP application available for
    rollback for at least one release cycle after cutover.

## Validation / Exit Criteria
1. The model-discovery manifest was generated and every `flags[]` entry was reviewed by a human
   before its corresponding entity was translated — not skipped because the manifest "looked
   clean."
2. The parity harness exists, was validated to pass against working PHP and fail against
   deliberately broken PHP, and was actually run against the final Java port — not merely built
   and left unused.
3. Every gap-inventory item (magic methods, variable variables, `eval`, `mixed` types, duck
   typing, interface-targeted Doctrine relationships) was resolved to an explicit, documented Java
   contract — none were translated line-by-line or silently dropped.
4. All routes present in the PHP application have a corresponding Spring MVC endpoint with the
   same path, HTTP method, and status codes; a route-inventory diff between PHP and Java is empty.
5. Every field mapped from a money-related cast/column uses `BigDecimal` — no exceptions.
6. Parity harness result: 100% match per module, or every mismatch is explicitly logged as an
   accepted, approved behavior change (not silently ignored).
7. No `// TODO(port)` markers remain unresolved in code intended for production cutover.
8. The Spring Boot application compiles and starts successfully, and serves the same routes the
   PHP application did.
9. A cutover mechanism (strangler-fig or big-bang) was explicitly chosen and justified based on
   the application's size, traffic, and existing test coverage — not defaulted to without
   consideration.
