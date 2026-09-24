# Behavior Changes (PHP → Java parity notes)

Deviations from the PHP/Symfony source recorded during the Java/Spring Boot migration.
Each entry notes the parity intent and why the Java behavior differs.

## Task 9.2 — CommentCreatedEvent + email listener

- **Listener style:** `CommentNotificationListener` uses a plain synchronous
  `@EventListener` (not `@TransactionalEventListener`). In `BlogController.commentNew`
  the event is published *after* `CommentRepository.save(...)` has already committed its
  own transaction, and the controller is not transactional, so no transaction is bound to
  the publishing thread. A `@TransactionalEventListener(phase = AFTER_COMMIT)` would
  silently not fire in that situation. The plain listener also matches the PHP subscriber,
  which dispatched synchronously right after the comment was flushed.

- **Mail-send failures are non-fatal:** the email send is wrapped in a try/catch that logs
  a warning on `MessagingException`/`MailException` instead of propagating. The PHP demo
  ships with a `null://null` mailer transport that silently discards mail, so a comment
  post never fails due to mail. In Java, `spring.mail.*` defaults to `localhost:1025`,
  which is typically not running in dev/test; swallowing the failure preserves the PHP
  behavior of the comment POST always succeeding. Real SMTP settings still deliver mail.

- **Subject/body text is English literal (not MessageSource):** the subject uses the
  literal "A new comment has been posted" and the body template uses English prose
  approximating `notification.comment_created.description`. Full i18n via `MessageSource`
  is deferred to task 12.1, consistent with how the public templates (task 8.3) handled
  translations.

- **Absolute link base URI:** the notification link is built from `app.default-uri`
  (mirrors the Symfony `DEFAULT_URI` used for absolute URL generation), default
  `http://localhost`, producing `http://localhost/blog/posts/{slug}#comment_{id}`.

## Task 10.2 — Profile edit + change password (UserController)

- **Change-password logout mechanics:** Symfony called
  `$security->logout(validateCsrfToken: false)` then `redirectToRoute('homepage')`.
  The Java port reproduces the logout inline in the controller: it invalidates the HTTP
  session (`HttpServletRequest.getSession(false).invalidate()`) and clears the
  `SecurityContextHolder`, then redirects to `/blog/` (the blog index / homepage
  equivalent). No CSRF re-validation is performed on the logout side effect, matching
  `validateCsrfToken: false`; the POST itself is still CSRF-protected by the form token.
  Behavior is equivalent (the user is signed out and lands anonymous on the homepage),
  only the framework mechanism differs.

- **Flash attribute key:** the PHP `addFlash('success', 'user.updated_successfully')` is
  ported as `RedirectAttributes.addFlashAttribute("flash_success", "user.updated_successfully")`,
  rendered by `templates/user/edit.html` as a Bootstrap `alert-success`. There was no
  pre-existing flash convention in the shared `layout.html`, so the flash is rendered on
  the edit page itself (via `${flash_success}`) rather than a shared layout region. The
  literal message key `user.updated_successfully` is emitted as-is (i18n/MessageSource
  wiring is task 12.1).

- **Authorization guard:** the PHP class-level `#[IsGranted(User::ROLE_USER)]` is ported as
  a class-level `@PreAuthorize("hasRole('USER')")` on `UserController` (method security).
  `SecurityConfig` independently restricts `/profile/**` to `hasRole('USER')` at the URL
  layer; the two agree and `ROLE_ADMIN` satisfies both via the `ROLE_ADMIN > ROLE_USER`
  hierarchy. SecurityConfig was not modified by this task.

- **Change-password success redirect status:** the PHP `redirectToRoute('homepage')` sets no
  explicit status, so the Java port uses a plain 302 redirect to `/blog/` (not 303). The
  profile-edit success path does use 303 SEE_OTHER, matching its explicit
  `Response::HTTP_SEE_OTHER` in the PHP source.

## Task 10.1 — Login/logout views

- **Login CSRF parameter name:** the Twig login form used Symfony's `_csrf_token` field with the
  `authenticate` token id. The Spring port renders Spring Security's own token (`${_csrf}`,
  parameter `_csrf`). CSRF protection remains enabled for the login POST (matching Symfony's
  `enable_csrf: true`); only the field name differs. Framework-mechanics deviation, no behavioral
  impact. (Also noted for task 6.1.)
- **`_target_path` hidden field omitted:** the Twig form carried a hidden `_target_path` populated
  from `?redirect_to=` so a successful login could redirect to an arbitrary target. Spring form
  login instead uses its saved-request mechanism plus `defaultSuccessUrl("/blog/", false)`, so the
  posted-target-path field is not honored and is omitted from the template. Post-login redirect
  parity (to the originally requested protected page, else `/blog/`) is preserved via the saved
  request; only the explicit `?redirect_to=` override query param is unsupported.
- **`last_username` source may be empty:** the PHP page prefilled the username via
  `AuthenticationUtils::getLastUsername()`. The Java port reads the `SPRING_SECURITY_LAST_USERNAME`
  session attribute. Spring's default username/password filter does not always populate this
  attribute, so after a failed login the username field may render empty (the error alert still
  shows). The field is prefilled whenever the attribute is present. Minor deviation.
