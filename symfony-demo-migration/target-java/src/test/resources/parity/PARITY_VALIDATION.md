# Parity Harness Validation (the judge)

This document records how the parity harness — the objective referee for the
PHP&nbsp;&rarr;&nbsp;Java migration — is proven **trustworthy** before its verdict is relied on.
This is the **Phase 1 gate** of the migration skill: a judge is only useful if it *passes* on
equivalent responses and *fails* on divergent ones.

Validation has two layers:

1. **Pure-JVM judge validation** — always runs, no Docker required.
2. **Live-stack validation** — Docker-gated, run in a Docker-enabled environment (e.g. CI).

---

## 1. Pure-JVM judge validation (always runs)

The comparison logic (`ResponseComparison`) is exercised directly with deliberately divergent
`ParityResponse` pairs. These tests need no PHP or Java server, so they run on every build.

- `JudgeValidationTest` — the Phase 1 gate. Drives the **aggregate** judge
  (`ResponseComparison.compareAll`, the same method the parity suite uses) through every breakage
  class and confirms it also tolerates legitimate noise:
  - **(a) status code differs** — e.g. `200` vs `403`, `200` vs `404` → mismatch reported.
  - **(b) redirect `Location` path differs** — e.g. `/blog/` vs `/login`, and one-side-redirect →
    mismatch reported.
  - **(c) JSON structure differs** — renamed/dropped key, changed value type, changed array length
    → mismatch reported.
  - **(d) normalized HTML body differs** — different visible text, or a dropped `<form>` seen
    *through* CSRF/timestamp noise → mismatch reported.
  - **MATCH on noise only** — differing CSRF `_token`, session id (`PHPSESSID`/`JSESSIONID`),
    RSS/ISO timestamps, and inter-tag whitespace → reported as a match.
  - Property-based checks: *every* status-code flip is detected; arbitrary CSRF token values never
    cause a false mismatch.
- `ResponseComparisonTest` — granular unit tests for each individual comparator and the
  normalization helpers.

Run them:

```bash
# from symfony-demo-migration/target-java
./mvnw -q test -Dtest=JudgeValidationTest,ResponseComparisonTest
```

Both classes passing is the gate: the judge catches real breakage and tolerates incidental
cross-stack noise.

---

## 2. Live-stack validation (Docker-gated)

This layer proves the *end-to-end* harness — real HTTP requests issued to both stacks — behaves
correctly. It requires a Docker engine and the side-by-side compose environment, so it is **gated**
and skips cleanly when Docker is unavailable (`AbstractParityTest.parityEnvironmentAvailable()`).
Run the following in a Docker-enabled environment (local workstation with Docker, or CI).

### 2.1 Bring up the side-by-side stack

```bash
# from symfony-demo-migration/target-java/src/test/resources/parity
docker compose -f compose-parity.yml up -d --build

# PHP baseline   → http://localhost:8000
# Java migration → http://localhost:8080
```

Wait until both targets answer (the parity tests probe both ports before running):

```bash
curl -sSf http://localhost:8000/blog/ >/dev/null && echo "PHP up"
curl -sSf http://localhost:8080/blog/ >/dev/null && echo "Java up"
```

### 2.2 Confirm the harness PASSES against the unmodified PHP baseline

With both stacks healthy, the environment-gated parity tests now execute instead of skipping:

```bash
# from symfony-demo-migration/target-java
./mvnw -q test -Dtest='Parity*Test,*ParityTest'
```

Expected: parity tests run (not skipped) and pass — the Java port matches the PHP baseline on the
covered flows. `ParityInfrastructureTest` confirms both targets are reachable and their blog-index
status codes match.

> Sanity self-check: also confirm the tests actually *ran*. If Maven reports them as skipped, the
> compose stack is not reachable on the expected ports — re-check step 2.1 before trusting a
> "green" result.

### 2.3 Confirm the harness FAILS against deliberately broken PHP

A judge that only ever passes is worthless. Introduce a deliberate break in the **PHP baseline**
and confirm the harness turns red. Pick any one of these:

- **Flip a status / drop a route** — in the PHP container, make a covered page return the wrong
  status (e.g. force `/blog/posts/{slug}` to `404`, or make `/login` return `200` where a redirect
  is expected).
- **Drop a response field** — remove a key from a JSON response (e.g. delete a field from the RSS
  or search payload) so the structural comparison diverges.
- **Alter body structure** — remove an element the template always renders (e.g. delete the comment
  `<form>` from the post-detail template) so the normalized-HTML comparison diverges.

Apply the break, rebuild just the PHP service, and re-run the parity tests:

```bash
# from symfony-demo-migration/target-java/src/test/resources/parity
docker compose -f compose-parity.yml up -d --build php-baseline

# from symfony-demo-migration/target-java
./mvnw -q test -Dtest='Parity*Test,*ParityTest'
```

Expected: the parity suite **fails**, and the assertion message names the divergence (status code,
redirect path, JSON keys/type, or normalized body). This is the proof the judge detects real
breakage end to end.

### 2.4 Restore and tear down

```bash
# revert the deliberate break in the PHP baseline, then:
# from symfony-demo-migration/target-java/src/test/resources/parity
docker compose -f compose-parity.yml down -v
```

---

## Gate summary

| Layer | Requires Docker | Proves | Status here |
|-------|-----------------|--------|-------------|
| Pure-JVM (`JudgeValidationTest`, `ResponseComparisonTest`) | No | Comparison logic passes on equivalent, fails on each breakage class, tolerates noise | ✅ runs on every build |
| Live-stack pass (2.2) | Yes | Harness passes against unmodified PHP baseline | ⏸️ Docker-gated (run in CI) |
| Live-stack fail (2.3) | Yes | Harness fails against deliberately broken PHP | ⏸️ Docker-gated (run in CI) |

The pure-JVM gate is satisfied in this environment. The live-stack steps are documented here and
execute automatically wherever the `compose-parity.yml` environment is up (e.g. CI), where they
stop skipping and become executable pass/fail checks.
