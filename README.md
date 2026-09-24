# Repeatable PHP-to-Java Migration Engine with Kiro

A reusable, skill-driven engine for migrating PHP applications (Symfony, Laravel, Doctrine-based)
to Java/Spring Boot with **behavioral parity** — built for [Kiro](https://kiro.dev), and packaged
so the same skill files can also drive [AWS Transform](https://aws.amazon.com/transform/) custom
transformations for portfolio-scale agentic runs.

The model: **one shared brain, many projects.** The migration methodology (Skill) and the
governing workflow (Steering) live once at the repository root and apply to every migration. Each
migration gets its own **Spec** folder and its own **code** folder. Improve the methodology once,
and every current and future project inherits the improvement.

Open **this folder** as the Kiro workspace root — everything below resolves from here.

---

## Layout

```
.
├── README.md                                ← this file
├── .kiro/                                   ← SHARED, GLOBAL to all projects
│   ├── skills/
│   │   ├── php-to-java-migration/           ← the migration methodology (Skill) + references/
│   │   └── php-model-discovery/             ← the discovery scanner (Skill) + scripts/
│   ├── steering/
│   │   └── aws-aidlc-rules/core-workflow.md ← governing AI-DLC workflow (auto-applied)
│   ├── aws-aidlc-rule-details/              ← rule detail files the steering loads
│   └── specs/                               ← ONE sub-folder per migration (unique per project)
│       └── symfony-demo-php-to-java-migration/
│
├── symfony-demo-migration/                  ← PROJECT: Symfony Demo (framework PHP → Java)
│   ├── source-php/                          (symfony/demo, pinned commit — cloned on demand)
│   └── target-java/                         (generated Spring Boot port + parity harness)
│
├── laravel-eloquent-sample/                 ← PROJECT: BookStack slice (Eloquent → JPA)
├── sylius-doctrine-sample/                  ← PROJECT: Sylius slice (Doctrine XML → JPA)
│
└── Transform/                               ← AWS Transform custom adoption (same skills,
    ├── definition/                            agentic execution path)
    ├── manifests/                             (Phase 0 discovery manifests per app)
    └── specs/                                 (per-app migration specs)
```

### What's shared vs per-project

| Thing | Location | Shared or per-project |
|---|---|---|
| **Skills** (methodology + scanner) | `.kiro/skills/` | **Shared** — improve once, every project benefits |
| **Steering** (workflow) | `.kiro/steering/` + `.kiro/aws-aidlc-rule-details/` | **Shared** |
| **Spec** (the plan) | `.kiro/specs/<project>-php-to-java-migration/` | **Per-project** (own sub-folder) |
| **Code** (PHP source + Java output) | `<project>/source-php`, `<project>/target-java` | **Per-project** (own top-level folder) |

---

## Prerequisites

- [Kiro](https://kiro.dev) (or the AWS Transform `atx` CLI for the agentic path)
- PHP 8.0+ on the machine running the discovery scanner (the scanner is dependency-free —
  no Composer install, and it never executes the target application's code)
- Java 21 and Maven for the generated Spring Boot projects
- Docker (optional) for the side-by-side parity environment

## Getting started

1. Clone this repository and open the repository root as your Kiro workspace.
2. Fetch the PHP source under migration into the project's `source-php/` folder (see
   [Source applications](#source-applications) below for the exact pinned commits).
3. Ask Kiro to migrate the application. The `php-to-java-migration` skill activates
   automatically, runs the discovery scanner before anything else, and generates a fresh spec
   under `.kiro/specs/` for that project.
4. Work through the approval gates. Where Docker is available, the parity environment under
   `symfony-demo-migration/target-java/src/test/resources/parity/` runs the PHP baseline and the
   Java port side by side for request-level comparison.
5. For an agentic run at portfolio scale, the `Transform/` folder shows how the same skill files
   are consolidated into an AWS Transform custom transformation definition.

### Adding a new migration project

1. Create a code folder at the root, e.g. `my-app-migration/` with `source-php/`
   (`target-java/` will be generated).
2. Clone/copy the PHP app into `my-app-migration/source-php/`.
3. Ask Kiro to migrate it — the shared Skill activates automatically and generates a **new** spec
   folder at `.kiro/specs/my-app-php-to-java-migration/`.
4. That's it — no need to copy the skill or steering; they're already shared at the root.

To **improve the methodology**, edit `.kiro/skills/php-to-java-migration/` once — the change
applies to all current and future projects in this workspace.

---

## The migration methodology (8 phases, parity-first)

Front-loaded human judgment (Phases 0–3), then mostly self-driving loops (Phases 4–8), each with a
deliverable and an approval gate:

| Phase | Name | Deliverable |
|---|---|---|
| 0 | Assess & discover | `MIGRATION_ASSESSMENT.md` (inventory, dependency graph, cutover choice) |
| 1 | Build the judge (parity harness) | harness runnable against **both** PHP and Java |
| 2 | Rulebook + gap inventory | `RULEBOOK.md`, `GAP_INVENTORY.md`, `WORKFLOW_MAPPINGS.md` |
| 3 | Stress-test the rulebook | hardened `RULEBOOK.md` |
| 4 | Scaffold + map dependencies | compiling Spring Boot project + `DEPENDENCY_GAPS.md` |
| 5 | Translate the data model | JPA entities/records + schema baseline |
| 6 | Translate the I/O layer | controllers, security, templates respond |
| 7 | Convert business logic | classes converted, tests green, `BEHAVIOR_CHANGES.md` |
| 8 | Compile, run, prove parity, cut over | parity report + cutover checklist |

Two governing principles:

- **The PHP code is the specification.** The Java port is done when the parity harness agrees,
  module by module — or when every mismatch is explicitly logged as an approved, intentional
  change in `BEHAVIOR_CHANGES.md`.
- **Fix the loop, not the code.** When the same mistake shows across files, amend the rulebook
  and regenerate — never hand-patch.

---

## Projects in this workspace

| Project | PHP kind | Purpose |
|---|---|---|
| **Symfony Demo** (`symfony-demo-migration/`) | Symfony framework | Full worked migration with parity harness |
| **BookStack slice** (`laravel-eloquent-sample/`) | Laravel/Eloquent | Scanner validation + bounded data-model port |
| **Sylius slice** (`sylius-doctrine-sample/`) | Symfony/Doctrine (XML-mapped) | Scanner validation + bounded data-model port |
| **AWS Transform adoption** (`Transform/`) | — | Same skills packaged as an AWS Transform custom definition |

---

## Source applications

**The original PHP source is intentionally NOT committed in this repository.** It is third-party
open-source code migrated *from*, not code written here. Each PHP source is pinned to an exact
upstream commit so anyone reviewing parity can fetch precisely the version this migration was
built against:

| Project | PHP source | License | Pinned commit | Clone into |
|---|---|---|---|---|
| **Symfony Demo** | [symfony/demo](https://github.com/symfony/demo) | MIT | `03fe256` | `symfony-demo-migration/source-php/` |
| **BookStack** | [BookStackApp/BookStack](https://github.com/BookStackApp/BookStack) | MIT | latest at scan time | `laravel-eloquent-sample/source-php/` |
| **Sylius** | [Sylius/Sylius](https://github.com/Sylius/Sylius) | MIT | latest at scan time | `sylius-doctrine-sample/source-php/` |

To fetch the Symfony Demo source for local comparison (from the repo root):

```bash
git clone https://github.com/symfony/demo.git symfony-demo-migration/source-php
cd symfony-demo-migration/source-php && git checkout 03fe256 && cd -
```

All `source-php/` folders are gitignored — see `.gitignore`.

### How the Java port relates to the PHP source

- **Same routes, same behavior, different stack.** Each PHP entry point maps to a Spring
  `@Controller`/`@RestController` method at the equivalent URL path. Business logic is
  translated, not reinvented — the PHP app is the specification; the Java app is expected to
  behave identically (see `BEHAVIOR_CHANGES.md` per project for documented, intentional
  deviations).
- **Build & run the Java port** (from each project's `target-java/` folder):

  ```bash
  ./mvnw clean package      # compile + run unit/property tests
  ./mvnw spring-boot:run    # run the app locally (http://localhost:8080)
  ```

- **Compare against the PHP original**: where Docker is available, the parity compose environment
  runs the PHP original alongside the Java port for side-by-side comparison:

  ```bash
  docker compose -f symfony-demo-migration/target-java/src/test/resources/parity/compose-parity.yml up --build
  ```

### Container base images

Container base images in the Dockerfiles are literal
[Amazon ECR Public](https://gallery.ecr.aws/) references
(`public.ecr.aws/docker/library/...`, the ECR mirror of the Docker Hub official images):

| Image | ECR Public reference |
|---|---|
| Maven build | `public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21` |
| JRE runtime | `public.ecr.aws/docker/library/eclipse-temurin:21-jre-jammy` |
| PHP baseline | `public.ecr.aws/docker/library/php:8.4-cli` |
| Composer | `public.ecr.aws/docker/library/composer:2` |

ECR Public allows anonymous pulls, so builds work with no registry authentication and avoid
Docker Hub rate limits.

---

## Key takeaways

- **Repeatable, not bespoke.** The Skill methodology is designed to drive any PHP kind — plain
  procedural PHP or a framework app (Symfony, Laravel, CodeIgniter) — with only the per-project
  Spec differing.
- **Parity is the referee.** A judge is built before translation and runs against both the PHP
  original and the Java port; "done" means it agrees.
- **Judgment is front-loaded.** Humans spend time on the rulebook and stress test; bulk
  translation is a resumable, parallelized loop.
- **Deviations are documented, not hidden.** Every intentional difference lands in
  `BEHAVIOR_CHANGES.md` with justification and a parity cross-reference.
- **Two execution paths, one brain.** The same skill files drive interactive Kiro sessions and
  AWS Transform custom agentic runs — fine-tune once, both engines improve.

---

## Third-party content and attribution

The Java projects in this repository are behavioral ports of MIT-licensed open-source PHP
applications. The generated Java code, templates, and message catalogs are derived from those
projects to preserve behavior:

- [symfony/demo](https://github.com/symfony/demo) — MIT License (c) Fabien Potencier
- [BookStack](https://github.com/BookStackApp/BookStack) — MIT License (c) Dan Brown and contributors
- [Sylius](https://github.com/Sylius/Sylius) — MIT License (c) Paweł Jędrzejewski and contributors

No upstream PHP source is redistributed here; see [Source applications](#source-applications) for
pinned upstream commits.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## Contributing

See [CONTRIBUTING](CONTRIBUTING.md) for more information.

## License

This library is licensed under the MIT-0 License. See the [LICENSE](LICENSE) file.
