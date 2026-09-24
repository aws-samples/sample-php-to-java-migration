# Manifest schema

`discover-models.php` writes one JSON manifest per app. It always contains
`eloquentModelsFound`, `doctrineEntitiesFound`, and `doctrineXmlEntitiesFound` counts — all three
extraction strategies run unconditionally on every scan, so an app using only one mapping style
simply produces zero results from the other two. There is no framework flag to set; the manifest
itself tells you which strategy(ies) fired.

```json
{
  "app": "bookstack",
  "root": "/path/to/app",
  "generatedBy": "discover-models.php",
  "summary": {
    "filesScanned": 1603,
    "classesDiscovered": 462,
    "eloquentModelsFound": 37,
    "doctrineEntitiesFound": 0,
    "doctrineXmlEntitiesFound": 0,
    "flaggedForReview": 55
  },
  "models": [ /* Eloquent, Doctrine-attribute, and/or Doctrine-XML entries, see below */ ],
  "unresolvedBaseClasses": [
    {"class": "App\\Models\\Legacy\\Widget", "extends": "SomeUnresolvedParent", "file": "..."}
  ]
}
```

`unresolvedBaseClasses` lists classes whose extends-chain resolved (by short name) to something
that looks like one of the configured `baseClasses` but couldn't be confirmed — usually because
the parent lives in a `use`-imported namespace the scanner couldn't fully resolve. Check these by
hand; they're either real models worth adding to config, or false positives.

## `flags`

Every model/entity entry carries a `flags` array — plain-English notes on anything the scanner
could not resolve mechanically: a computed property value, a relationship it couldn't identify,
an accessor/mutator with logic that needs manual porting, an unmapped cast/column type. **Treat a
non-empty `flags` array as required reading before generating Java for that class** — the tool is
designed to flag ambiguity rather than guess, per [[php-model-discovery]]'s core principle. A
model with zero flags is not "done" either — it means nothing *mechanical* looked ambiguous, not
that the business logic was reviewed.

## Eloquent model entry

Extracted by walking the inheritance chain (not folder convention) and reading declared
properties/traits/methods — including up the chain, since PHP property and trait inheritance
means a `$casts` or `use SoftDeletes;` declared only on an abstract ancestor still applies to
every concrete subclass.

```json
{
  "class": "BookStack\\Entities\\Models\\Page",
  "file": "/abs/path/Page.php",
  "parent": "BookStack\\Entities\\Models\\BookChild",
  "confidence": "medium",
  "table": null,
  "primaryKey": "id",
  "fillable": ["name", "priority"],
  "guarded": [],
  "hidden": ["html", "markdown", "text", "pivot", "deleted_at", "entity_id", "entity_type"],
  "visible": [],
  "appends": [],
  "casts": [
    {"field": "draft", "phpCast": "boolean", "javaType": "Boolean"}
  ],
  "softDeletes": true,
  "traits": ["HasFactory", "SoftDeletes", "HasCreatorAndUpdater"],
  "relationships": [
    {"method": "chapter", "type": "belongsTo", "related": "Chapter", "lineStart": 58, "lineEnd": 61}
  ],
  "accessorsAndMutators": [
    {"method": "getFooAttribute", "attribute": "foo", "kind": "accessor", "style": "legacy", "lineStart": 10, "lineEnd": 14}
  ],
  "scopes": [
    {"method": "scopeVisible", "lineStart": 48, "lineEnd": 53}
  ],
  "flags": ["scopeVisible() is query-building logic (lines 48-53) — has no declarative JPA equivalent, port manually"],
  "framework": "eloquent"
}
```

Field notes:
- **`table` / `primaryKey` / `fillable` / etc. being `null`/`[]`**: for `table`/`primaryKey`, `null`
  usually means the model relies on Eloquent's naming-convention default (snake_case plural of the
  class name / `id`) rather than an explicit declaration — confirm the real table name against the
  migration, don't assume the class name maps directly. For the list properties, an empty array
  means either "declared as empty" or "not declared anywhere in the chain" — both are
  behaviorally the same at runtime (nothing restricted/hidden), so the manifest doesn't need to
  distinguish them for consumers.
