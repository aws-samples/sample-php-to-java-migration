# Requirements Document

## Introduction

The Definition_Owner has already produced a hand-built PHP-to-Java migration playbook (a
transformation definition, a `php-to-java-migration` skill, and a companion `php-model-discovery`
skill under `definition/document_references/`) and has already completed Phase 0 discovery
manually — model manifests exist at `manifests/*.json` and per-app migration specs exist at
`specs/*.md` for three target PHP applications under `apps/` (bookstack — Laravel/Eloquent, sylius
— Symfony/Doctrine XML+attribute, symfony-demo — Symfony/Doctrine attribute).

This feature adopts **AWS Transform custom** (the `atx` CLI / AWS Transform custom service) as the
execution engine that drives this existing playbook, instead of continuing to execute it manually
turn-by-turn. No Java code has been written yet and no deployment has occurred, so this spec covers
only getting AWS Transform custom to successfully draft, validate, publish, and execute the
existing transformation definition end-to-end against the three target repositories. It does not
cover writing, reviewing, or validating the Java code that AWS Transform custom produces — that
remains a separate downstream concern governed by the migration playbook's own exit criteria.

Environment discovery performed for this spec found: the `atx` CLI is already installed
(v3.7.0), Node.js v24.7.0 and Git 2.50.1 are present and satisfy AWS Transform's prerequisites, AWS
CLI resolves an identity (an IAM user in account `111122223333`) with `AdministratorAccess`
attached (broader than AWS Transform custom requires), no default AWS region is configured, and
all three target application directories under `apps/` are already valid Git repositories (`git
status` succeeds in each), though `apps/symfony-demo` currently has one untracked file
(`MIGRATION_SOURCE_NOTES.md`).

## Glossary

- **AWS_Transform_CLI**: The `atx` command-line tool used to draft, publish, execute, and manage
  AWS Transform custom transformation definitions and client-side skills.
- **Transformation_Registry**: The AWS-account-specific store of transformation definitions
  maintained by AWS Transform custom, reachable only in AWS Transform custom's supported regions
  (`us-east-1`, `eu-central-1`, `eu-west-2`, `ca-central-1`, `ap-northeast-1`, `ap-northeast-2`,
  `ap-southeast-2`, `ap-south-1`).
- **Transformation_Definition**: A directory containing exactly `SKILL.md` (required, with YAML
  frontmatter `name` and `description`), an optional `references/` folder of text-only documents
  (10MB total cap), and an optional `scripts/` folder — no other files or subdirectories are
  permitted when this directory is published to the Transformation_Registry.
- **Migration_Definition**: The specific Transformation_Definition this spec produces, consolidating
  the existing `php-to-java-migration` playbook content (currently `definition/transformation_definition.md`
  plus `definition/document_references/php-to-java-migration/`) into one publishable directory.
- **Model_Discovery_Skill**: The existing `php-model-discovery` skill (SKILL.md, `references/`,
  and `scripts/discover-models.php`) that scans a PHP codebase and emits a JSON model/entity
  manifest.
- **Client_Side_Skill**: A skill placed under `.aws/atx/skills/` (or `.agents/skills/`) that
  augments any AWS_Transform_CLI execution with supplementary instructions or scripts, independent
  of the Transformation_Registry and its publish-directory constraints.
- **Draft**: A Transformation_Definition saved to the Transformation_Registry via
  `atx custom def save-draft`, expiring 30 days after creation if not published.
  publishing.
- **Pilot_Run**: An `atx custom def exec` invocation against a drafted (not yet published)
  Migration_Definition, run to validate the definition's behavior before publishing, informed by
  the existing Phase 0 manifests and per-app specs.
- **Target_Repository**: One of the three Git repositories under `apps/` — `apps/bookstack`,
  `apps/sylius`, or `apps/symfony-demo` — that the Migration_Definition executes against.
- **Execution_Configuration**: A YAML or JSON file supplied to `atx custom def exec` via
  `--configuration`, carrying `codeRepositoryPath`, `transformationName`, `buildCommand`, and
  free-text `additionalPlanContext` for a single run.
- **Lesson**: An automatically extracted, reviewable, archivable insight AWS Transform custom
  records from a completed execution, viewable via `atx custom def learnings`.
- **Agent_Minutes**: The billing/usage unit consumed by an AWS_Transform_CLI execution, trackable
  per session and cappable via `--limit`.
- **Definition_Owner**: The person or team responsible for authoring, drafting, piloting,
  publishing, and executing the Migration_Definition in this workspace.

## Requirements

### Requirement 1: Consolidate the transformation definition into one publishable structure

