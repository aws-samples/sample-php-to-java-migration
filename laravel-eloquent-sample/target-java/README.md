# BookStack Core Slice — Java/Spring Boot

A bounded proof-of-concept slice of [BookStack](https://github.com/ssddanbrown/BookStack)
(`Book`/`Chapter`/`Page`/`User`, read-only REST API), migrated from Laravel/Eloquent to Spring
Boot. **This is not a full BookStack migration** — see Scope below.

Built using the `php-model-discovery` skill's manifest (model fields/casts/relationships/hidden)
as the seed for these entities, following `php-to-java-migration`'s data-model translation phase
at a deliberately small scale.

## Scope

Ported: `Book`, `Chapter`, `Page`, `User` entities (structural fields only) and a read-only REST
API (`list`/`get`/child-listing). **Not ported**: auth (password/roles/LDAP/SAML/OIDC), the
permissions system, search, exports, attachments, revisions, comments, soft-delete-via-`Deletion`
(BookStack moved off a `deleted_at` column to a dedicated entity — out of scope here), cover
images, templates, sort rules, and all business-logic controllers. Full parity for those would
require carrying every model through `php-to-java-migration`'s Phases 1–8, including the parity
harness against real BookStack traffic — this slice skips that rigor deliberately, in exchange for
being small enough to actually finish and deploy.

## Deliberate simplifications

- **Database**: runs on an in-memory H2 database (`ddl-auto: create-drop`, `DataSeeder` inserts a
  few sample rows), not a connection to a real BookStack MySQL instance. Swapping in real MySQL +
  a schema derived from the actual migration history is real remaining work, not done here.
- **Schema fidelity**: columns reflect BookStack's *original* `create_*_table` migrations plus the
  fields visible in each model's own `@property` docblocks — not a full trace of every `ALTER
  TABLE` migration since 2015 (BookStack even restructured these tables in a 2025 migration). Good
  enough for this slice; not something to trust as exact for a real cutover.
- **API shape mirrors Eloquent's `$hidden`**: `Page.html`/`.markdown`/`.text` are `@JsonIgnore`
  because the php-model-discovery manifest recorded them in `Page`'s Eloquent `$hidden` list — this
  is a directly-sourced parity decision, not an arbitrary one. A real content endpoint would need
  an explicit DTO that deliberately re-includes them, as BookStack's own API does.

## Run locally

```bash
mvn spring-boot:run
```

Then: `curl localhost:8080/api/books`, `curl localhost:8080/api/books/1/chapters`, etc.

## Build a runnable jar

```bash
mvn package
java -jar target/bookstack-core-slice-1.0.0-SNAPSHOT.jar
```
