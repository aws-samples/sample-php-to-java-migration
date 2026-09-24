# Implementation Plan: Symfony Demo PHP-to-Java Migration

## Overview

Migrates the Symfony Demo blog from PHP/Symfony 8 to Java 21 + Spring Boot 3.2+ with behavioral
parity. Order follows the migration skill: scaffold → build the judge → data model → I/O layer →
business logic → tests → parity → docs. Leaf modules (entities, utils) first; high-fan-in
(controllers, admin) last. Property tests validate correctness properties; the parity harness is
the objective referee against the PHP baseline in `symfony-demo-migration/source-php`.

## Tasks

- [x] 1. Scaffold project and build system
  - [x] 1.1 Create Maven Spring Boot project
    - `symfony-demo-migration/target-java/pom.xml` (Spring Boot 3.2+, Java 21): Web, Security, Data JPA, Thymeleaf, Validation, Mail, SQLite/Flyway, commonmark-java, jqwik, Testcontainers
    - `DemoApplication.java` with `@SpringBootApplication`; package root `com.symfony.demo`
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 1.2 Application configuration
    - `application.yml` mapping `.env` (DB, mailer, locale) via `@ConfigurationProperties`
    - SQLite datasource + JPA `ddl-auto: none` (schema via Flyway)
    - _Requirements: 14.1, 14.2, 1.4_

- [x] 2. Build the parity harness (the judge) — before translation
  - [x] 2.1 Stand up the PHP baseline + harness infrastructure
    - Docker Compose for PHP Symfony Demo (SQLite, fixtures) + placeholder Java service
    - Base parity client (hits both targets) + response comparison util (status, redirect path, normalized body)
    - _Requirements: 17.1, 17.2_
  - [x] 2.2 Validate the judge
    - Confirm harness passes on correct PHP; fails on deliberately broken PHP (flip a check, drop a field)
    - _Requirements: 17.3_

- [x] 3. Checkpoint - project compiles, harness validated
  - Ensure the empty project builds and the judge is trustworthy.

- [x] 4. Data model (entities + repositories + schema)
  - [x] 4.1 JPA entities
    - `User`, `Post`, `Comment`, `Tag` with mappings/relations per design (tables `symfony_demo_*`)
    - Roles default `ROLE_USER`; comments OrderBy DESC; tags ManyToMany JoinTable + OrderBy ASC
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  - [x] 4.2 Repositories
    - `UserRepository`, `PostRepository` (findLatest + tag filter), `TagRepository`, `CommentRepository`
    - _Requirements: 3.1, 3.2, 6.2_
  - [x] 4.3 Flyway schema + seed
    - `V1__schema.sql` (unchanged table shapes) + `V2__seed.sql` equivalent to `AppFixtures`
    - _Requirements: 1.4, 16.1_
  - [x] 4.4 Property test — roles default
    - **Property 3: getRoles() always includes ROLE_USER, de-duplicated. Validates: Requirements 2.5**

- [x] 5. Cross-cutting services (leaf-first)
  - [x] 5.1 Paginator service
    - Reproduce PHP `Paginator` page size + metadata (current/total pages, results)
    - _Requirements: 3.1, 3.4_
  - [x] 5.2 Markdown renderer
    - commonmark-java renderer equivalent to league/commonmark
    - _Requirements: 4.3_
  - [x] 5.3 Validation DTOs + constraints
    - `PostDto`, `CommentDto`, `UserDto`, `ChangePasswordDto` with Jakarta Bean Validation mirroring PHP constraints
    - _Requirements: 11.1, 11.2_
  - [x] 5.4 Property tests — paginator + validation
    - **Property 5: Paginator invariant (bounds + slice). Validates: Requirements 3.4**
    - **Property 1: Post validation parity. Validates: Requirements 11.1**
    - **Property 2: User validation parity. Validates: Requirements 11.1**
    - **Property 7: Markdown round-trip. Validates: Requirements 4.3**

- [x] 6. Security infrastructure
  - [x] 6.1 Spring Security config + UserDetails
    - Form login on `/login`, `UserDetailsService` over `UserRepository`, password encoder matching seeded hashes, role hierarchy ADMIN>USER, CSRF on
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_
  - [x] 6.2 PostVoter parity (authorization)
    - `PostPermissionEvaluator`/`AuthorizationManager`: allow edit/delete iff author or admin; 403 otherwise
    - _Requirements: 10.1, 10.2, 9.5_
  - [x] 6.3 Property test — PostVoter
    - **Property 4: edit/delete granted iff author or admin. Validates: Requirements 10.1**

- [x] 7. Checkpoint - data model, services, security compile and unit/property tests pass

- [x] 8. I/O layer — public blog
  - [x] 8.1 BlogController: index, pagination, tag filter, RSS
    - `GET /blog/`, `/blog/page/{page}`, `/blog/rss.xml`; tag query filter; Thymeleaf `blog/index`
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  - [x] 8.2 BlogController: post detail + search
    - `GET /blog/posts/{slug}` (404 if missing, markdown render, comments DESC); `GET /blog/search`
    - _Requirements: 4.1, 4.2, 4.3, 6.1, 6.2_
  - [x] 8.3 Blog Thymeleaf templates
    - `blog/index.html`, `blog/index.xml` (rss), `blog/post_show.html`, `blog/search.html`, `_comment_form`
    - _Requirements: 14.1, 14.2_