**User Story:** As the Definition_Owner, I want the existing playbook content restructured into a
single valid AWS Transform custom Transformation_Definition, so that it can be drafted and
published to the Transformation_Registry without violating the publish-directory constraint.

#### Acceptance Criteria

1. THE Migration_Definition SHALL consist of exactly one `SKILL.md` file, one optional
   `references/` folder, and one optional `scripts/` folder, with no other files or subdirectories
   present at its publish-directory root.
2. THE Migration_Definition's `SKILL.md` frontmatter SHALL declare a `name` field and a
   `description` field, carried forward from the existing `php-to-java-migration/SKILL.md`
   frontmatter shape.
3. THE Migration_Definition's `references/` folder SHALL contain only text-format documents, and
   the total size of that folder SHALL NOT exceed 10MB.
4. WHEN the existing `php-to-java-migration/references/` and `php-model-discovery/references/`
   markdown files are consolidated, THE Migration_Definition's `references/` folder SHALL retain
   the full content of every existing reference file (`laravel-to-spring.md`,
   `symfony-to-spring.md`, `semantic-traps.md`, `composer-to-maven.md`, `loop-architecture.md`,
   `app-spec-template.md`, `manifest-schema.md`, `config-schema.md`) without content loss.
5. THE Migration_Definition's `scripts/` folder SHALL contain `discover-models.php` directly under
   `scripts/`, not under a nested subdirectory, consistent with AWS Transform's requirement that
   bundled scripts referenced by a definition live directly under `scripts/`.

### Requirement 2: Resolve the companion-skill relationship as a client-side skill

**User Story:** As the Definition_Owner, I want the relationship between the migration playbook
and its model-discovery scanner resolved in a way AWS Transform custom actually supports, so that
the model-discovery capability remains available without requiring two dependent registry entries.

#### Acceptance Criteria

1. THE Model_Discovery_Skill SHALL be installed as a Client_Side_Skill (under `.aws/atx/skills/`
   or `.agents/skills/`) rather than as a second Transformation_Definition published to the
   Transformation_Registry.
2. WHERE the Model_Discovery_Skill is installed as a Client_Side_Skill, THE Model_Discovery_Skill
   SHALL remain available to every AWS_Transform_CLI execution run against a Target_Repository,
   independent of which named Transformation_Definition that execution invokes.
3. THE Migration_Definition's `SKILL.md` SHALL reference the Model_Discovery_Skill by name in its
   instructions (for example, in the mandatory first-step discovery instruction) without embedding
   a second `SKILL.md`, `references/`, or `scripts/` directory for it inside the
   Migration_Definition's own publish directory.
4. IF the `discover-models.php` scanner logic changes, THEN THE Definition_Owner SHALL update the
   copy under the Model_Discovery_Skill's Client_Side_Skill directory and the copy under the
   Migration_Definition's `scripts/` folder together, so the two copies do not diverge.

### Requirement 3: Fold planning artifacts into the published instructions

**User Story:** As the Definition_Owner, I want the content of `transformation_definition.md` and
`summaries.md` reflected in the Migration_Definition's actual instructions, so that no planning
work is lost when those files are excluded from the publish directory.

#### Acceptance Criteria

1. THE Migration_Definition's `SKILL.md` SHALL incorporate the Objective, Summary, Entry Criteria,
   Implementation Steps, and Validation/Exit Criteria sections currently in
   `definition/transformation_definition.md`.
2. WHEN the Migration_Definition is finalized, THE Migration_Definition SHALL NOT include
   `transformation_definition.md` or `summaries.md` as files inside its own publish directory,
   since neither is part of the AWS Transform custom skill format.
3. THE Definition_Owner SHALL retain `transformation_definition.md` and `summaries.md` outside the
   Migration_Definition's publish directory as internal planning artifacts for future edits to
   `SKILL.md` and `references/`.

### Requirement 4: Verify and complete prerequisite tooling setup

**User Story:** As the Definition_Owner, I want the local machine's tooling prerequisites
confirmed, so that `atx` commands run without an environment-caused failure.

#### Acceptance Criteria

1. THE AWS_Transform_CLI SHALL be installed and report a version when invoked with `--version`.
2. THE Definition_Owner's machine SHALL have Node.js version 22 or higher installed.
3. THE Definition_Owner's machine SHALL have Git installed.
4. IF any of the AWS_Transform_CLI, a Node.js version of 22 or higher, or Git is missing, THEN THE
   Definition_Owner SHALL install or upgrade the missing prerequisite before running any `atx
   custom def` command.

### Requirement 5: Configure least-privilege AWS credentials for AWS Transform custom

