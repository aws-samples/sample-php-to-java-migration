# Config schema

`discover-models.php --config=model-discovery.config.json` accepts an optional config file. Every
field has a sane default — an app with no local base classes and standard casts needs no config
file at all. Config is what lets the same scanner script run unmodified across 100+ differently
structured apps: per-app variance (custom base classes, non-standard cast types, extra excluded
paths) lives in a config file next to that app, never in the script.

```json
{
  "baseClasses": [
    "Illuminate\\Database\\Eloquent\\Model",
    "App\\Models\\LegacyBaseModel"
  ],
  "excludePaths": ["vendor", "node_modules", "storage", "bootstrap/cache", "tests", ".git"],
  "traitFlags": ["SoftDeletes"],
  "castTypeOverrides": {
    "money": "BigDecimal",
    "encrypted": "String"
  }
}
```

## `baseClasses`

Default: `["Illuminate\\Database\\Eloquent\\Model"]`.

Eloquent model detection walks the `extends` chain looking for a match against this list — it does
**not** need every custom base class in the app listed here. If an app defines its own
`App\Model extends Illuminate\Database\Eloquent\Model` (a common pattern, e.g. to add a shared
helper method across all models), the scanner discovers that automatically by walking through
`App\Model`'s own `extends` clause — you only need to add an entry here if a model's real base
class lives *outside* the scanned codebase and isn't a rename/wrapper of the default (e.g. a
second ORM's base class, or a base class pulled in via a Composer package whose source isn't
present locally, or intentionally excluded via `excludePaths`).

Multiple entries are additive (any match anywhere in the chain counts), not exclusive — useful for
an app mid-migration between ORMs, or one with a couple of independent model hierarchies.

## `excludePaths`

Default: `["vendor", "node_modules", "storage", "bootstrap/cache", "tests", "Tests", ".git"]`.

Paths are matched as a leading path segment relative to `--root` (e.g. `"tests"` excludes
`<root>/tests/**` but not `<root>/app/tests_helper.php`). Add app-specific generated/vendored
directories here rather than teaching the scanner about them — e.g. a monorepo with a `legacy/`
tree that shouldn't be scanned yet.

## `traitFlags`

Default: `["SoftDeletes"]`.

Currently only used to decide the `softDeletes` boolean on Eloquent model entries. Add other trait
names here if a future manifest consumer needs a similar boolean flag surfaced at the top level
(the full trait list is always in `traits[]` regardless of this setting — `traitFlags` only
controls which ones get their own dedicated field).

## `castTypeOverrides`

Default: `{}` — falls back to the built-in `DEFAULT_CAST_MAP` (Eloquent casts) and
`DOCTRINE_TYPE_MAP`/`PHP_TYPE_MAP` (Doctrine columns).

Keys are matched against **both** Eloquent cast strings (e.g. `"decimal:2"` is tried whole, then
its base `"decimal"`) and Doctrine type constants (e.g. `"STRING"`) and bare PHP type names (e.g.
`"DateTimeImmutable"`) — there's a single override map, not one per strategy, since a customer
override like `"money" => "BigDecimal"` is meaningful regardless of which ORM declared it. Use
this when the built-in defaults are wrong for a specific app's conventions — e.g. an app that
stores money as a plain Eloquent `"money"` custom cast class, or a Doctrine app using a custom
DBAL type the built-in map doesn't know about.

Any cast/type with no entry here and no built-in default mapping produces a `FLAG:` value in the
manifest and a corresponding `flags[]` entry — never a silent guess.

## Per-app config convention for a 100+-app portfolio

Keep one `model-discovery.config.json` per app, colocated with that app's source (e.g.
`<app-root>/.migration/model-discovery.config.json`), rather than a single shared config file
covering all apps. Most apps need zero overrides and can omit the file entirely; only write one
when a specific app's `baseClasses`/`castTypeOverrides` genuinely diverge from the defaults. This
keeps the scanner script itself identical across every app in the portfolio — the only thing that
varies is an optional, app-local override file, which is what makes the tool actually generic
rather than Laravel-shaped with an escape hatch bolted on.