- [x] 9. I/O layer — commenting + events
  - [x] 9.1 Comment creation (authenticated)
    - `POST /blog/comment/{postSlug}/new` w/ auth + CSRF + validation; persist; redirect 303; re-render form on error
    - _Requirements: 5.1, 5.2, 5.3_
  - [x] 9.2 CommentCreatedEvent + email listener
    - Publish event; `@EventListener` sends author notification via Spring Mail + Thymeleaf email template
    - _Requirements: 5.4_

- [x] 10. I/O layer — authentication + user profile
  - [x] 10.1 Login/logout views
    - `/login` page (last username, last error; redirect if authenticated); logout invalidates session
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.6_
  - [x] 10.2 Profile edit + change password
    - `/profile/edit` (ROLE_USER, success flash, 303); `/profile/change-password` (update + logout)
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [x] 11. I/O layer — admin blog CRUD
  - [x] 11.1 Admin BlogController
    - List own posts, new, edit, delete (ROLE_ADMIN); author set on create; orphan-removal on delete; authorization via voter
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_
  - [x] 11.2 Admin Thymeleaf templates
    - `admin/blog/index`, `new`, `edit` forms
    - _Requirements: 14.1_

- [ ] 12. i18n + locale
  - [ ] 12.1 MessageSource + translations
    - Convert PHP translations to `messages_*.properties`; wire `MessageSource`
    - _Requirements: 12.1_
  - [ ] 12.2 Preferred-locale redirect
    - `LocaleResolver` + interceptor reproducing `RedirectToPreferredLocaleSubscriber`
    - _Requirements: 12.2_

- [ ] 13. Console commands
  - [ ] 13.1 User management commands
    - `AddUserCommand`, `DeleteUserCommand`, `ListUsersCommand` via CommandLineRunner/Picocli
    - _Requirements: 13.1_

- [ ] 14. Static assets
  - [x] 14.1 Serve pre-built CSS/JS/images
    - Provide compiled static assets so pages render equivalently (frontend build replaced)
    - _Requirements: 14.2_

- [ ] 15. Checkpoint - all routes respond; app runs end-to-end

- [ ] 16. Integration tests (Testcontainers / MockMvc)
  - [ ] 16.1 Blog + comment flows
    - index/pagination/tag, post detail 404, authenticated comment add + event
    - _Requirements: 3.1, 4.1, 4.2, 5.1, 5.4_
  - [ ] 16.2 Auth + profile + admin
    - login/logout, profile edit, change-password logout, admin CRUD, 403 for unauthorized
    - _Requirements: 7.3, 8.2, 8.3, 9.2, 10.2_

- [ ] 17. Parity tests (PHP vs Java)
  - [ ] 17.1 Core-page parity
    - blog index/pagination/tag, RSS, post detail, search, login page
    - _Requirements: 17.1, 17.2_
  - [ ] 17.2 Authenticated + admin parity
    - comment add, profile edit, change password, admin CRUD, authorization 403 cases
    - Document intentional deviations in `BEHAVIOR_CHANGES.md`
    - _Requirements: 17.2, 17.4_

- [ ] 18. Checkpoint - full test suite green

- [ ] 19. Migration documentation
  - [ ] 19.1 RULEBOOK.md + WORKFLOW_MAPPINGS.md (Symfony→Spring rules applied)
    - _Requirements: 18.1_
  - [ ] 19.2 DEPENDENCY_GAPS.md (Composer→Maven, unmapped packages)
    - _Requirements: 18.1_
  - [ ] 19.3 BEHAVIOR_CHANGES.md (intentional deviations + parity cross-refs)
    - _Requirements: 18.2, 17.4_
  - [ ] 19.4 Feed learnings back into the shared skill
    - Update `.kiro/skills/php-to-java-migration/references/symfony-to-spring.md` with any new rules discovered
    - _Requirements: 18.1_

- [ ] 20. Final checkpoint - build, tests, Docker, parity report
  - `mvn package` produces runnable JAR; `docker compose up` runs PHP + Java; parity report per flow.

## Notes

- Parity against `symfony-demo-migration/source-php` is the definition of done.
- Lower-risk than DVWA: strongly-typed source, near-zero dynamic-typing traps.
- The parity harness (Task 2) is built before translation and validated against broken code.
- Skill refinements discovered here flow back to the shared skill (Task 19.4) so future
  Symfony/Laravel migrations benefit.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1"] },
    { "id": 2, "tasks": ["2.2"] },
    { "id": 3, "tasks": ["4.1", "4.3"] },
    { "id": 4, "tasks": ["4.2", "4.4", "5.1", "5.2", "5.3"] },
    { "id": 5, "tasks": ["5.4", "6.1"] },
    { "id": 6, "tasks": ["6.2"] },
    { "id": 7, "tasks": ["6.3"] },
    { "id": 8, "tasks": ["8.1", "8.2", "8.3"] },
    { "id": 9, "tasks": ["9.1", "9.2"] },
    { "id": 10, "tasks": ["10.1", "10.2"] },
    { "id": 11, "tasks": ["11.1", "11.2"] },
    { "id": 12, "tasks": ["12.1", "12.2", "13.1", "14.1"] },
    { "id": 13, "tasks": ["16.1", "16.2"] },
    { "id": 14, "tasks": ["17.1", "17.2"] },
    { "id": 15, "tasks": ["19.1", "19.2", "19.3", "19.4"] }
  ]
}
```
