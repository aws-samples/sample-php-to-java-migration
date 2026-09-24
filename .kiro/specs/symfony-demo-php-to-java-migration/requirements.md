# Requirements Document

Symfony Demo PHP-to-Java Migration

## Introduction

This document specifies the requirements for migrating the **Symfony Demo application**
(a blog engine with a public site and an admin backend) from PHP/Symfony 8 to Java 21 +
Spring Boot 3.2+, preserving full behavioral parity with the original. The migration keeps
URL paths, HTTP semantics, validation rules, authorization behavior, and rendered output
equivalent to the PHP source, while replacing the framework stack (Symfony → Spring MVC,
Doctrine → Spring Data JPA, Twig → Thymeleaf, Symfony Security → Spring Security).

Behavior comes first: no feature is "improved" during migration. Parity against the PHP
baseline is the definition of done, verified by a parity harness.

## Glossary

- **Parity**: identical observable behavior (HTTP status, redirect target, response body
  structure, validation outcome, authorization decision) between the PHP original and the
  Java port for the same input.
- **Slug**: URL-safe identifier for a post (ASCII slug).
- **Voter / authorization**: the rule deciding whether the current user may edit/delete a post.
- **Baseline**: the running PHP Symfony Demo app, the source of truth for parity.

## Requirements

### Requirement 1: Project scaffold and build

**User Story:** As a developer, I want a Spring Boot project mirroring the Symfony app's
structure, so that the migration is traceable and buildable.

