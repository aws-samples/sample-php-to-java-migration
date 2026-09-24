# Symfony Demo → Java/Spring Boot (migration project)

The **code folders** for one migration: the official [Symfony Demo](https://github.com/symfony/demo)
ported to Java/Spring Boot with behavioral parity.

This is a *project folder* inside the `PHP-Java-Migration` workspace. The reusable brain
(Skill + Steering) and this project's plan (Spec) live at the **workspace root**, not here —
see the root [`README.md`](../README.md).

## Where the artifacts live (all at the workspace root `PHP-Java-Migration/.kiro/`)

| Artifact | Path (from workspace root) | Scope |
|---|---|---|
| **Skill** | `.kiro/skills/php-to-java-migration/` | Shared across every migration project |
| **Steering** | `.kiro/steering/aws-aidlc-rules/core-workflow.md` | Shared (governs the whole workspace) |
| **Spec** (this project) | `.kiro/specs/symfony-demo-php-to-java-migration/` | Unique to this migration — `requirements.md`, `design.md`, `tasks.md` |

## This folder

```
symfony-demo-migration/
├── README.md         ← this file
├── source-php/       ← PHP source of truth (clone Symfony Demo here) — read-only
└── target-java/      ← generated Java/Spring Boot output (created in Phase 4)
```

## What it exercises in the skill

Unlike DVWA (plain PHP), Symfony Demo stresses the framework-specific paths:
Symfony → Spring MVC/DI, Doctrine ORM → Spring Data JPA, Twig → Thymeleaf,
Symfony Security → Spring Security, Symfony Forms/Validator → `@Valid` DTOs + Jakarta Validation,
Console commands → Spring `@Scheduled`/CLI runners.

## How to run

1. Clone the source — follow `source-php/README.md` (SQLite, no external DB needed).
2. Ask Kiro: *"Migrate the Symfony Demo app in symfony-demo-migration/source-php to Java/Spring
   Boot with behavioral parity."* The shared skill activates and starts at **Phase 0 (Assess)**,
   writing `MIGRATION_ASSESSMENT.md` and pausing at the gate.
3. Approve each phase gate; track progress via the Spec's `tasks.md`. Parity against `source-php/`
   is the definition of done.