**User Story:** As the Definition_Owner, I want AWS credentials scoped to what AWS Transform custom
actually needs, so that drafting, piloting, publishing, and executing the Migration_Definition does
not rely on broader permissions than required.

#### Acceptance Criteria

1. THE AWS credentials used for AWS_Transform_CLI commands SHALL resolve to a principal that has
   at least one of the `AWSTransformCustomFullAccess`,
   `AWSTransformCustomExecuteTransformations`, or `AWSTransformCustomManageTransformations`
   AWS-managed IAM policies attached.
2. IF the resolved AWS principal currently has only `AdministratorAccess` (or another
   broader-than-necessary policy) attached and lacks one of the policies in Acceptance Criterion 1,
   THEN THE Definition_Owner SHALL attach `AWSTransformCustomFullAccess` (or the narrower
   combination of `AWSTransformCustomExecuteTransformations` and
   `AWSTransformCustomManageTransformations` if execute/manage roles are to be separated) to a
   principal used for this work, rather than continuing to rely solely on `AdministratorAccess` for
   AWS Transform custom operations.
3. THE Definition_Owner SHALL confirm the resolved AWS identity via `aws sts get-caller-identity`
   before running any `atx custom def` command that creates, modifies, publishes, or deletes a
   Transformation_Definition.

### Requirement 6: Select and configure a supported AWS region

**User Story:** As the Definition_Owner, I want an AWS Transform custom-supported region
configured, so that `atx` commands resolve to a region where the Transformation_Registry is
available.

#### Acceptance Criteria

1. THE Definition_Owner SHALL configure a default AWS region, or supply a region explicitly on
   every `atx custom def` invocation, before drafting, publishing, or executing the
   Migration_Definition.
2. THE configured region SHALL be one of `us-east-1`, `eu-central-1`, `eu-west-2`, `ca-central-1`,
   `ap-northeast-1`, `ap-northeast-2`, `ap-southeast-2`, or `ap-south-1`.
3. IF no default AWS region is configured and no region is passed explicitly on an `atx custom def`
   command, THEN THE AWS_Transform_CLI SHALL prompt the Definition_Owner to select one of the
   supported regions listed in Acceptance Criterion 2 interactively, rather than silently
   resolving to an unsupported region or proceeding without a region.
4. WHEN the Definition_Owner selects a region through the interactive prompt in Acceptance
   Criterion 3, THE Definition_Owner SHALL configure that region as the default AWS region so
   subsequent `atx custom def` commands do not require re-selection.

### Requirement 7: Draft the Migration_Definition in the Transformation_Registry

**User Story:** As the Definition_Owner, I want to save the consolidated Migration_Definition as a
draft before publishing, so that it can be piloted against the target applications without being
visible as a published, reusable definition.

#### Acceptance Criteria

1. WHEN the Migration_Definition satisfies Requirement 1's structural constraints, THE
   Definition_Owner SHALL save it to the Transformation_Registry as a draft via
   `atx custom def save-draft`.
2. THE draft Migration_Definition SHALL expire 30 days after its creation if it is not published
   within that window.
3. IF the Definition_Owner modifies `SKILL.md`, `references/`, or `scripts/` content after saving a
   draft, THEN THE Definition_Owner SHALL re-run `save-draft` to update the draft before piloting
   or publishing it.

### Requirement 8: Pilot and validate the draft before publishing

**User Story:** As the Definition_Owner, I want to pilot the drafted Migration_Definition using the
already-completed Phase 0 discovery artifacts, so that structural or instructional defects are
caught before the definition is published for reuse.

#### Acceptance Criteria

1. THE Definition_Owner SHALL run at least one Pilot_Run of the draft Migration_Definition against
   a Target_Repository before running `atx custom def publish`.
2. WHEN a Pilot_Run executes Step 1 of the Migration_Definition's instructions against a
   Target_Repository, THE Pilot_Run's model/entity manifest output SHALL be compared against the
   corresponding existing manifest in `manifests/*.json` for that Target_Repository, so any
   divergence between the Pilot_Run's discovery output and the already-validated manifest is
   identified before publishing.
3. WHEN a Pilot_Run produces a gap inventory, rulebook deltas, or phase-tracker output for a
   Target_Repository, THE Definition_Owner SHALL compare that output against the corresponding
   sections of that Target_Repository's existing spec in `specs/*.md`, so any regression relative
   to the already-recorded findings is identified before publishing.
4. IF a Pilot_Run reveals that the Migration_Definition's instructions produce output inconsistent
   with the existing manifests or specs, THEN THE Definition_Owner SHALL revise `SKILL.md` or
   `references/` content and re-draft the Migration_Definition before attempting another Pilot_Run.