#### Acceptance Criteria
1. THE system SHALL be a Maven project using Java 21 and Spring Boot 3.2+ with dependencies for Spring Web, Spring Security, Spring Data JPA, Thymeleaf, Validation, Mail, and a Markdown library (CommonMark Java).
2. THE Java package root SHALL mirror the PHP `App\` namespace (e.g. `com.symfony.demo`) for traceability.
3. THE system SHALL build to a runnable Spring Boot JAR via `mvn package`.
4. THE system SHALL use SQLite for parity with the demo (or an equivalent embedded DB), with schema managed by Flyway and unchanged table shapes (`symfony_demo_*`).

### Requirement 2: Data model (entities)

**User Story:** As a developer, I want the blog domain entities in JPA, so that data behavior matches Doctrine.

#### Acceptance Criteria
1. THE system SHALL define a `User` entity mapped to `symfony_demo_user` with id, fullName, username (unique), email (unique), password, and roles (JSON list).
2. THE system SHALL define a `Post` entity mapped to `symfony_demo_post` with id, title, slug, summary, content, publishedAt, author (ManyToOne User), comments (OneToMany, ordered by publishedAt DESC), and tags (ManyToMany, ordered by name ASC).
3. THE system SHALL define a `Comment` entity mapped to its table with id, content, publishedAt, author (ManyToOne User), and post (ManyToOne Post).
4. THE system SHALL define a `Tag` entity mapped to its table with id and unique name.
5. WHERE a user has no roles, THE `getRoles()` behavior SHALL return `ROLE_USER` as the default, matching the PHP entity.
6. THE join table for post/tag SHALL be `symfony_demo_post_tag`.

### Requirement 3: Public blog listing, pagination, and tag filter

**User Story:** As a visitor, I want to browse the latest posts with pagination and tag filtering, so that I can read the blog.

#### Acceptance Criteria
1. WHEN a visitor requests `GET /blog/` (or `/blog/page/{page}`), THE system SHALL render the latest posts for that page, newest first, using the same page size as the PHP `Paginator`.
2. WHEN a `tag` query parameter is present, THE system SHALL filter posts by that tag name.
3. WHEN a visitor requests `GET /blog/rss.xml`, THE system SHALL return the posts as an RSS/XML feed.
4. THE pagination metadata (current page, total pages/results) SHALL match the PHP `Paginator` output for the same data.

### Requirement 4: Post detail

**User Story:** As a visitor, I want to view a single post by its slug, so that I can read its content and comments.

#### Acceptance Criteria
1. WHEN a visitor requests `GET /blog/posts/{slug}`, THE system SHALL render the post identified by that slug, including its comments ordered newest first.
2. IF no post matches the slug, THEN THE system SHALL return HTTP 404.
3. THE post content SHALL be rendered from Markdown to HTML equivalently to the PHP `league/commonmark` output.

### Requirement 5: Commenting (authenticated)

**User Story:** As an authenticated user, I want to comment on a post, so that I can participate.

#### Acceptance Criteria
1. WHEN an authenticated user submits `POST /blog/comment/{postSlug}/new` with a valid comment and valid CSRF token, THE system SHALL persist the comment against that post authored by the current user and redirect (HTTP 303) to the post page.
2. IF the request is unauthenticated, THEN THE system SHALL require authentication (redirect to login / 401 per baseline).
3. IF the comment fails validation, THEN THE system SHALL re-render the comment form with the validation errors and NOT persist.
4. WHEN a comment is created, THE system SHALL publish a "comment created" event that triggers an email notification to the post author, mirroring the PHP `CommentCreatedEvent` + subscriber.

### Requirement 6: Search

**User Story:** As a visitor, I want to search posts, so that I can find content.

#### Acceptance Criteria
1. WHEN a visitor requests `GET /blog/search`, THE system SHALL render the search page.
2. WHEN a `q` query parameter is provided, THE system SHALL return matching posts equivalently to the PHP search behavior (results shape used by the template/endpoint).

### Requirement 7: Authentication (form login/logout)

**User Story:** As a user, I want to log in and out, so that I can access protected features.

#### Acceptance Criteria
1. WHEN a visitor requests `GET /login`, THE system SHALL render the login page showing the last username and last authentication error if present.
2. WHILE a user is already authenticated, WHEN they request `/login`, THE system SHALL redirect to the blog index.
3. WHEN valid credentials are submitted, THE system SHALL authenticate the user and honor the saved target path (defaulting to the admin index).
4. WHEN invalid credentials are submitted, THE system SHALL re-render login with an authentication error.
5. THE password verification SHALL be compatible with the stored hash format so seeded demo users authenticate identically to the PHP app.
6. THE system SHALL provide logout that invalidates the session.

### Requirement 8: User profile management

**User Story:** As an authenticated user, I want to edit my profile and change my password, so that I can manage my account.

#### Acceptance Criteria
1. THE `/profile/*` routes SHALL require `ROLE_USER`.
2. WHEN a user submits `POST /profile/edit` with a valid `UserType` form, THE system SHALL persist changes and show a success flash message, then redirect (HTTP 303).
3. WHEN a user submits `POST /profile/change-password` with a valid new password, THE system SHALL update the password and log the user out (as the PHP app does).
4. IF a profile form fails validation, THEN THE system SHALL re-render the form with errors and NOT persist.

### Requirement 9: Admin blog management

**User Story:** As an admin, I want to create, edit, list, and delete posts, so that I can manage content.

#### Acceptance Criteria
1. THE admin blog routes SHALL require `ROLE_ADMIN`.
2. THE system SHALL support listing the current admin user's posts, creating a new post, editing a post, and deleting a post, preserving the PHP admin routes and HTTP methods.
3. WHEN a new post is created, THE system SHALL set the author to the current user and persist it.
4. WHEN a post is deleted, THE system SHALL remove it and its comments (orphan removal), matching the Doctrine cascade behavior.
5. WHERE a post edit/delete is attempted, THE authorization SHALL permit it only if the current user is the post's author OR an admin, matching the PHP `PostVoter`.

### Requirement 10: Authorization (PostVoter parity)

**User Story:** As the system, I want the same authorization decisions as the PHP voter, so that access control is preserved.

#### Acceptance Criteria
1. THE system SHALL grant `edit`/`delete` on a post IF AND ONLY IF the current user is the post's author OR has `ROLE_ADMIN`, matching `PostVoter`.
2. IF an unauthorized user attempts an admin action, THEN THE system SHALL return HTTP 403.

### Requirement 11: Forms and validation

**User Story:** As a user, I want the same validation rules as the PHP app, so that inputs are accepted/rejected identically.

#### Acceptance Criteria
1. THE system SHALL enforce the same constraints as the PHP entities/forms: Post title NotBlank; summary NotBlank + max 255; content NotBlank + min 10; tags max 4; slug unique (error surfaced on title); User fullName NotBlank; username NotBlank + length 2–50 + unique; email valid + unique.
2. WHEN a constraint is violated, THE system SHALL produce the equivalent validation message key and re-render the form without persisting.

### Requirement 12: Internationalization and locale

**User Story:** As a visitor, I want localized content and locale switching, so that the app matches the PHP i18n behavior.

#### Acceptance Criteria
1. THE system SHALL resolve the active locale and expose translated message keys equivalently to the PHP translations.
2. THE system SHALL replicate the "redirect to preferred locale" behavior for the root path, mirroring `RedirectToPreferredLocaleSubscriber`.

### Requirement 13: Console commands

**User Story:** As an operator, I want the user-management commands, so that I can manage users from the CLI.

#### Acceptance Criteria
1. THE system SHALL provide equivalents of `AddUserCommand`, `DeleteUserCommand`, and `ListUsersCommand` (create user with role, delete user, list users) with the same inputs/outputs.

### Requirement 14: Templating and rendered output

**User Story:** As a visitor, I want the pages to render equivalently, so that the migrated site looks and behaves the same.

#### Acceptance Criteria
1. THE system SHALL render pages with Thymeleaf templates that produce DOM structure equivalent to the Twig templates for the same data.
2. THE static assets (CSS/JS/images) SHALL be served so pages render equivalently; the frontend build (AssetMapper/Sass) MAY be replaced by pre-built static assets.

### Requirement 15: Configuration and secrets

**User Story:** As an operator, I want externalized configuration, so that deployment matches the PHP env approach.

#### Acceptance Criteria
1. THE `.env`-based configuration SHALL map to `application.yml` + `@ConfigurationProperties`, with secrets kept external.
2. THE database, mailer, and locale settings SHALL be configurable via environment variables.

### Requirement 16: Data fixtures / seed data

**User Story:** As a developer, I want the demo seed data, so that parity tests run against equivalent content.

#### Acceptance Criteria
1. THE system SHALL provide a seed (Flyway seed or dev-profile seeder) equivalent to the PHP `AppFixtures` (demo users, posts, tags, comments) for parity testing.

### Requirement 17: Parity harness (the judge)

**User Story:** As a developer, I want an automated parity referee, so that the migration has an objective exit condition.

#### Acceptance Criteria
1. THE system SHALL provide a parity harness that can issue identical requests to both the PHP baseline and the Java port and compare responses (status code, redirect target, normalized body structure).
2. THE harness SHALL cover the core flows: blog index/pagination/tag, post detail, comment add (auth), search, login/logout, profile edit, change password, and admin CRUD.
3. THE harness SHALL be validated: it passes against correct code and fails against deliberately broken code.
4. WHERE a behavioral difference is intentional, THE difference SHALL be documented in `BEHAVIOR_CHANGES.md` with justification.

### Requirement 18: Migration documentation

**User Story:** As a reviewer, I want the migration documented, so that decisions and deviations are traceable.

#### Acceptance Criteria
1. THE system SHALL produce `MIGRATION_ASSESSMENT.md`, `RULEBOOK.md`/`WORKFLOW_MAPPINGS.md`, `DEPENDENCY_GAPS.md`, and `BEHAVIOR_CHANGES.md`.
2. Each intentional deviation from PHP behavior SHALL have a `BEHAVIOR_CHANGES.md` entry cross-referenced to the parity results.
