# Loop Architecture (Phases 7–8)

Concrete patterns for running the implement / review / fix loop. The principle behind all of it: **you fix the process that produces the code, not the code.** Recurring errors become rulebook amendments; the loop regenerates the affected files.

## The mechanical, resumable queue

"Done" means the target file exists on disk and passes its gate. Rebuild the queue from disk every run so an interruption costs nothing.

```bash
#!/usr/bin/env bash
# build_queue.sh — emit the next batch of PHP classes to translate.
# Pending = source .php exists but target .java does not.
SRC_ROOT="app"                 # PHP source root (skip vendor/)
DST_ROOT="src/main/java"       # Java target root
BATCH_SIZE="${1:-12}"

# Map a PHP path to its expected Java path (structure-preserving).
php_to_java() {
  local php="$1"
  echo "$DST_ROOT/${php#"$SRC_ROOT"/}" | sed 's/\.php$/.java/'
}

find "$SRC_ROOT" -name '*.php' -not -path '*/vendor/*' | while read -r php; do
  java="$(php_to_java "$php")"
  [ -f "$java" ] || echo "$php"
done | head -n "$BATCH_SIZE"
```

Order the queue leaf-first using the dependency map from Phase 0 (files with no un-migrated internal dependencies go first). A simple approach: topologically sort the dependency graph, then filter through `build_queue.sh`.

## Implementer agents

Fan out one subagent per file (or small group) in a batch. Each agent's brief:

- Translate strictly by `RULEBOOK.md`. The rulebook is law; do not improvise idioms.
- Do not over-refactor. A blunt instruction helps: "Translate faithfully. The compiler and tests will catch mistakes in the next step. Do not redesign."
- Anything you cannot translate confidently: emit `// TODO(port): <reason>` and move on. Do not guess.
- Capture observed PHP behavior in JUnit tests, bug-for-bug. Record intentional deviations in `BEHAVIOR_CHANGES.md`.

Right-size the model: high-volume implementation can run on a smaller/faster model. Reserve the strongest effort for reviewers and rule-writers, because a bad rule propagates to every file.

## Where the compiler sits

- **Fast build** (a class compiles in seconds): run the compiler inside the loop for immediate feedback.
- **Slow build** (minutes): ban the compiler from the loop; keep the loop pure translation and defer compilation to Phase 8. Otherwise agents serialize on the build.

State the choice explicitly in `RULEBOOK.md` so every agent behaves the same way.

## Adversarial review

Two reviewer subagents, separate/fresh contexts, evaluate each batch:

- Every finding must cite the specific `RULEBOOK.md` rule it violates. A finding with no rule behind it means the rulebook has a gap — add the rule.
- Reviewer disagreement goes to a third agent for a tie-break.
- Reviewers hunt for divergence from PHP behavior, not style preferences.

When a reviewer catches the **same** mistake across files, that is a loop fix, not a file fix:

1. Add one sentence to `RULEBOOK.md`.
2. Delete the affected `.java` files (they become "pending" again).
3. Re-run the batch. Never hand-edit code to match a rule.

## Fixer agents (Phase 8)

The compiler, smoke test, and test suite each produce a mechanical error list. That list *is* the queue — the to-do list writes itself.

- Group errors by root cause before assigning fixers (e.g., "all cyclic-import errors," "all null-handling errors"). Fix the category, not the instance.
- Adversarial reviewers check every fix.
- Systemic categories get pushed upstream into the rulebook and regenerated.

## Build daemon

Serialize the most expensive operation. One process owns rebuilding the artifact and re-running affected tests.

```bash
#!/usr/bin/env bash
# build_daemon.sh — the ONLY process allowed to rebuild + retest.
# Fixer agents drop patch markers; the daemon batches, builds once, reports.
PATCH_DIR=".migration/patches"
RESULT_DIR=".migration/results"
mkdir -p "$PATCH_DIR" "$RESULT_DIR"

while true; do
  if compgen -G "$PATCH_DIR/*.applied" > /dev/null; then
    ./mvnw -q -T1C test > "$RESULT_DIR/last-build.log" 2>&1
    status=$?
    mv "$PATCH_DIR"/*.applied "$RESULT_DIR/" 2>/dev/null || true
    echo "build exit=$status @ $(date -u +%FT%TZ)" >> "$RESULT_DIR/history.log"
  fi
  sleep 10
done
```

Fixers write patches and mark them ready; the daemon batches them, rebuilds once, re-runs the affected tests, and feeds results back. This prevents N agents from each triggering a costly rebuild.

## Extending a thin referee

If the inherited/authored test corpus is small, have Claude design its own end-to-end suite and run it autonomously overnight, fixing failures and re-running for several nights. Each night surfaces paper cuts a hand-written scenario list would miss. The PHP codebase remains the ground truth for every diff.

## What to watch, and what to ignore

- Watch **patterns**: a failure repeating across files is a missing rule.
- Ignore **individual failures**: that is the loop's job — fixer agents burn them down.
- Track token spend: it concentrates in loops. Design loops deliberately; do not run every step on the largest model.