5. THE Definition_Owner SHALL run every Pilot_Run in this requirement with continual learning
   disabled (`-d`), so pilot-stage runs do not seed Lessons that a not-yet-validated definition
   would otherwise produce.

### Requirement 9: Publish the Migration_Definition

**User Story:** As the Definition_Owner, I want to publish the validated Migration_Definition, so
that it becomes a durable, reusable entry in the Transformation_Registry available for execution
against all three target applications.

#### Acceptance Criteria

1. WHEN at least one Pilot_Run under Requirement 8 has completed, THE Definition_Owner SHALL
   publish the Migration_Definition via `atx custom def publish`.
2. THE AWS_Transform_CLI SHALL allow the Definition_Owner to publish the Migration_Definition via
   `atx custom def publish` regardless of whether a Pilot_Run revealed an unresolved divergence
   from the existing manifests or specs, since the publish decision after reviewing Pilot_Run
   results remains the Definition_Owner's judgment call rather than a tool-enforced gate.
3. THE published Migration_Definition SHALL be retrievable via `atx custom def get` and SHALL
   appear in the output of `atx custom def list`.
4. IF the Definition_Owner needs to change the Migration_Definition's `SKILL.md`, `references/`, or
   `scripts/` content after publishing, THEN THE Definition_Owner SHALL re-publish the definition
   under the same name rather than creating a duplicate registry entry.

### Requirement 10: Verify Git repository state for each target application

**User Story:** As the Definition_Owner, I want each target application's Git state confirmed
before executing the Migration_Definition against it, so that an execution does not fail solely
because the repository is not in a state `atx custom def exec` accepts.

#### Acceptance Criteria

1. THE Definition_Owner SHALL confirm, via `git status`, that each of `apps/bookstack`,
   `apps/sylius`, and `apps/symfony-demo` is a valid Git repository before running
   `atx custom def exec` against it.
2. IF a Target_Repository has uncommitted or untracked changes at the time of an execution (for
   example, `apps/symfony-demo`'s untracked `MIGRATION_SOURCE_NOTES.md`), THEN THE Definition_Owner
   SHALL commit or explicitly accept those changes before that execution, so the execution's
   resulting diff is attributable to the Migration_Definition's own changes.
3. IF a Target_Repository is not yet a Git repository at execution time, THEN THE Definition_Owner
   SHALL run `git init`, `git add .`, and `git commit` in that repository before running
   `atx custom def exec` against it.

### Requirement 11: Supply per-application differences via execution configuration

**User Story:** As the Definition_Owner, I want each target application's distinct target Java
version, Spring Boot version, build command, and cutover strategy supplied per run, so that the
Migration_Definition itself does not need to be forked per application.

#### Acceptance Criteria

1. THE Definition_Owner SHALL supply an Execution_Configuration file to every `atx custom def exec`
   invocation against `apps/bookstack`, `apps/sylius`, or `apps/symfony-demo`, rather than
   modifying the Migration_Definition's `SKILL.md` to encode application-specific values.
2. IF the Definition_Owner runs `atx custom def exec` against `apps/bookstack`, `apps/sylius`, or
   `apps/symfony-demo` without supplying an Execution_Configuration file via `--configuration`,
   THEN THE AWS_Transform_CLI SHALL fail that invocation rather than proceeding with default
   settings.
2. THE Execution_Configuration for `apps/bookstack` SHALL set `codeRepositoryPath` to
   `apps/bookstack` and SHALL carry `additionalPlanContext` noting: Java 21, Spring Boot 3.x,
   structure-preserving structure, and a strangler-fig cutover pending confirmation, per
   `specs/bookstack-spec.md`.
3. THE Execution_Configuration for `apps/sylius` SHALL set `codeRepositoryPath` to `apps/sylius`
   and SHALL carry `additionalPlanContext` noting: Java 21, Spring Boot 3.x, structure to be
   confirmed as structure-preserving given the multi-module monorepo, and a strangler-fig cutover,
   per `specs/sylius-spec.md`.
4. THE Execution_Configuration for `apps/symfony-demo` SHALL set `codeRepositoryPath` to
   `apps/symfony-demo` and SHALL carry `additionalPlanContext` noting: Java 21, Spring Boot 3.x,
   structure-preserving structure, and a big-bang cutover, per `specs/symfony-demo-spec.md`.
5. WHERE a Target_Repository's execution has produced a compiling Maven or Gradle build, THE
   Execution_Configuration for that Target_Repository SHALL set `buildCommand` to the project's
   build/validation command for that run.
6. WHILE a Target_Repository's execution has not yet produced a Maven or Gradle project (for
   example, the first scaffold-only run), THE Execution_Configuration for that Target_Repository
   MAY omit `buildCommand`.
