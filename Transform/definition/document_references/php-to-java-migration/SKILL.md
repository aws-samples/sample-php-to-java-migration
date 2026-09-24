---
name: php-to-java-migration
description: Migrate PHP codebases to Java/Spring Boot with behavioral parity using an agentic, loop-driven process. Use whenever the user asks to convert, port, translate, rewrite, or migrate PHP code (plain PHP, Laravel, Symfony, CodeIgniter) to Java, Spring Boot, or the JVM — single files, whole services, migration planning, PHP-to-Java type mapping, Composer-to-Maven dependency mapping, or parity testing between PHP and Java implementations. Trigger even on indirect phrasing: "get off PHP", "rewrite this service in Java", "modernize our PHP stack to Spring Boot".
---

# PHP to Java Migration

Convert PHP codebases to Java + Spring Boot. Preserve behavior first. Modernize second. Prove parity before cutover.

The old PHP code is the spec. The test corpus is the referee. Your job is to build a translation *process* that grinds against that referee until parity holds — not to hand-translate files one by one.

## Core principles

These govern every phase below. When a phase conflicts with a principle, the principle wins.

1. **Fix the loop, not the code.** When a mistake shows up across many files, you do not patch each file. You amend the rule that produced it and regenerate the affected files. Code is never hand-patched against the rulebook.
2. **Build the judge before you translate.** A migration with no exit condition never ends. The parity harness (the "judge") is a prerequisite, not a final phase. It must run against *both* the PHP original and the Java port on equal terms. Validate it against deliberately broken code — a judge that does not catch breakage is not a judge.
3. **Verification is mechanical; review is adversarial.** Let scripts be the referee — the compiler, a JSON diff, the test suite. Use separate reviewers with fresh context to hunt for divergence, and send reviewer disagreement to a third opinion. Objective ground truth lets the process run for a long time without a human arbitrating quality.
4. **The queue is mechanical and resumable.** "Done" means the output file exists on disk and passes its gate. Rebuild the work queue from disk every run so the migration is resumable by construction and interruptions cost nothing.
5. **Parallelize, and right-size the effort.** Translation fans out across independent files — delegate high-volume implementation to subagents. Reserve your most careful effort for writing rules and for adversarial review, because a rule error propagates to every file that follows.
6. **Front-load the human hours.** The rulebook and the stress test are where judgment matters most and where time should go. Everything after is mostly queues burning down.
7. **Review loop *results*, not individual failures.** Individual failures are the loop's job. Your attention belongs on patterns: a failure repeating across files is a missing rule.

## Target Configuration

Before starting, confirm the following with the user. Store selections in `MIGRATION_CONFIG.md` at the project root.

### Java Version (minimum: 21)

| Java Version | Spring Boot | Key Features Available |
|---|---|---|
| 21 (LTS) | 3.2+ | Virtual threads, pattern matching, records, sealed classes |
| 22 | 3.3+ | Unnamed variables, FFM API (preview) |
| 23+ | 3.4+ | Markdown doc comments, primitive patterns (preview) |

Ask the user: "Which Java version? (minimum 21)" — accept 21, 22, 23, or latest.

### Spring Boot Version

Auto-select based on Java version, or let the user override:

| Java Version | Default Spring Boot | Minimum Spring Boot |
|---|---|---|
| 21 | 3.2.x | 3.2.0 |
| 22 | 3.3.x | 3.3.0 |
| 23+ | 3.4.x | 3.4.0 |

### Migration Strategy (pick one — from the four modernization approaches)

A language rewrite is one of four modernization paths. Name the choice explicitly so scope is clear:

