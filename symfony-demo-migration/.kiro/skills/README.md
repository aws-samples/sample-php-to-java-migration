# Skills — shared, not local

This project intentionally has **no local skill copy**. It uses the shared, canonical
migration skill installed at the **user level**:

```
~/.kiro/skills/php-to-java-migration/
├── SKILL.md
└── references/
    ├── symfony-to-spring.md
    ├── laravel-to-spring.md
    ├── semantic-traps.md
    ├── composer-to-maven.md
    └── loop-architecture.md
```

## Why user-level (single source of truth)

- **Improve once, use everywhere.** Refinements discovered during this Symfony migration
  update the same skill that every other project (including the DVWA project) uses.
- **No drift / no ambiguity.** A workspace-level skill with the same `name` as the user-level
  one would conflict and silently diverge. Keeping exactly one definition avoids that.

## How to evolve the skill

Edit the files under `~/.kiro/skills/php-to-java-migration/`. Because it's user-level, the
change is immediately available in this workspace and all others. Consider version-controlling
`~/.kiro/skills/` separately so skill history is tracked.
