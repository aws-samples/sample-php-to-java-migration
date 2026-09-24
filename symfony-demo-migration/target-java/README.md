# target-java — Symfony Demo → Java/Spring Boot (migration output)

This folder will hold the **generated Java/Spring Boot application**. It is created by the
migration workflow, not by hand:

- **Phase 4** scaffolds the Spring Boot project here (Java 21 + Spring Boot 3.2+), with the
  package root mirroring the Symfony namespace root for traceability.
- **Phases 5–7** fill in entities/records, the I/O layer (controllers, security, templates),
  and the converted business logic.
- **Phase 8** compiles, runs, and proves parity against the PHP baseline in `../source-php`.

It's intentionally empty until the workflow runs Phase 4. Do not scaffold it manually — the
skill's process (rulebook-driven, resumable queue) owns generation so the migration stays
consistent and re-runnable.