- **`parent`**: the nearest ancestor still declared in the scanned codebase (not the ultimate
  vendor base class). Present so a Java generator can mirror the PHP hierarchy with
  `@MappedSuperclass` / joined inheritance instead of flattening every model independently.
- **`confidence`**: `"high"` means the extends-chain resolved to a configured base class with a
  fully-qualified match at every hop. `"medium"` or lower means at least one hop was resolved by
  short-name heuristic rather than a confirmed namespace — always listed in `flags` too, and worth
  a quick human confirmation before trusting the rest of the entry.
- **`relationships`**: only relationships declared via a direct `$this->hasMany(...)`-style call
  inside the method body are detected. A method that returns another method's relationship
  builder (e.g. `public function revisions() { return $this->allRevisions()->where(...); }`)
  is invisible to this static scan — it has no literal relationship call of its own. This is a
  known limitation, not a bug: resolving it would require call-graph analysis across methods,
  which this tool deliberately doesn't attempt. Cross-check relationship counts against the
  model's actual usage in controllers/views if a class looks relationship-light.
- **`accessorsAndMutators`**: always flagged for manual review — these contain arbitrary PHP logic
  that must be read and re-implemented, never assumed to be a plain field passthrough. Note that
  the legacy-style pattern (`getFooAttribute`) is detected by name convention alone, which can
  false-positive on a coincidentally-named helper method (e.g. `getRawAttribute()` as a generic
  helper, not a real Eloquent accessor) — worth a glance, not a guarantee.

## Doctrine entity entry

Extracted by finding classes carrying `#[ORM\Entity]` (Doctrine entities are typically plain
classes with no common base, so inheritance-chain detection doesn't apply) and reading the
attribute groups on each typed property.

```json
{
  "class": "App\\Entity\\Post",
  "framework": "doctrine",
  "file": "/abs/path/Post.php",
  "table": "symfony_demo_post",
  "id": {"field": "id", "phpType": "?int", "generationStrategy": "AUTO"},
  "columns": [
    {"field": "title", "columnName": null, "phpType": "?string", "doctrineType": "STRING", "javaType": "String", "nullable": false, "unique": false}
  ],
  "relationships": [
    {
      "field": "comments", "type": "OneToMany", "target": "Comment",
      "mappedBy": "post", "inversedBy": null,
      "joinColumn": null, "joinTable": null,
      "orderBy": {"publishedAt": "DESC"},
      "cascade": ["persist"], "orphanRemoval": true, "line": 70
    }
  ],
  "flags": ["content: String — mark the JPA field @Lob (line 56)"]
}
```

Field notes:
- **`generationStrategy`**: reported as Doctrine's own term (`AUTO`, `IDENTITY`, `SEQUENCE`,
  `NONE`, or whatever the `#[ORM\GeneratedValue(strategy: ...)]` argument literally says — `AUTO`
  if the attribute is bare). Map `AUTO` → JPA `GenerationType.IDENTITY` for MySQL-backed apps (the
  common case); confirm against the actual DB driver before assuming this for others.
- **`columnName: null`**: means the source didn't set an explicit `name:` on `#[ORM\Column]` or
  `#[ORM\JoinColumn]` — Doctrine's own naming-convention default applies (property name as-is for
  columns, `<property>_id` for join columns). Don't assume the manifest's `null` means "no column
  exists" — it means "name comes from convention, not explicit declaration."
- **`nullable: false` by default**: this is Doctrine's actual `#[ORM\Column]` default regardless
  of whether the PHP property itself has a `?` (nullable-in-PHP-memory) type hint — the two are
  independent. Don't infer SQL nullability from the PHP type hint; only an explicit
  `nullable: true` argument (or an unset primary key before first persist) means the column is
  actually nullable.