- **Rewrite (this skill's default)** — PHP → Java/Spring Boot, preserving business logic. Highest payoff; the process below exists to de-risk it.
- **Rehost** — move PHP unchanged onto cheaper infra. Out of scope; the maintainability problem remains.
- **Refactor** — restructure PHP without changing language. Out of scope.
- **Replace** — swap for a commercial product. Out of scope.

Within a rewrite, choose *how* the target is structured — this decides the shape of the rulebook (Phase 2):

- **Structure-preserving** (default): Java packages mirror PHP namespaces. Files translate roughly one-to-one, so line-by-line diffing works and the stress test in Phase 3 is meaningful. Refactor the package layout only after parity passes.
- **Redesign**: target architecture differs from source. The rulebook becomes a design document instead of lookup tables, and Phase 3 shifts from diffing translations to attacking the design with adversarial reviewers plus a disposable end-to-end run.

### Source Framework Detection

Auto-detect the PHP source framework and version. Confirm with user.

| Indicator | Framework | Version Detection |
|---|---|---|
| `artisan` + `composer.json` has `laravel/framework` | Laravel | Read version constraint from `composer.json` |
| `symfony.lock` or `composer.json` has `symfony/framework-bundle` | Symfony | Read `symfony.lock` or constraint |
| `system/core/CodeIgniter.php` or `spark` CLI | CodeIgniter | Read `CI_VERSION` constant or `composer.json` |
| None of the above | Plain PHP | Check `composer.json` for `php` version constraint |

#### Framework-Version-Specific Handling

**Laravel versions:**
- Laravel 8–9: Class-based route model binding, middleware groups → map to Spring interceptor chains
- Laravel 10: Typed properties, invokable rules → map to Jakarta Validation + custom validators
- Laravel 11+: Slim skeleton, health routes, per-second scheduling → map to Spring Actuator + `@Scheduled`

**Symfony versions:**
- Symfony 5: Service autowiring, Messenger component → map to Spring DI + Spring Integration
- Symfony 6: Enums in routing, fiber-friendly services → map to Spring MVC + virtual threads
- Symfony 7+: MapQueryParameter, lazy objects → map to `@RequestParam` + `@Lazy`

**CodeIgniter versions:**
- CodeIgniter 3: Legacy MVC, `$this->load` → manual wiring to Spring components
- CodeIgniter 4: Namespace-based, Entities separate from Models → closer Spring Data JPA mapping

## Workflow: eight phases, in order

Run phases sequentially. Produce the named deliverable per phase. Stop at each gate for user confirmation. Phases 0–3 are front-loaded human judgment; Phases 4–7 are loops that mostly run themselves.

### Phase 0 — Assess and discover

Traditional analysis maps explicit call graphs. That is not enough. The expensive risk lives in *implicit* couplings that static analysis misses. Surface them here.

1. Inventory the codebase:
   - `find . -name "*.php" -not -path "*/vendor/*" | wc -l` and total LOC
   - Detect framework (see table above); record PHP version and extension deps (`ext-*` in `composer.json`)
2. Map dependencies — both explicit and implicit:
   - **Explicit**: namespace imports, `use` statements, Composer requires. Have a subagent produce a deterministic script that emits a file-level dependency graph; review-and-fix the script until the graph is trustworthy.
   - **Implicit** (the risky ones): shared global state (`$GLOBALS`, `static` properties, singletons), superglobals (`$_SESSION`, `$_REQUEST`), files/DB tables that couple modules without a code reference, and side-effecting `include`/`require` and bootstrap sequences that set runtime state. Record each as a coupling to preserve or break deliberately.
3. Rank modules by fan-in. Leaf modules (zero internal dependents' dependencies) migrate first; high-fan-in modules last.
4. Choose the cutover mechanism at the gate:
   - **Big-bang rewrite**: codebase under ~20k LOC, low traffic, tests exist.
   - **Strangler fig**: route-by-route cutover behind a proxy. Default for production systems.

**Deliverable**: `MIGRATION_ASSESSMENT.md` — inventory, explicit + implicit dependency graph, fan-in ranking, cutover mechanism. **Gate**: user approves.

### Phase 1 — Build the judge (parity harness)

Do this before writing a line of Java. This is the exit condition for the entire project.

1. Categorize the existing PHP tests. Have a subagent split them into: (a) tests expressible as external calls (HTTP request/response, CLI in/out, public API) that can run against both codebases, and (b) tests that depend on PHP internals and will not port.
2. Build the harness from external-facing behavior:
   - **If a request corpus is available**: record N real request/response pairs per route from production or staging. Scrub PII. This is the highest-fidelity referee.
   - **If no corpus exists**: do not block. Author a set of real-world scenarios (Mike's Python→TS port used seven) that exercise each route and critical workflow, and diff Java output against PHP output. Treat any behavior change as a bug.
   - Diff semantics: JSON-aware structural diff, float epsilon compare, timestamp/UUID normalization, header allowlist. Encode these normalizations explicitly — they are part of the referee's definition.
3. **Validate the judge.** Run it against the current PHP code: it must pass. Then run it against deliberately broken PHP (flip a comparison, drop a field): it must fail. A judge that does not catch breakage is worthless.
4. Have adversarial reviewers confirm the ported/rewritten assertions did not weaken relative to the originals.

**Deliverable**: `PARITY_HARNESS/` (runnable against PHP and Java) + `parity-report.md` template. **Gate**: judge passes on PHP, fails on broken PHP.

### Phase 2 — Write the rulebook, dependency map, and gap inventory

Order matters: **rulebook first, then gap inventory.** The gap inventory is defined by what the rulebook's defaults do not cover. Audit the two together.

#### 2a. Rulebook

The rulebook is the single source of truth for how PHP idioms become Java. Every translation agent follows it; when it is wrong, you fix it here, not in the code. For a structure-preserving migration it is mostly lookup tables; for a redesign it is a design document.

Seed it from the tables in this skill:
- **Type map** (Phase 5 table) — PHP scalar/array/date types → Java types, with the coercion rule per row.
- **Semantic-trap table** (Phase 6 table) — PHP behaviors that silently differ in Java, with the required Java rule per row.
- **Composer → Maven map** (Phase 4 table) — library replacements.
- **Framework idiom maps** — see `references/laravel-to-spring.md`, `references/symfony-to-spring.md`.

For every area of ambiguity, chat with the user to form an explicit policy and write it down. The rulebook grows throughout Phases 4–7: whenever a reviewer catches the same mistake twice, add one sentence to the rulebook and regenerate the affected batch.

#### 2b. Gap inventory — the refactor-not-translate list

The fundamental gap between PHP and Java is **static typing and explicit contracts**. PHP accepts dynamic shapes Java's compiler will not. These constructs cannot be translated line-by-line; they must be *refactored* into a contract before the code will compile. Inventory every occurrence.

Grep for and record each hit as a manual-refactor item:

| PHP construct | Detection | Why it can't just translate | Refactor target |
|---|---|---|---|
| Magic methods | `__get`, `__set`, `__call`, `__callStatic` | No compile-time member; dispatch is runtime | Explicit methods/fields, or a `Map` facade with a typed interface |
| Variable variables | `$$var` | No such construct on JVM | Refactor to explicit `Map<String,Object>` access |
| Dynamic eval/include | `eval(`, `include $x`, `require $x` | No dynamic code loading | Resolve to a fixed dispatch table or strategy pattern |
| `extract()` / `compact()` | `extract(`, `compact(` | Injects/collects local vars by name | Explicit locals or a DTO |
| `call_user_func*` | `call_user_func`, `call_user_func_array` | Runtime callable dispatch | Method reference, functional interface, or strategy map |
| `mixed` params/returns | `mixed`, untyped params | No contract for the compiler | Declare the actual accepted shape as an interface/sealed type |
| Dynamic property creation | assignment to undeclared `$obj->x` | No implicit fields on JVM | Declare fields, or model as `Map` |
| Duck-typed arguments | function used with any object exposing method X | Java needs a declared interface | Extract an interface, implement it on each caller |
| Doctrine relationship mapped to an interface (`ResolveTargetEntityListener` pattern — common in Symfony apps built as reusable Components, e.g. Sylius) | `target-entity`/`targetEntity` value ends in `Interface`, paired with a `doctrine.orm.resolve_target_entities` service config entry | Java/JPA relationships need a concrete target type; there's no runtime interface-to-impl resolution for a field's declared type | Resolve the interface to its configured concrete class (check the app's `services.xml`/`doctrine.yaml`) *before* generating the JPA relationship annotation — never map the relationship to the interface itself |

For each gap item: document the PHP location, propose the Java contract, and rate complexity — **simple** (mechanical), **moderate** (logic rewrite), **complex** (architecture change). Complex items get their own design note before Phase 4.

#### 2c. Complex workflow mappings

Scan for infrastructure patterns that need a dedicated mapping (never inline-convert these):

| PHP Pattern | Detection Signal | Java Equivalent | Complexity |
|---|---|---|---|
| Queue jobs | Laravel: `implements ShouldQueue`, `dispatch()`. Symfony: Messenger handlers. | Spring `@Async` + `@RabbitListener` or `@SqsListener` | moderate |
| Scheduled tasks | Laravel: `Kernel::schedule()`. Symfony: `#[AsCronTask]`. Cron entries. | Spring `@Scheduled` or Quartz | simple |
| Event/listener system | Laravel: `Event::dispatch`. Symfony: `EventDispatcherInterface`. | `ApplicationEventPublisher` + `@EventListener` | moderate |
| Caching | Laravel: `Cache::remember()`. Symfony: `CacheInterface`. | Spring `@Cacheable` / `@CacheEvict` + provider | simple |
| File storage | Flysystem adapters, S3 config, local disk. | Spring `Resource` + AWS SDK v2 S3 | moderate |
| Email/notifications | Laravel: `Mailable`, `Notification`. Symfony: Mailer. `mail()`. | Spring Mail + Thymeleaf | moderate |
| WebSocket/broadcasting | Laravel Broadcasting, Pusher/Redis driver. | Spring WebSocket + STOMP | complex |
| PDF generation | dompdf, wkhtmltopdf, TCPDF. | OpenPDF or iText | moderate |
| Search | Laravel Scout, Elasticsearch client. | Spring Data Elasticsearch | moderate |
| Rate limiting | Laravel `RateLimiter`. Symfony `#[RateLimit]`. | Bucket4j or Resilience4j | moderate |
| Session management | PHP native sessions, Redis session driver. | Spring Session + Redis | moderate |
| Background workers | Laravel Horizon, Symfony Messenger workers. | Spring Boot + virtual-thread executor | complex |
| Webhooks (outbound) | A dedicated model/table tracking registered webhook URLs + delivery/event log (e.g. Laravel apps with a `Webhook`/`WebhookTrackedEvent`-style pair). Not the same as inbound routes. | Spring `RestClient`/`WebClient` dispatch from an `ApplicationEventListener`, with a JPA-backed delivery log entity mirroring the source table | moderate |
| External auth providers (LDAP/SAML/OIDC) | Dedicated service classes per provider (e.g. `LdapService`, `Saml2Service`, `OidcService`) alongside standard session auth. | Spring Security's LDAP, SAML2, and OAuth2/OIDC starters — one filter chain per provider, not a hand-rolled equivalent | complex |
| Fine-grained/inherited permissions (ACL) | Permission resolution that walks an entity hierarchy plus role/user grants at query time (not a static field or simple role check) — e.g. Laravel apps with a "joint permission" builder that precomputes effective access per entity. | Spring Security `PermissionEvaluator` + a materialized permissions table mirroring the source's precomputed-join approach; do not translate the query-time resolution logic line-by-line, redesign it as an explicit authorization service | complex |
| Soft delete via a dedicated entity (not a `deleted_at` column) | A separate polymorphic table/model recording deletions across multiple entity types (a later-stage refactor some apps adopt instead of Eloquent's default `SoftDeletes` trait). Detection: a model with fields like `deletable_type`/`deletable_id` plus deletion metadata, and an app-wide migration that dropped `deleted_at` columns in favor of it. | A generic `Deletion` JPA entity + a shared soft-delete service the other entities call — never a per-entity `@SQLDelete`/`@Where` annotation, which would silently reintroduce the column-based pattern the source app deliberately moved away from | complex |
| Automatic auditing fields via a Doctrine extension library (Gedmo: `Timestampable`, `Sluggable`, `Blameable`, `SoftDeleteable`, `Translatable`, `Loggable`) | XML mapping or attribute referencing a `gedmo:` namespace/extension attribute (e.g. `<gedmo:timestampable on="create"/>`), or `composer.json` requiring `stof/doctrine-extensions-bundle` | `Timestampable`/`Blameable` → Spring Data JPA auditing (`@CreatedDate`/`@LastModifiedDate`/`@CreatedBy` + `AuditingEntityListener`); `Sluggable` → a `@PrePersist`/`@PreUpdate` listener generating the slug explicitly; `Loggable` → Hibernate Envers; `Translatable` → a separate translations table modeled as its own JPA entity, never a dynamic-locale field | moderate |

**Deliverable**: `RULEBOOK.md`, `GAP_INVENTORY.md`, `WORKFLOW_MAPPINGS.md`, and the dependency map from Phase 0 finalized. **Gate**: user approves the rulebook and gap inventory together (joint audit).

> **Table maintenance**: this table is a living seed, not a fixed spec. When an app surfaces an
> infrastructure pattern not listed here, add a row (with detection signal, mapping, and
> complexity) rather than solving it one-off in that app's `RULEBOOK.md` — the next app with the
> same pattern should get it for free. Log the addition in that app's `MIGRATION_SPEC.md` §10
> (see `references/app-spec-template.md`), citing which app surfaced it.

### Phase 3 — Stress-test the rulebook (shakedown cruise)

Never fan out across the whole codebase on an unproven rulebook. Prove it on a tiny sample first, then throw the sample away.

For a **structure-preserving** migration:
1. Have one subagent translate three representative files strictly by the rulebook.
2. Have a second subagent translate the same three files "like a senior Java/Spring engineer," ignoring the rulebook.
3. Have a third subagent diff the two and propose new rules that close the gaps.
4. Fold accepted rules into `RULEBOOK.md`. **Discard all translated files.** The goal is to refine rules, not make progress.

For a **redesign** migration: attack the design document directly with adversarial reviewers, then validate it with one disposable end-to-end run. Discard the run.

Pick files that exercise the trickiest gap-inventory items and at least one complex workflow. Catching two bad rules here saves fixing them across hundreds of files later.

**Deliverable**: hardened `RULEBOOK.md`. **Gate**: user confirms rules are ready to fan out.

### Phase 4 — Scaffold target project and map dependencies

1. Generate the Spring Boot project using the configured Java + Spring Boot versions. Package root mirrors the PHP namespace root for traceability (structure-preserving). Refactor packages after parity, not before.
2. Map dependencies Composer → Maven. Add only what the current phase needs — never pre-load every dependency.

| Composer package | Java replacement | Version notes |
|---|---|---|
| guzzlehttp/guzzle | Spring `RestClient` (3.2+) or `WebClient` | `RestClient` for sync; `WebClient` for reactive |
| monolog/monolog | SLF4J + Logback | — |
| phpunit/phpunit | JUnit 5 + AssertJ | — |
| doctrine/orm, Eloquent | Spring Data JPA | — |
| symfony/validator, Laravel validation | Jakarta Bean Validation (3.0+) | `jakarta.validation` namespace (not `javax`) |
| league/flysystem | Spring `Resource` abstraction | — |
| firebase/php-jwt | jjwt 0.12+ | — |
| ramsey/uuid | java.util.UUID | — |
| nesbot/carbon | java.time | — |
| vlucas/phpdotenv | application.yml + `@ConfigurationProperties` | — |
| predis/predis, phpredis | Spring Data Redis + Lettuce | — |
| php-amqplib/php-amqplib | Spring AMQP | — |
| aws/aws-sdk-php | AWS SDK for Java v2 | — |
| symfony/cache, Laravel Cache | Spring Cache + provider (Redis/Caffeine) | — |

3. List unmapped packages in `DEPENDENCY_GAPS.md`. Resolve before business-logic translation.

**Deliverable**: compiling empty project + `DEPENDENCY_GAPS.md`.

### Phase 5 — Translate the data model

1. Classify each PHP data shape:
   - Stable-shape associative array or data class → Java `record`
   - Dynamic keys → `Map<String, Object>` + gap-inventory review flag
   - Eloquent/Doctrine entity → JPA `@Entity`
2. Apply the type map (this table seeds the rulebook):

| PHP | Java | Rule |
|---|---|---|
| string | String | `mb_*` usage → verify UTF-8 handling |
| int | long | PHP ints overflow to float silently — add range checks |
| float (money) | BigDecimal | never double for currency |
| float (other) | double | epsilon compare in tests |
| bool | boolean | — |
| array (list) | `List<T>` | — |
| array (hash) | `LinkedHashMap<K,V>` | PHP arrays preserve insertion order |
| nullable param | `Optional<T>` / `@Nullable` | audit every implicit null |
| DateTime / Carbon | java.time.* | flag every `strtotime()` — lenient parsing |

3. Database migrations → Flyway. Keep schema unchanged during migration. Refactor schema after cutover.

**Deliverable**: entities/records + Flyway baseline. Project compiles.

### Phase 6 — Translate the I/O layer

1. Routes → `@RestController`. Preserve URL paths, HTTP methods, and status codes exactly — the judge depends on this.
2. Middleware → `OncePerRequestFilter` / `HandlerInterceptor`. Exception handlers → `@ControllerAdvice`.
3. FormRequest / Symfony constraints → `@Valid` DTOs.
4. Auth: Laravel guards / Symfony security → Spring Security. Keep session/token format compatible during the strangler period.
5. `.env` → `application.yml`. Secrets stay external.
6. Any JPA `@OneToMany`/`@ManyToMany` reachable from a serialized REST response needs an explicit
   fetch/serialization decision *now*, not a discovery at runtime: PHP/Eloquent and Doctrine both
   lazy-load transparently within a request; JPA's default `LAZY` collections throw
   `LazyInitializationException` the moment Jackson tries to serialize them outside the
   persistence context (the default with `open-in-view: false`, which is itself the correct
   setting — don't "fix" this by turning `open-in-view` back on). Decide per relationship: `EAGER`
   only for genuinely small, always-needed collections; a DTO projection or `@EntityGraph`/fetch-
   join query for anything else. Found the hard way while building Sylius's pilot slice — a
   `@ManyToMany` that compiled cleanly still 500'd on first real request.

**Deliverable**: all routes respond (501 stubs allowed). Route inventory diff vs PHP = empty.

### Phase 7 — Convert business logic (the implement / review / fix loop)

This is the fan-out phase. Do not translate file-by-file in a single thread. Run a loop.

**Queue** (mechanical, resumable): a script slices pending classes into batches, ordered leaf-first by the dependency map. "Pending" = the target `.java` file does not yet exist on disk. Because the queue rebuilds from disk each run, the migration resumes cleanly after any interruption.

**Implement**: fan out batches to implementer subagents. Each agent translates its class strictly by `RULEBOOK.md`. Anything it cannot translate confidently gets a `// TODO(port): <reason>` marker rather than a guess — the compiler and tests will enumerate these later. Do not let agents over-refactor; the rulebook is the law.

**Where the compiler sits**: for fast per-unit checks (single class compiles in seconds) run the compiler inside the loop. If your build is slow, defer compilation to Phase 8 and keep the loop pure translation. Choose one and state it.

**Review** (adversarial): two reviewer subagents with separate contexts check each batch against `RULEBOOK.md` and cite the specific rule behind every finding. Disagreement between the two goes to a third agent. A finding that cites a rule becomes a queue item; a finding with no rule behind it means the rulebook has a gap.

**Fix upstream, not per-file**: when a reviewer catches the same mistake across multiple files, do **not** patch each file. Add one sentence to `RULEBOOK.md` and regenerate the affected batch. The code never gets hand-patched against the rulebook.

**Capture behavior, including bugs**: write JUnit tests that capture *observed* PHP behavior, bug-for-bug. Log every intentional deviation in `BEHAVIOR_CHANGES.md`. Check every class against the semantic-trap table (this table seeds the rulebook):

| PHP behavior | Trap | Java rule |
|---|---|---|
| `==` loose comparison | `"0" == false` is true | use `equals()`; replicate coercion only where tests demand it |
| `empty()` | `empty("0")` is true | translate the exact predicate, not "is null" |
| `/` division | int/int returns float | match with double division, or `intdiv` semantics explicitly |
| int overflow | silently becomes float | `Math.addExact` or BigInteger at flagged sites |
| `substr($s, -3)` | negative offsets legal | `s.substring(s.length()-3)` with bounds guard |
| `foreach` by `&` reference | mutates source array | rewrite with iterator; verify with test |
| `usort` | stability is PHP-version-dependent | `List.sort`; assert ordering expectations in tests |
| `strtotime` | accepts near-anything | strict `DateTimeFormatter`; enumerate accepted formats from logs |
| string↔number juggling | `"10" + 5` works | parse explicitly; test malformed-input paths |
| PCRE regex | syntax deltas vs java.util.regex | run each pattern through both engines in tests |

**Deliverable**: all classes converted, `TODO(port)` list drained or tracked, unit suite green, `BEHAVIOR_CHANGES.md` current, `RULEBOOK.md` reflecting every rule learned.

### Phase 8 — Compile, run, prove parity, cut over

These steps share the loop architecture and need progressively less human judgment.

1. **Compile**: run the build once across the workspace. Fixer agents burn down the compiler error list in parallel with adversarial review. Review the error list for *systemic* issues (e.g., a whole category of cyclic-import errors) and fix them by amending the loop/rulebook, not one error at a time.
2. **Smoke run**: start the app; drive basic traffic. Group crashes by root cause; adversarial reviewers check each fix.
3. **Serialize expensive rebuilds with a build daemon**: one process owns rebuilding the artifact and re-running affected tests. Fixer agents write patches; the daemon batches them, rebuilds once, and feeds results back. This prevents many agents from each triggering a costly rebuild.
4. **Prove parity**: run the Phase 1 judge — replay the corpus/scenarios against PHP and Java, diff results. Parity gate per module: 100% match, or every mismatch logged as accepted in `BEHAVIOR_CHANGES.md`. When one failure repeats across many tests, fix it upstream in the rulebook and regenerate the touched files.
5. **Extend the referee if it is thin**: if the corpus is small, have Claude design an end-to-end suite and run it autonomously overnight, fixing what breaks and re-running for several nights. This surfaces the paper cuts a hand-written scenario list misses. The PHP codebase is always the ground truth.
6. **Cut over**: one route/module at a time (strangler) or flip the proxy (big-bang). Keep PHP hot for rollback for one release cycle.

**Deliverable**: parity report per module + cutover checklist.

## Rules

- Behavior first. No refactors, renames, or improvements until parity passes.
- Fix the loop, not the code. Recurring mistakes get a rulebook amendment + batch regeneration, never per-file hand-patches.
- Build and validate the judge (Phase 1) before writing Java. No exit condition, no start.
- Verification mechanical, review adversarial. Two reviewers, disagreement → third opinion; scripts are the referee.
- The work queue is mechanical and resumable: "done" = output file exists on disk and passes its gate.
- One module per PR.
- Every deviation from PHP behavior gets a test + `BEHAVIOR_CHANGES.md` entry.
- Money = BigDecimal. No exceptions.
- Stop at each phase gate. Wait for user confirmation.
- Java version minimum: 21. Spring Boot must match the selected Java version.
- Dependencies added incrementally — only add to `pom.xml` when a phase requires them.
- Gap-inventory items (magic methods, `$$`, `eval`, `mixed`, duck typing) are refactored into explicit contracts before translation, never translated line-by-line.
- Complex workflows (queues, events, scheduling, broadcasting) get a dedicated mapping before implementation.
- Right-size the effort: fan out high-volume translation to subagents; spend the most care on rules and adversarial review.

## References (load on demand)

- **`php-model-discovery` skill** — companion skill; run it first for Phase 0 and Phase 5. Scans
  any PHP app for Eloquent/Doctrine models by inheritance/attribute (not folder convention) and
  emits a manifest of fields, casts, relationships, and flagged ambiguities that seeds this
  skill's `RULEBOOK.md`, `GAP_INVENTORY.md`, and entity generation — built for exactly this kind
  of multi-app migration, so the same scanner runs unmodified across a 100+-app portfolio
- `references/laravel-to-spring.md` — Eloquent, queues, events, Blade, Artisan → Spring equivalents
- `references/symfony-to-spring.md` — bundles, DI container, Twig, Console → Spring equivalents
- `references/semantic-traps.md` — full catalog with PHP/Java code pairs
- `references/composer-to-maven.md` — extended dependency table
- `references/loop-architecture.md` — queue script, adversarial review, and build-daemon patterns for Phases 7–8
- `references/app-spec-template.md` — one consolidated per-app spec doc (identity, framework,
  model inventory, gap inventory, workflow mappings, parity plan, phase tracker, skill feedback
  log) consolidating what Phases 0–2 would otherwise scatter across separate files. Use this for
  every app in a multi-app portfolio — it's what makes "app X surfaced a pattern the skill didn't
  cover" a tracked event instead of a one-off fix

## Source

Methodology adapted from Anthropic's published guidance on AI-driven code modernization: ["COBOL modernization with AI: breaking the cost barrier"](https://claude.com/blog/how-ai-helps-break-cost-barrier-cobol-modernization) and ["How Anthropic runs large-scale code migrations with Claude Code"](https://claude.com/blog/ai-code-migration), plus the [code migration starter kit](https://github.com/anthropics/code-migration-kit-with-claude-code). Content adapted and rephrased for PHP→Java; consult the originals for the general framework.