- **`error` model attribute absent (not null) on success/first visit:** the controller only adds the
  `error` attribute when a prior authentication failed (read from the
  `SPRING_SECURITY_LAST_EXCEPTION` session attribute, which is then consumed one-shot). The template
  guards with `th:if="${error}"`, treating absent and null identically — same rendered output as the
  PHP `error is not null` check.
- **Remember-me checkbox default-checked:** the demo login template ships the `_remember_me` checkbox
  checked by default for convenience; the Symfony template left it unchecked. Cosmetic default only;
  remember-me wiring (parameter, 1-week lifetime) is unchanged from task 6.1.


## Task 11.1 — Admin BlogController (`AdminBlogController`)

- **Delete CSRF token:** the PHP `delete()` action validated a hand-rolled CSRF token
  named `delete` read from the POST payload (`isCsrfTokenValid('delete', $token)`), and on
  an invalid token silently redirected to the index. The Java port relies on Spring
  Security's built-in CSRF protection instead (the delete form authored in task 11.2
  renders the standard `_csrf` token); no custom `delete`-named token is implemented. An
  invalid/missing token is rejected by the security filter chain with HTTP 403 rather than
  a silent redirect. This is a framework-mechanics difference with no behavioral-parity
  impact on the success path.

- **Per-post authorization is invoked inline, not via `@PreAuthorize`:** show/edit/delete
  reproduce the PHP `denyAccessUnlessGranted(...)` / `#[IsGranted('edit', subject: 'post')]`
  checks by calling the shared `PostPermissionEvaluator` after loading the post, throwing
  `AccessDeniedException` (→ HTTP 403) on denial. `@PreAuthorize("hasPermission(#post, …)")`
  was not used because the post must be loaded from the repository before it can be
  authorized (it is not a resolved method argument). Behavior matches the PHP 403 on a
  non-author; the evaluator's rule remains author-only (see the task 6.2 note below).

- **Authorization rule is author-only (inherited deviation):** the reused
  `PostPermissionEvaluator` grants show/edit/delete iff the current user *is* the post's
  author, matching the PHP `PostVoter` source of truth. Requirement 9/10's "author OR
  admin" wording is superseded by the actual PHP behavior. In the demo, admins manage only
  their own posts because both the controller listing (`findByAuthorOrderByPublishedAtDesc`)
  and the voter are author-scoped.

- **Admin index lists all of the author's posts (including unpublished/future):** the PHP
  `index()` used `findBy(['author' => $user], ['publishedAt' => 'DESC'])`, which — unlike
  the public `findLatest` — does not apply a `publishedAt <= now` cutoff. The new
  `PostRepository.findByAuthorOrderByPublishedAtDesc(User)` mirrors this exactly (no cutoff),
  so scheduled/future-dated posts still appear in the admin list.

- **Slug generation:** the PHP `PostType` set the slug on submit only when it was still
  null (`$slugger->slug($title)->lower()`), i.e. for new posts; edits never regenerate it.
  The Java `create()` handler generates the slug from the title for new posts and `edit()`
  leaves the existing slug untouched, matching that behavior. The slugifier uses
  `java.text.Normalizer` (NFD) + diacritic stripping + lowercase + hyphenation rather than
  Symfony's ICU-backed `AsciiSlugger`; for the ASCII/Latin titles used by the demo the
  output matches, but exotic Unicode may transliterate slightly differently. Slug
  uniqueness (`UniqueEntity(slug, errorPath: title)`) is still not enforced at the form
  layer (carried over from the `PostDto` note) — a DB-level/service uniqueness hook is
  deferred.

- **Tag binding:** the PHP `TagsInputType` view-transformer resolved submitted tag names to
  `Tag` entities (reusing existing ones or creating new). The Java `applyTags(...)` helper
  reproduces this: each non-blank, de-duplicated name is resolved via
  `TagRepository.findByName` or created as `new Tag(name)`, then attached to the post
  (cascade `PERSIST` persists new tags). This is a minimal reproduction of the rich JS tag
  widget; the `PostDto.tags` `@Size(max = 4)` constraint preserves `post.too_many_tags`.

- **Flash key + redirect status:** success flashes use `RedirectAttributes.addFlashAttribute
  ("flash_success", …)` (the task 10.2 flash-key convention) with the PHP i18n keys kept as
  literal values (`post.created_successfully` / `post.updated_successfully` /
  `post.deleted_successfully`); i18n resolution is task 12.1. All success redirects are HTTP
  303 SEE_OTHER, matching the PHP `Response::HTTP_SEE_OTHER`.

## Task 11.2 — Admin blog Thymeleaf templates (`admin/blog/{index,new,edit,show}`)

- **Admin sidebar actions relocated into main content.** The PHP admin templates rendered
  per-page sidebar actions (`admin/blog/index` "Create post" button; `edit`/`show` "Show"/"Edit"
  links + delete form) inside the Twig `sidebar` block via `admin/layout.html.twig`. The Java port
  reuses the single shared `layout` fragment, which owns the sidebar (About + RSS), so these
  page-specific actions are placed at the top of the `main` region instead. Same actions, same
  targets — only their on-page position differs.