7. THE `transformationName` field in every Execution_Configuration SHALL match the name published
   under Requirement 9 for the Migration_Definition, so all three applications execute against the
   same registry entry.

### Requirement 12: Execute the Migration_Definition against each target application

**User Story:** As the Definition_Owner, I want the published Migration_Definition executed against
bookstack, sylius, and symfony-demo, so that AWS Transform custom is confirmed to run the existing
playbook end-to-end against every target application in scope.

#### Acceptance Criteria

1. WHEN the Definition_Owner runs `atx custom def exec` against `apps/bookstack` with its
   Execution_Configuration from Requirement 11, THE AWS_Transform_CLI SHALL execute the published
   Migration_Definition's Step 1 (model-discovery invocation via the Model_Discovery_Skill) before
   any other step that reads bookstack's model/entity files.
2. WHEN the Definition_Owner runs `atx custom def exec` against `apps/sylius` with its
   Execution_Configuration from Requirement 11, THE AWS_Transform_CLI SHALL execute the published
   Migration_Definition's Step 1 before any other step that reads sylius's model/entity files.
3. WHEN the Definition_Owner runs `atx custom def exec` against `apps/symfony-demo` with its
   Execution_Configuration from Requirement 11, THE AWS_Transform_CLI SHALL execute the published
   Migration_Definition's Step 1 before any other step that reads symfony-demo's model/entity
   files.
4. IF an `atx custom def exec` invocation targets a Target_Repository that is not a Git repository,
   THEN THE AWS_Transform_CLI SHALL fail that invocation rather than proceeding.
5. WHERE the Definition_Owner needs a bounded-cost trial execution, THE Definition_Owner SHALL
   supply `--limit` to cap Agent_Minutes consumed by that execution.
6. WHERE the Definition_Owner invokes execution via headless natural-language form (for example,
   `atx -x "apply transformation definition <name> to <path>" -t`), THE resulting execution SHALL
   be subject to the same Requirements 10 and 11 (Git state and Execution_Configuration) as an
   explicit `atx custom def exec` invocation.

### Requirement 13: Review continual learning output after each execution

**User Story:** As the Definition_Owner, I want automatically extracted lessons reviewed after each
published-definition execution, so that recurring mistakes get folded back into the
Migration_Definition rather than repeating across applications.

#### Acceptance Criteria

1. WHEN an execution of the published Migration_Definition against a Target_Repository completes,
   THE Definition_Owner SHALL review the Lessons recorded for that execution via
   `atx custom def learnings` before executing the same Migration_Definition against another
   Target_Repository.
2. IF a reviewed Lesson describes a defect or gap in the Migration_Definition's `SKILL.md` or
   `references/` content, THEN THE Definition_Owner SHALL update the Migration_Definition and
   re-publish it (per Requirement 9) before relying on that Lesson's fix in a subsequent execution.
3. IF a reviewed Lesson does not describe a defect applicable to future executions (for example, an
   application-specific one-off note), THEN THE Definition_Owner SHALL archive that Lesson via
   `atx custom def learnings` rather than leaving it in an unreviewed state.
4. THE Definition_Owner SHALL NOT disable continual learning (`-d`) on an execution of the
   published Migration_Definition against `apps/bookstack`, `apps/sylius`, or `apps/symfony-demo`,
   reserving `-d` for the Pilot_Runs described in Requirement 8.

### Requirement 14: Non-goals and out-of-scope boundaries

**User Story:** As the Definition_Owner, I want this spec's scope explicitly bounded to adopting
AWS Transform custom as the execution engine, so that the full migration methodology already
captured in the existing playbook is not re-litigated here.

#### Acceptance Criteria

1. THE Definition_Owner SHALL treat the content of the eight-phase migration methodology
   (`php-to-java-migration/SKILL.md`'s Phases 0–8) as already decided and out of scope for this
   spec's requirements, except where Requirement 1 through Requirement 3 require restructuring that
   content for publish-directory compliance.
2. THE Definition_Owner SHALL treat review, correctness, and quality assessment of any Java code
   produced by an execution under Requirement 12 as out of scope for this spec.
3. THE Definition_Owner SHALL treat cutover execution (the actual strangler-fig or big-bang
   traffic switch for any of the three target applications) as out of scope for this spec.
4. THE Definition_Owner SHALL treat onboarding additional target applications beyond bookstack,
   sylius, and symfony-demo as out of scope for this spec.