- **`javaType` starting with `FLAG:`**: no mapping exists in `DOCTRINE_TYPE_MAP`/`PHP_TYPE_MAP` (or
  a customer override) for that Doctrine/PHP type — always paired with a `flags` entry citing the
  field and line.
- **`orderBy`**: an object of `{field: "ASC"|"DESC", ...}` when the source's `#[ORM\OrderBy]`
  attribute set a direction, matching the PHP array-literal argument shape.

## Doctrine XML-mapped entity entry

Some apps (Sylius among them) keep domain model classes framework-agnostic — no Doctrine
attribute on the class at all — and map them externally via `*.orm.xml` files instead, so
Components can be reused without a hard Doctrine dependency. This entry type is found entirely
independently of the PHP source: the scanner walks for `*.orm.xml` files and parses their content
directly, never reading the mapped class's own file.

```json
{
  "class": "Sylius\\Component\\Core\\Model\\Product",
  "framework": "doctrine-xml",
  "file": "/abs/path/Product.orm.xml",
  "kind": "mapped-superclass",
  "table": "sylius_product",
  "id": null,
  "fields": [
    {"field": "variantSelectionMethod", "column": "variant_selection_method", "type": "string", "length": null, "nullable": false, "unique": false}
  ],
  "relationships": [
    {
      "field": "mainTaxon", "type": "many-to-one",
      "target": "Sylius\\Component\\Taxonomy\\Model\\TaxonInterface",
      "mappedBy": null, "inversedBy": null,
      "joinColumn": {"name": "main_taxon_id", "nullable": true},
      "joinTable": null, "orderBy": null, "orphanRemoval": false
    }
  ],
  "flags": [
    "mainTaxon: target-entity is an interface (Sylius\\Component\\Taxonomy\\Model\\TaxonInterface) — Doctrine resolves this to a concrete class via ResolveTargetEntityListener at runtime; confirm the concrete implementation before choosing the Java relationship's target type"
  ]
}
```

Field notes:
- **`kind`**: `"entity"` or `"mapped-superclass"` (from the XML root element) — maps directly to
  JPA's `@Entity` vs `@MappedSuperclass`. A `mapped-superclass` has no table of its own; its
  fields/relationships are inherited by whichever concrete `entity` extends it.
- **`id: null`**: expected and *not* flagged when `kind` is `mapped-superclass` (common — the
  concrete entity elsewhere provides or inherits the id). Flagged when `kind` is `entity`, since a
  concrete entity with no id anywhere in its chain would be a real problem.
- **`target` is an interface FQCN (ends in `Interface`)**: this is Sylius's
  `ResolveTargetEntityListener` pattern — the XML deliberately maps to an interface, and a
  separate Symfony service config (`doctrine.orm.resolve_target_entities`, usually in a bundle's
  `services.xml` or `config/packages/doctrine.yaml`) resolves it to a concrete class at runtime.
  Java/JPA has no equivalent runtime resolution for relationship *types* — find the configured
  concrete implementation before deciding what the JPA relationship should actually point to.
  Always paired with a `flags` entry; never silently resolved by the scanner, since the
  resolution lives in Symfony config, not in anything token/XML scanning alone can see.
- **`relationships`**: same shape as the Doctrine-attribute entries (`joinColumn`, `joinTable`,
  `orderBy` with direction, `orphanRemoval`) — the two Doctrine strategies share a relationship
  shape deliberately, since they're two mapping styles for the same underlying ORM concepts.

## Cross-model consumption for Phase 5 (data model translation)

Feed `models[]` into [[php-to-java-migration]] Phase 5 as the seed for entity generation: each
entry maps to one JPA `@Entity`, `columns`/`casts` seed the type-map lookups in `RULEBOOK.md`, and
every `flags` entry becomes either a `GAP_INVENTORY.md` line (if it's a refactor-not-translate
construct) or a `// TODO(port)` marker in the generated class (per Phase 7's rule that ambiguity
gets a marker, never a guess).