- **Delete CSRF token.** The PHP `_delete_form.html.twig` posted a custom hidden field
  `token = csrf_token('delete')`. The Java delete forms (edit + show) emit Spring Security's
  standard `_csrf` hidden input instead (`th:name="${_csrf?.parameterName}"`), matching the
  AdminBlogController delete handler which relies on Spring's built-in CSRF (already recorded for
  task 11.1). Framework-mechanics difference, no behavioral-parity impact.
- **Delete confirmation.** The PHP `_delete_post_confirmation` used an unobtrusive JS
  `data-confirmation="true"` hook (admin.js). The port uses a plain `onsubmit="return confirm(...)"`
  handler to reproduce the "are you sure" prompt without the asset pipeline.
- **Admin show content not Markdown-rendered.** The PHP `admin/blog/show.html.twig` ran
  `post.content | markdown_to_html | sanitize_html`. The public post view is handed a pre-rendered
  `contentHtml` attribute, but the admin show handler (task 11.1) exposes only the `post` entity
  with no pre-rendered HTML. The admin template therefore outputs the raw Markdown `post.content`
  verbatim as escaped text (inside a `<pre>` to preserve line breaks) via `th:text`, rather than
  rendering it to HTML. Behavioral deviation limited to the admin preview page.
- **Tags input.** The PHP `TagsInputType` rendered a single comma-separated text input backed by a
  view transformer. The port binds a single text input to the `PostDto.tags` `List<String>` via
  `th:field="*{tags}"` (Spring's default collection binding splits on commas), preserving the
  comma-separated UX.
- **i18n literals.** All labels/titles are plain English literals (e.g. "Post List", "Create post",
  "Save and create new", "Back to list", "Edit post #{id}", "No posts found."); the PHP `|trans`
  keys are deferred to the MessageSource wiring in task 12.1.
- **Test tightening.** With the `admin/blog/index` template now present, `AdminBlogControllerTest.adminListsOwnPosts`
  was tightened from a best-effort render (previously swallowing the missing-template exception) to
  assert HTTP 200, view name `admin/blog/index`, and the `posts` model attribute. Full suite: 6/6 green.


## Task 14.1 — Static assets (CSS/JS/images)

- **Frontend build pipeline replaced.** The PHP app used Symfony AssetMapper + importmap
  (`assets/app.js`, `assets/controllers.json`) compiling Bootstrap + Bootswatch ("Flatly")
  SCSS into a versioned bundle. The Java app has no Node/webpack/AssetMapper build: Bootstrap 5
  is loaded via CDN (task 8.3) and the app's own custom rules from `assets/styles/app.scss`
  were hand-ported to a plain, dependency-free `src/main/resources/static/css/app.css`, linked
  from `layout.html` after the Bootstrap CDN link so it overrides. Rendering is close-to-parity;
  the exact Bootswatch "Flatly" theme tokens are not reproduced (stock Bootstrap 5 + overrides).
- **Stimulus JS controllers not ported.** The PHP Stimulus controllers (`csrf_protection_controller.js`,
  `login-controller.js`) and helper JS (`assets/js/*`, jQuery/highlight/flatpickr/tagsinput) provided
  progressive-enhancement behaviors (client-side CSRF token injection, login autofill, syntax
  highlighting, date pickers, tag inputs). None of these JS behaviors were ported — server-side
  rendering and Spring Security CSRF cover the functional needs; the enhancements are cosmetic.
- **Static files served directly by Spring Boot.** `favicon.svg`, `favicon.ico`,
  `apple-touch-icon.png`, and `robots.txt` were copied verbatim from `source-php/public/` into
  `src/main/resources/static/` (served at `/`). Added `<link rel="icon" type="image/svg+xml">`
  (svg favicon) to the `layout.html` head to match the PHP base layout's `asset('favicon.svg')`.

## Seed data — user email domain

- **Seed emails use `@example.com`, not `@symfony.com`:** the upstream PHP fixtures
  (`AppFixtures.php`) create demo users with addresses like `jane_admin@symfony.com`.
  `symfony.com` is a real, registered domain, so those addresses could correspond to
  real mailboxes or be mistaken for real user data. The Java seed
  (`db/migration/V2__seed.sql`) instead uses the RFC 2606 reserved domain
  `example.com` (`jane_admin@example.com`, `tom_admin@example.com`,
  `john_user@example.com`), which is guaranteed never to resolve to a real mailbox.
- **Parity impact: none.** Authentication is by username, tests assert on usernames
  and roles (not email literals), and no parity scenario compares email values. The
  mail notification path only ever *sends to* the seeded author's address, and both
  the PHP demo (`null://null` transport) and the Java port (non-fatal send failure)
  discard mail in dev/test.
- The unit-test helper in `DatabaseUserDetailsServiceTest` was updated to fabricate
  addresses on the same reserved domain for consistency.
