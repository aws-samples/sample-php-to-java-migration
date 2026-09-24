<?php
/**
 * discover-models.php — generic ORM model discovery/extraction for PHP codebases.
 *
 * Finds model classes by inheritance chain (token-based, not by folder convention),
 * extracts the fields, casts, relationships, and behavior-relevant metadata a Java/JPA
 * port needs for 1:1 parity, and emits a normalized JSON manifest.
 *
 * Zero Composer dependencies — uses only PHP's built-in tokenizer (token_get_all), so it
 * runs against any target app regardless of its own dependency/health state, without
 * booting the app or touching its autoloader.
 *
 * Usage:
 *   php discover-models.php --root=/path/to/app [--config=/path/to/model-discovery.config.json] [--out=/path/to/manifest.json] [--pretty]
 *
 * Requires PHP 8.0+ to RUN this script (it only tokenizes source text, never executes
 * the target app's code, so the target app's own PHP version is irrelevant).
 */

declare(strict_types=1);

// ---------------------------------------------------------------------------
// Defaults
// ---------------------------------------------------------------------------

const DEFAULT_BASE_CLASSES = [
    'Illuminate\Database\Eloquent\Model',
];

const DEFAULT_EXCLUDE_PATHS = [
    'vendor', 'node_modules', 'storage', 'bootstrap/cache', 'tests', 'Tests', '.git',
];

const DEFAULT_TRAIT_FLAGS = ['SoftDeletes'];

// PHP Eloquent cast type -> suggested Java type. Overridable via config castTypeOverrides.
const DEFAULT_CAST_MAP = [
    'int' => 'Long', 'integer' => 'Long',
    'string' => 'String',
    'bool' => 'Boolean', 'boolean' => 'Boolean',
    'float' => 'Double', 'double' => 'Double', 'real' => 'Double',
    'array' => 'FLAG:List/Map — shape must be inferred from usage',
    'json' => 'FLAG:JSON — shape must be inferred from usage',
    'object' => 'FLAG:Object — shape must be inferred from usage',
    'collection' => 'FLAG:List — element type must be inferred from usage',
    'date' => 'LocalDate',
    'datetime' => 'LocalDateTime',
    'immutable_date' => 'LocalDate',
    'immutable_datetime' => 'LocalDateTime',
    'timestamp' => 'Instant',
    'encrypted' => 'FLAG:String — decrypt/encrypt boundary must be re-implemented',
    'encrypted:array' => 'FLAG:Map — decrypt/encrypt boundary must be re-implemented',
    'decimal' => 'BigDecimal',
];

const RELATIONSHIP_METHODS = [
    'hasOne', 'hasMany', 'belongsTo', 'belongsToMany',
    'morphOne', 'morphMany', 'morphTo', 'morphToMany', 'morphedByMany',
    'hasOneThrough', 'hasManyThrough',
];

// ---------------------------------------------------------------------------
// CLI entry point
// ---------------------------------------------------------------------------

function main(array $argv): int
{
    $args = parseArgs($argv);
    if (!isset($args['root']) || !is_dir($args['root'])) {
        fwrite(STDERR, "Usage: php discover-models.php --root=/path/to/app [--config=path] [--out=path] [--pretty]\n");
        return 1;
    }

    $root = rtrim($args['root'], '/');
    $config = loadConfig($args['config'] ?? null);

    $files = collectPhpFiles($root, $config['excludePaths']);
    fwrite(STDERR, "Scanned file count: " . count($files) . "\n");

    // classIndex is kept lightweight (no token arrays) for the whole run — a monorepo-scale
    // codebase can have thousands of files, and holding every file's full tokenized source in
    // memory simultaneously just in case it's needed later runs out of memory fast (this exact
    // bug surfaced scanning Sylius: 4730 files exhausted PHP's default 128MB limit). Full token
    // data is reloaded on demand, per file, only for the much smaller set of files that turn out
    // to actually contain a confirmed model or one of its ancestors — see loadFullClassInfo().
    $classIndex = [];
    foreach ($files as $file) {
        foreach (scanFile($file) as $fqcn => $info) {
            unset($info['tokens'], $info['lineOf']);
            $classIndex[$fqcn] = $info;
        }
    }
    fwrite(STDERR, "Classes discovered: " . count($classIndex) . "\n");

    $models = [];
    $unresolved = [];
    foreach ($classIndex as $fqcn => $info) {
        $resolution = resolveIsModel($fqcn, $classIndex, $config['baseClasses']);
        if ($resolution['isModel']) {
            $models[$fqcn] = extractModelDetails($fqcn, $classIndex, $config, $resolution['confidence']) + ['framework' => 'eloquent'];
        } elseif ($resolution['confidence'] === 'unresolved-extends') {
            $unresolved[] = ['class' => $fqcn, 'extends' => $info['extendsRaw'], 'file' => $info['file']];
        }
    }

    // Doctrine entities are found independently (by attribute, not inheritance) — a codebase
    // using one ORM simply yields zero matches from the other strategy, so both always run.
    $doctrineEntities = [];
    foreach ($files as $file) {
        foreach (scanDoctrineFile($file) as $fqcn => $entityInfo) {
            $doctrineEntities[$fqcn] = extractDoctrineEntityDetails($fqcn, $entityInfo, $config);
        }
    }
    fwrite(STDERR, "Doctrine entities discovered: " . count($doctrineEntities) . "\n");

    // Independent of both of the above: some apps map entities via *.orm.xml instead of
    // attributes, keeping the PHP classes themselves framework-agnostic. Config file presence
    // drives this, not anything in PHP source, so it's discovered by a completely separate file
    // walk, not the collectPhpFiles() list.
    $xmlEntities = [];
    foreach (collectXmlMappingFiles($root, $config['excludePaths']) as $file) {
        foreach (scanXmlMappingFile($file) as $fqcn => $entity) {
            $xmlEntities[$fqcn] = $entity;
        }
    }
    fwrite(STDERR, "Doctrine XML-mapped entities discovered: " . count($xmlEntities) . "\n");

    $allModels = array_merge(array_values($models), array_values($doctrineEntities), array_values($xmlEntities));

    $manifest = [
        'app' => basename($root),
        'root' => $root,
        'generatedBy' => 'discover-models.php',
        'summary' => [
            'filesScanned' => count($files),
            'classesDiscovered' => count($classIndex),
            'eloquentModelsFound' => count($models),
            'doctrineEntitiesFound' => count($doctrineEntities),
            'doctrineXmlEntitiesFound' => count($xmlEntities),
            'flaggedForReview' => array_sum(array_map(fn($m) => count($m['flags']), $allModels)),
        ],
        'models' => $allModels,
        'unresolvedBaseClasses' => $unresolved,
    ];

    $json = json_encode($manifest, ($args['pretty'] ?? false) ? JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES : JSON_UNESCAPED_SLASHES);
    if (isset($args['out'])) {
        file_put_contents($args['out'], $json . "\n");
        fwrite(STDERR, "Manifest written to {$args['out']}\n");
    } else {
        echo $json . "\n";
    }

    return 0;
}

function parseArgs(array $argv): array
{
    $out = ['pretty' => false];
    foreach (array_slice($argv, 1) as $arg) {
        if ($arg === '--pretty') { $out['pretty'] = true; continue; }
        if (preg_match('/^--([a-zA-Z]+)=(.*)$/', $arg, $m)) { $out[$m[1]] = $m[2]; }
    }
    return $out;
}

function loadConfig(?string $path): array
{
    $config = [
        'baseClasses' => DEFAULT_BASE_CLASSES,
        'excludePaths' => DEFAULT_EXCLUDE_PATHS,
        'traitFlags' => DEFAULT_TRAIT_FLAGS,
        'castTypeOverrides' => [],
    ];
    if ($path && is_file($path)) {
        $user = json_decode(file_get_contents($path), true) ?? [];
        foreach (['baseClasses', 'excludePaths', 'traitFlags'] as $key) {
            if (!empty($user[$key])) $config[$key] = $user[$key];
        }
        if (!empty($user['castTypeOverrides'])) $config['castTypeOverrides'] = $user['castTypeOverrides'];
    }
    return $config;
}

// ---------------------------------------------------------------------------
// File discovery
// ---------------------------------------------------------------------------

function collectPhpFiles(string $root, array $excludePaths): array
{
    $files = [];
    $iterator = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS)
    );
    foreach ($iterator as $fileInfo) {
        if ($fileInfo->getExtension() !== 'php') continue;
        $relative = substr($fileInfo->getPathname(), strlen($root) + 1);
        $excluded = false;
        foreach ($excludePaths as $ex) {
            if (str_starts_with($relative, $ex . '/') || $relative === $ex) { $excluded = true; break; }
        }
        if (!$excluded) $files[] = $fileInfo->getPathname();
    }
    return $files;
}

// ---------------------------------------------------------------------------
// Structural scan: namespace, use-imports, class declarations (token-based)
// ---------------------------------------------------------------------------

function scanFile(string $file): array
{
    $source = file_get_contents($file);
    $tokens = token_get_all($source);
    $count = count($tokens);

    // Single-char tokens ('{', '}', ';', etc.) come back as plain strings with no line
    // number attached — only complex tokens carry [id, text, line]. Precompute a line for
    // every index by walking the stream once, so callers can look up a line for ANY token.
    $lineOf = [];
    $line = 1;
    foreach ($tokens as $idx => $t) {
        $lineOf[$idx] = $line;
        $line += substr_count(is_array($t) ? $t[1] : $t, "\n");
    }

    $namespace = '';
    $uses = []; // alias => FQCN
    $classes = [];

    $i = 0;
    while ($i < $count) {
        $tok = $tokens[$i];

        if (is_array($tok) && $tok[0] === T_NAMESPACE) {
            [$namespace, $i] = readNameUntil($tokens, $i + 1, [';', '{']);
        } elseif (is_array($tok) && $tok[0] === T_USE) {
            // Top-level `use` import. Trait-use statements live inside class bodies, which this
            // outer loop always skips past wholesale (see `$i = $bodyEnd` below), so any T_USE
            // reaching here is necessarily a top-level import.
            [$name, $j] = readNameUntil($tokens, $i + 1, [';']);
            $alias = basename(str_replace('\\', '/', $name));
            // handle `use X\Y as Z;`
            $k = $j;
            while ($k < $count && !(is_string($tokens[$k]) && $tokens[$k] === ';')) {
                if (is_array($tokens[$k]) && $tokens[$k][0] === T_AS) {
                    $k++;
                    while ($k < $count && !(is_array($tokens[$k]) && $tokens[$k][0] === T_STRING)) $k++;
                    if ($k < $count) $alias = $tokens[$k][1];
                }
                $k++;
            }
            $uses[$alias] = $name;
            $i = $k;
        } elseif (is_array($tok) && $tok[0] === T_CLASS && !isClassConstFetch($tokens, $i)) {
            $nameIdx = $i + 1;
            while ($nameIdx < $count && !(is_array($tokens[$nameIdx]) && $tokens[$nameIdx][0] === T_STRING)) $nameIdx++;
            $className = is_array($tokens[$nameIdx] ?? null) ? $tokens[$nameIdx][1] : null;

            $extendsRaw = null;
            $j = $nameIdx + 1;
            while ($j < $count && !(is_string($tokens[$j]) && $tokens[$j] === '{')) {
                if (is_array($tokens[$j]) && $tokens[$j][0] === T_EXTENDS) {
                    [$extendsRaw, $j] = readNameUntil($tokens, $j + 1, ['{', 'implements']);
                    continue;
                }
                $j++;
            }
            $bodyStart = $j; // index of '{'
            $bodyEnd = findMatchingBrace($tokens, $bodyStart);

            if ($className !== null) {
                $fqcn = $namespace ? $namespace . '\\' . $className : $className;
                $classes[$fqcn] = [
                    'file' => $file,
                    'namespace' => $namespace,
                    'uses' => $uses,
                    'extendsRaw' => $extendsRaw,
                    'bodyStart' => $bodyStart,
                    'bodyEnd' => $bodyEnd,
                    'tokens' => $tokens, // shared reference; body slice re-read by index on demand
                    'lineOf' => $lineOf,
                ];
            }
            $i = $bodyEnd;
        }
        $i++;
    }

    return $classes;
}

function isClassConstFetch(array $tokens, int $i): bool
{
    // Detects `Foo::class` (T_CLASS following T_DOUBLE_COLON) vs an actual class declaration.
    for ($k = $i - 1; $k >= 0 && $k >= $i - 2; $k--) {
        if (is_array($tokens[$k]) && $tokens[$k][0] === T_DOUBLE_COLON) return true;
        if (is_array($tokens[$k]) && $tokens[$k][0] === T_WHITESPACE) continue;
        break;
    }
    return false;
}

/**
 * PHP 8.0+ tokenizes qualified names (`Foo\Bar\Baz`) as a single T_NAME_QUALIFIED token
 * (T_NAME_FULLY_QUALIFIED if leading-`\`, T_NAME_RELATIVE for `namespace\Foo`) rather than
 * the pre-8.0 sequence of T_STRING/T_NS_SEPARATOR tokens. Both forms must be accepted since
 * target apps are scanned with whatever PHP version this script runs under, independent of
 * the target app's own PHP version.
 */
function isNameToken($tok): bool
{
    return is_array($tok) && in_array($tok[0], [T_STRING, T_NAME_QUALIFIED, T_NAME_FULLY_QUALIFIED, T_NAME_RELATIVE], true);
}

function readNameUntil(array $tokens, int $start, array $stopStrings): array
{
    $name = '';
    $i = $start;
    $count = count($tokens);
    while ($i < $count) {
        $tok = $tokens[$i];
        if (is_string($tok) && in_array($tok, $stopStrings, true)) break;
        if (isNameToken($tok)) $name .= $tok[1];
        elseif (is_array($tok) && $tok[0] === T_NS_SEPARATOR) $name .= '\\';
        elseif (is_array($tok) && in_array($tok[0], [T_IMPLEMENTS, T_AS], true)) break;
        elseif (is_array($tok) && $tok[0] === T_WHITESPACE) { /* skip */ }
        elseif (is_string($tok) && ($tok === '{' || $tok === ';')) break;
        $i++;
    }
    return [$name, $i];
}

function findMatchingBrace(array $tokens, int $openIndex): int
{
    $depth = 0;
    $count = count($tokens);
    for ($i = $openIndex; $i < $count; $i++) {
        if (is_string($tokens[$i]) && $tokens[$i] === '{') $depth++;
        if (is_string($tokens[$i]) && $tokens[$i] === '}') {
            $depth--;
            if ($depth === 0) return $i;
        }
    }
    return $count - 1;
}

// ---------------------------------------------------------------------------
// Model resolution: walk the extends chain by inheritance, not by folder path
// ---------------------------------------------------------------------------

function resolveFqcn(?string $rawName, array $classInfo): ?string
{
    if ($rawName === null) return null;
    if (str_starts_with($rawName, '\\')) return ltrim($rawName, '\\');
    if (isset($classInfo['uses'][$rawName])) return $classInfo['uses'][$rawName];
    // No matching use-import: assume same namespace as the referencing class (common case
    // for local base classes like App\Models\BaseModel), else leave as short name for a
    // heuristic short-name match.
    return $classInfo['namespace'] ? $classInfo['namespace'] . '\\' . $rawName : $rawName;
}

function resolveIsModel(string $fqcn, array $classIndex, array $baseClasses, array $visited = []): array
{
    if (in_array($fqcn, $visited, true)) return ['isModel' => false, 'confidence' => 'cycle'];
    $visited[] = $fqcn;

    foreach ($baseClasses as $base) {
        if (rtrim($fqcn, '\\') === rtrim($base, '\\')) return ['isModel' => true, 'confidence' => 'high'];
    }

    if (!isset($classIndex[$fqcn])) {
        // Not declared in this codebase — could be a vendor/framework class (e.g. the real
        // Illuminate\Database\Eloquent\Model) or genuinely unresolved.
        $short = basename(str_replace('\\', '/', $fqcn));
        foreach ($baseClasses as $base) {
            if (basename(str_replace('\\', '/', $base)) === $short) {
                return ['isModel' => false, 'confidence' => 'unresolved-extends'];
            }
        }
        return ['isModel' => false, 'confidence' => 'external'];
    }

    $info = $classIndex[$fqcn];
    if ($info['extendsRaw'] === null) return ['isModel' => false, 'confidence' => 'no-parent'];

    $parentFqcn = resolveFqcn($info['extendsRaw'], $info);
    $result = resolveIsModel($parentFqcn, $classIndex, $baseClasses, $visited);

    // Downgrade confidence one notch per hop through an ambiguously-resolved parent so deep
    // chains through unresolved namespaces surface for manual confirmation rather than being
    // silently trusted.
    if ($result['isModel'] && !isset($classIndex[$parentFqcn]) && $result['confidence'] === 'high') {
        $result['confidence'] = 'medium';
    }
    return $result;
}

// ---------------------------------------------------------------------------
// Per-model extraction
// ---------------------------------------------------------------------------

/**
 * PHP property/trait declarations are inherited down the extends chain like any normal class
 * member: a subclass that doesn't redeclare `$casts` still runs with its parent's `$casts` at
 * runtime, and a trait `use`d only in an ancestor still applies to every descendant (that's how
 * `SoftDeletes` on an abstract `Entity` base makes every concrete subclass soft-deletable even
 * though the subclass file never mentions it). Reading only the leaf class, as a naive property
 * scan would, misses this and silently drops real behavior — the opposite of the parity this
 * tool exists to establish. Returns the ordered chain from `$fqcn` up through every ancestor
 * still declared in this codebase (stops at the first ancestor NOT in `$classIndex`, typically
 * the vendor base class).
 */
function resolveChain(string $fqcn, array $classIndex): array
{
    $chain = [];
    while (isset($classIndex[$fqcn]) && !in_array($fqcn, $chain, true)) {
        $chain[] = $fqcn;
        $info = $classIndex[$fqcn];
        if ($info['extendsRaw'] === null) break;
        $fqcn = resolveFqcn($info['extendsRaw'], $info);
    }
    return $chain;
}

/**
 * Reloads the full tokenized info (tokens, lineOf, bodyStart, bodyEnd) for one class, on demand,
 * by re-scanning its file. Cached per file path so classes sharing a file (or an ancestor chain
 * repeatedly hitting the same base-class file across many models) only pay the tokenize cost
 * once per run, not once per class.
 */
function loadFullClassInfo(string $fqcn, array $classIndex): array
{
    static $fileCache = [];
    $file = $classIndex[$fqcn]['file'];
    if (!isset($fileCache[$file])) {
        $fileCache[$file] = scanFile($file);
    }
    return $fileCache[$file][$fqcn];
}

function extractModelDetails(string $fqcn, array $classIndex, array $config, string $confidence): array
{
    $info = $classIndex[$fqcn];
    $self = loadFullClassInfo($fqcn, $classIndex);
    $tokens = $self['tokens'];
    $bodyStart = $self['bodyStart'];
    $bodyEnd = $self['bodyEnd'];
    $flags = [];
    if ($confidence !== 'high') {
        $flags[] = "base-class resolution confidence: $confidence — confirm this class is really a model";
    }

    $castMap = array_merge(DEFAULT_CAST_MAP, $config['castTypeOverrides']);
    $chain = resolveChain($fqcn, $classIndex);

    // Nearest-declared-wins per property (matches PHP property inheritance: a class that
    // doesn't redeclare a property just uses its parent's), but traits union across the whole
    // chain (using a trait anywhere in the chain applies its methods to every descendant).
    $arrayProps = ['table', 'primaryKey', 'fillable', 'guarded', 'casts', 'hidden', 'visible', 'appends', 'dates'];
    $extracted = array_fill_keys($arrayProps, ['value' => null, 'dynamic' => false, 'declared' => false]);
    $traits = [];
    foreach ($chain as $ancestorFqcn) {
        $a = loadFullClassInfo($ancestorFqcn, $classIndex);
        foreach ($arrayProps as $prop) {
            if ($extracted[$prop]['declared']) continue; // a nearer class already declared this
            $extracted[$prop] = extractProperty($a['tokens'], $a['bodyStart'], $a['bodyEnd'], $prop, $flags);
        }
        $traits = array_merge($traits, extractTraitUses($a['tokens'], $a['bodyStart'], $a['bodyEnd']));
    }
    $traits = array_values(array_unique($traits));
    $softDeletes = in_array('SoftDeletes', $traits, true);
    $parent = $chain[1] ?? null; // immediate ancestor still declared in this codebase, if any

    [$relationships, $accessorsAndMutators, $scopes] = extractMethods($tokens, $self['lineOf'], $bodyStart, $bodyEnd, $fqcn, $flags);

    $castsOut = [];
    if (is_array($extracted['casts']['value'] ?? null)) {
        foreach ($extracted['casts']['value'] as $field => $castType) {
            $baseCast = explode(':', (string) $castType)[0];
            $javaType = $castMap[$castType] ?? $castMap[$baseCast] ?? "FLAG:unknown cast '$castType' — no mapping, needs manual review";
            if (str_starts_with($javaType, 'FLAG:')) {
                $flags[] = "cast '$field' => '$castType': " . substr($javaType, 5);
            }
            $castsOut[] = ['field' => $field, 'phpCast' => $castType, 'javaType' => $javaType];
        }
    }

    return [
        'class' => $fqcn,
        'file' => $info['file'],
        'parent' => $parent,
        'confidence' => $confidence,
        'table' => scalarOrFlag($extracted['table'], $flags, 'table'),
        'primaryKey' => scalarOrFlag($extracted['primaryKey'], $flags, 'primaryKey') ?? 'id',
        'fillable' => listOrFlag($extracted['fillable'], $flags, 'fillable'),
        'guarded' => listOrFlag($extracted['guarded'], $flags, 'guarded'),
        'hidden' => listOrFlag($extracted['hidden'], $flags, 'hidden'),
        'visible' => listOrFlag($extracted['visible'], $flags, 'visible'),
        'appends' => listOrFlag($extracted['appends'], $flags, 'appends'),
        'casts' => $castsOut,
        'softDeletes' => $softDeletes,
        'traits' => $traits,
        'relationships' => $relationships,
        'accessorsAndMutators' => $accessorsAndMutators,
        'scopes' => $scopes,
        'flags' => $flags,
    ];
}

function scalarOrFlag(array $prop, array &$flags, string $name)
{
    if ($prop['dynamic']) { $flags[] = "$name is computed at runtime — not a literal, needs manual review"; return null; }
    return $prop['value'];
}

function listOrFlag(array $prop, array &$flags, string $name): array
{
    if ($prop['dynamic']) { $flags[] = "$name is computed at runtime — not a literal, needs manual review"; return []; }
    return is_array($prop['value']) ? array_values($prop['value']) : [];
}

/**
 * Finds `protected $name = <expr>;` at class-body top level (depth 0 relative to the class's
 * own opening brace) so method-local variables of the same name are never mistaken for it.
 */
function extractProperty(array $tokens, int $bodyStart, int $bodyEnd, string $propName, array &$flags): array
{
    $depth = 0;
    for ($i = $bodyStart; $i <= $bodyEnd; $i++) {
        $tok = $tokens[$i];
        if (is_string($tok) && $tok === '{') { $depth++; continue; }
        if (is_string($tok) && $tok === '}') { $depth--; continue; }
        if ($depth !== 1) continue; // 1 == directly inside this class body (0 is outside '{')
        if (is_array($tok) && $tok[0] === T_VARIABLE && $tok[1] === '$' . $propName) {
            // find '=' then read the value expression up to ';'
            $j = $i + 1;
            while ($j <= $bodyEnd && !(is_string($tokens[$j]) && $tokens[$j] === '=')) {
                if (is_string($tokens[$j]) && $tokens[$j] === ';') return ['value' => null, 'dynamic' => false, 'declared' => true]; // declared, no default
                $j++;
            }
            return readValueExpression($tokens, $j + 1, $bodyEnd) + ['declared' => true];
        }
    }
    return ['value' => null, 'dynamic' => false, 'declared' => false]; // not declared on this class — caller should check its ancestors
}

/**
 * Reads a literal scalar, or a literal array of scalars / key=>value pairs, up to the
 * terminating ';'. Anything containing a variable, function/method call, or constant fetch
 * other than a plain quoted string is marked dynamic rather than guessed at.
 */
function readValueExpression(array $tokens, int $start, int $bodyEnd): array
{
    $i = $start;
    while ($i <= $bodyEnd && is_array($tokens[$i]) && $tokens[$i][0] === T_WHITESPACE) $i++;

    $isArrayOpen = is_string($tokens[$i] ?? null) && $tokens[$i] === '['
        || (is_array($tokens[$i] ?? null) && $tokens[$i][0] === T_ARRAY);

    if (!$isArrayOpen) {
        // scalar: string, true/false/null, or something dynamic
        $tok = $tokens[$i] ?? null;
        if (is_array($tok) && $tok[0] === T_CONSTANT_ENCAPSED_STRING) {
            return ['value' => trim($tok[1], "'\""), 'dynamic' => false];
        }
        if (is_array($tok) && in_array($tok[0], [T_STRING], true) && in_array(strtolower($tok[1]), ['true', 'false', 'null'], true)) {
            return ['value' => strtolower($tok[1]), 'dynamic' => false];
        }
        return ['value' => null, 'dynamic' => true];
    }

    if (is_string($tokens[$i]) && $tokens[$i] === '[') {
        $openIdx = $i;
    } else {
        // T_ARRAY — find its '(' (legacy `array (` may have whitespace before the paren).
        $p = $i + 1;
        while ($p <= $bodyEnd && is_array($tokens[$p]) && $tokens[$p][0] === T_WHITESPACE) $p++;
        $openIdx = $p;
    }
    if (!is_string($tokens[$openIdx] ?? null)) return ['value' => null, 'dynamic' => true]; // malformed, bail safely
    $openChar = $tokens[$openIdx];
    $closeChar = $openChar === '[' ? ']' : ')';
    $depth = 0;
    $end = $openIdx;
    for ($k = $openIdx; $k <= $bodyEnd; $k++) {
        if (is_string($tokens[$k]) && $tokens[$k] === $openChar) $depth++;
        if (is_string($tokens[$k]) && $tokens[$k] === $closeChar) { $depth--; if ($depth === 0) { $end = $k; break; } }
    }

    // Walk items between openIdx+1 .. end-1, splitting on top-level commas.
    $items = [];
    $dynamic = false;
    $currentKey = null;
    $pendingValueTok = null;
    $localDepth = 0;
    for ($k = $openIdx + 1; $k < $end; $k++) {
        $tok = $tokens[$k];
        if (is_array($tok) && $tok[0] === T_WHITESPACE) continue;
        if (is_string($tok) && in_array($tok, ['[', '('], true)) { $localDepth++; $dynamic = true; continue; }
        if (is_string($tok) && in_array($tok, [']', ')'], true)) { $localDepth--; continue; }
        if ($localDepth > 0) continue;

        if (is_array($tok) && $tok[0] === T_DOUBLE_ARROW) { $currentKey = $pendingValueTok; $pendingValueTok = null; continue; }
        if (is_string($tok) && $tok === ',') {
            if ($pendingValueTok !== null) {
                if ($currentKey !== null) $items[$currentKey] = $pendingValueTok;
                else $items[] = $pendingValueTok;
            }
            $currentKey = null; $pendingValueTok = null;
            continue;
        }
        if (is_array($tok) && $tok[0] === T_CONSTANT_ENCAPSED_STRING) {
            $pendingValueTok = trim($tok[1], "'\"");
        } else {
            $dynamic = true; // variable, function call, constant fetch, etc.
        }
    }
    if ($pendingValueTok !== null) {
        if ($currentKey !== null) $items[$currentKey] = $pendingValueTok;
        else $items[] = $pendingValueTok;
    }

    return ['value' => $items, 'dynamic' => $dynamic];
}

function extractTraitUses(array $tokens, int $bodyStart, int $bodyEnd): array
{
    $traits = [];
    $depth = 0;
    for ($i = $bodyStart; $i <= $bodyEnd; $i++) {
        $tok = $tokens[$i];
        if (is_string($tok) && $tok === '{') { $depth++; continue; }
        if (is_string($tok) && $tok === '}') { $depth--; continue; }
        if ($depth !== 1) continue;
        if (is_array($tok) && $tok[0] === T_USE) {
            $j = $i + 1;
            while ($j <= $bodyEnd && !(is_string($tokens[$j]) && $tokens[$j] === ';')) {
                if (is_array($tokens[$j]) && $tokens[$j][0] === T_STRING) $traits[] = $tokens[$j][1];
                $j++;
            }
        }
    }
    return $traits;
}

function extractMethods(array $tokens, array $lineOf, int $bodyStart, int $bodyEnd, string $fqcn, array &$flags): array
{
    $relationships = [];
    $accessorsAndMutators = [];
    $scopes = [];
    $depth = 0;
    for ($i = $bodyStart; $i <= $bodyEnd; $i++) {
        $tok = $tokens[$i];
        if (is_string($tok) && $tok === '{') { $depth++; continue; }
        if (is_string($tok) && $tok === '}') { $depth--; continue; }
        if ($depth !== 1) continue;
        if (!(is_array($tok) && $tok[0] === T_FUNCTION)) continue;

        $j = $i + 1;
        while ($j <= $bodyEnd && !(is_array($tokens[$j]) && $tokens[$j][0] === T_STRING)) $j++;
        $methodName = $tokens[$j][1] ?? null;
        if ($methodName === null) continue;

        // find method body braces
        $k = $j;
        while ($k <= $bodyEnd && !(is_string($tokens[$k]) && $tokens[$k] === '{') && !(is_string($tokens[$k]) && $tokens[$k] === ';')) $k++;
        if ($k > $bodyEnd || !is_string($tokens[$k] ?? null) || $tokens[$k] !== '{') continue; // abstract/interface method, no body
        $methodBodyEnd = findMatchingBrace($tokens, $k);
        $lineStart = $lineOf[$j] ?? null;
        $lineEnd = $lineOf[$methodBodyEnd] ?? null;

        if (preg_match('/^get(.+)Attribute$/', $methodName, $m) || preg_match('/^set(.+)Attribute$/', $methodName, $m)) {
            $attribute = preg_replace('/(?<!^)[A-Z]/', '_$0', $m[1]);
            $attribute = strtolower($attribute);
            $accessorsAndMutators[] = [
                'method' => $methodName,
                'attribute' => $attribute,
                'kind' => str_starts_with($methodName, 'get') ? 'accessor' : 'mutator',
                'style' => 'legacy',
                'lineStart' => $lineStart, 'lineEnd' => $lineEnd,
            ];
            $flags[] = "$methodName() contains custom logic (lines $lineStart-$lineEnd) — read the method body and re-implement the equivalent transform in Java; do not assume it's a plain field mapping";
            continue;
        }

        if (preg_match('/^scope([A-Z].*)$/', $methodName, $m)) {
            $scopes[] = ['method' => $methodName, 'lineStart' => $lineStart, 'lineEnd' => $lineEnd];
            $flags[] = "scope$m[1]() is query-building logic (lines $lineStart-$lineEnd) — has no declarative JPA equivalent, port manually";
            continue;
        }

        // Attribute::class-style new accessor/mutator: detect a return type of `Attribute`.
        $rt = $k - 1;
        while ($rt > $j && is_array($tokens[$rt]) && $tokens[$rt][0] === T_WHITESPACE) $rt--;
        if (is_array($tokens[$rt] ?? null) && $tokens[$rt][0] === T_STRING && $tokens[$rt][1] === 'Attribute') {
            $accessorsAndMutators[] = [
                'method' => $methodName,
                'attribute' => preg_replace('/(?<!^)[A-Z]/', '_$0', $methodName),
                'kind' => 'attribute-cast',
                'style' => 'modern',
                'lineStart' => $lineStart, 'lineEnd' => $lineEnd,
            ];
            $flags[] = "$methodName() is a modern Attribute cast (lines $lineStart-$lineEnd) — read the get()/set() closures and re-implement in Java";
            continue;
        }

        // Relationship detection: scan the method body for a known relationship call.
        for ($b = $k; $b <= $methodBodyEnd; $b++) {
            if (is_array($tokens[$b]) && $tokens[$b][0] === T_STRING && in_array($tokens[$b][1], RELATIONSHIP_METHODS, true)) {
                // confirm it's a method call: preceded by '->'
                $p = $b - 1;
                while ($p > $k && is_array($tokens[$p]) && $tokens[$p][0] === T_WHITESPACE) $p--;
                if (!(is_array($tokens[$p]) && $tokens[$p][0] === T_OBJECT_OPERATOR)) continue;

                $relType = $tokens[$b][1];
                // first argument, if it's `Related::class`
                $related = null;
                $a = $b + 1;
                while ($a <= $methodBodyEnd && !(is_string($tokens[$a]) && $tokens[$a] === '(')) $a++;
                $a++;
                while ($a <= $methodBodyEnd && is_array($tokens[$a]) && $tokens[$a][0] === T_WHITESPACE) $a++;
                if (isNameToken($tokens[$a] ?? null)) $related = $tokens[$a][1];

                $relationships[] = [
                    'method' => $methodName,
                    'type' => $relType,
                    'related' => $related,
                    'lineStart' => $lineStart, 'lineEnd' => $lineEnd,
                ];
                if ($related === null) {
                    $flags[] = "$methodName(): could not resolve related class for $relType() (lines $lineStart-$lineEnd) — inspect manually";
                }
                break;
            }
        }
        $i = $methodBodyEnd;
    }
    return [$relationships, $accessorsAndMutators, $scopes];
}

// ---------------------------------------------------------------------------
// Doctrine ORM strategy: entities are identified by the #[ORM\Entity] attribute, not by
// inheritance — Doctrine entities are typically plain classes with no common base. Attributes
// always immediately precede the class/property they annotate, so a single forward pass that
// accumulates "pending" attribute groups and attaches them to whatever comes next is enough —
// no backward token-matching required.
// ---------------------------------------------------------------------------

// Keys are Doctrine's actual lowercase DBAL type identifiers (e.g. `type: 'integer'`), which is
// what appears in both `#[ORM\Column(type: 'integer')]` and `#[ORM\Column(type: Types::INTEGER)]`
// (the `Types::INTEGER` constant's *value* is the string `'integer'` — Doctrine's Types class is
// just a set of named constants for these same lowercase strings, not a separate type system).
// Lookups are case-insensitively normalized in mapDoctrineType() so a bare `Types::INTEGER`
// token capture (which yields the constant's name, "INTEGER") still resolves correctly.
const DOCTRINE_TYPE_MAP = [
    'integer' => 'Long', 'smallint' => 'Integer', 'bigint' => 'Long',
    'string' => 'String', 'text' => 'FLAG:String — mark the JPA field @Lob',
    'boolean' => 'Boolean',
    'float' => 'Double', 'decimal' => 'BigDecimal',
    'date_mutable' => 'LocalDate', 'date_immutable' => 'LocalDate', 'date' => 'LocalDate',
    'datetime_mutable' => 'LocalDateTime', 'datetime_immutable' => 'LocalDateTime', 'datetime' => 'LocalDateTime',
    'datetimetz_mutable' => 'OffsetDateTime', 'datetimetz_immutable' => 'OffsetDateTime', 'datetimetz' => 'OffsetDateTime',
    'time_mutable' => 'LocalTime', 'time_immutable' => 'LocalTime', 'time' => 'LocalTime',
    'json' => 'FLAG:Map/List — shape must be inferred from usage',
    'blob' => 'byte[]',
    'guid' => 'UUID',
];

// Bare PHP property type hints (used when a Column attribute has no explicit `type:` arg —
// Doctrine infers the column type from the property's PHP type in that case).
const PHP_TYPE_MAP = [
    'int' => 'Long', 'string' => 'String', 'bool' => 'Boolean', 'float' => 'Double',
    'array' => 'FLAG:List/Map — shape must be inferred from usage',
    'DateTimeImmutable' => 'LocalDateTime', 'DateTime' => 'LocalDateTime',
    'DateTimeInterface' => 'LocalDateTime',
];

const DOCTRINE_RELATIONSHIP_ATTRS = ['OneToOne', 'OneToMany', 'ManyToOne', 'ManyToMany'];

function scanDoctrineFile(string $file): array
{
    $source = file_get_contents($file);
    $tokens = token_get_all($source);
    $count = count($tokens);
    $lineOf = [];
    $line = 1;
    foreach ($tokens as $idx => $t) {
        $lineOf[$idx] = $line;
        $line += substr_count(is_array($t) ? $t[1] : $t, "\n");
    }

    $namespace = '';
    $entities = [];
    $pendingAttrs = [];

    $i = 0;
    while ($i < $count) {
        $tok = $tokens[$i];

        if (is_array($tok) && $tok[0] === T_ATTRIBUTE) {
            [$attrs, $closeIdx] = parseAttributeGroup($tokens, $i);
            $pendingAttrs = array_merge($pendingAttrs, $attrs);
            $i = $closeIdx + 1;
            continue;
        }
        if (is_array($tok) && $tok[0] === T_NAMESPACE) {
            [$namespace, $i] = readNameUntil($tokens, $i + 1, [';', '{']);
            $pendingAttrs = [];
        } elseif (is_array($tok) && $tok[0] === T_CLASS && !isClassConstFetch($tokens, $i)) {
            $nameIdx = $i + 1;
            while ($nameIdx < $count && !(is_array($tokens[$nameIdx]) && $tokens[$nameIdx][0] === T_STRING)) $nameIdx++;
            $className = is_array($tokens[$nameIdx] ?? null) ? $tokens[$nameIdx][1] : null;

            $j = $nameIdx + 1;
            while ($j < $count && !(is_string($tokens[$j]) && $tokens[$j] === '{')) $j++;
            $bodyStart = $j;
            $bodyEnd = findMatchingBrace($tokens, $bodyStart);

            $isEntity = attrsContainName($pendingAttrs, 'Entity');
            if ($isEntity && $className !== null) {
                $fqcn = $namespace ? $namespace . '\\' . $className : $className;
                $entities[$fqcn] = [
                    'file' => $file,
                    'classAttrs' => $pendingAttrs,
                    'properties' => scanDoctrineProperties($tokens, $lineOf, $bodyStart, $bodyEnd),
                ];
            }
            $pendingAttrs = [];
            $i = $bodyEnd;
        } elseif (!(is_array($tok) && in_array($tok[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true))) {
            // Any non-trivial token that isn't part of an attribute/namespace/class clears
            // pending attributes — attributes must immediately precede their target.
            $pendingAttrs = [];
        }
        $i++;
    }

    return $entities;
}

function attrsContainName(array $attrs, string $shortName): bool
{
    foreach ($attrs as $attr) {
        if (basename(str_replace('\\', '/', $attr['name'])) === $shortName) return true;
    }
    return false;
}

function findAttr(array $attrs, string $shortName): ?array
{
    foreach ($attrs as $attr) {
        if (basename(str_replace('\\', '/', $attr['name'])) === $shortName) return $attr;
    }
    return null;
}

/**
 * Parses one `#[...]` group (which may contain multiple comma-separated attributes) starting
 * at the T_ATTRIBUTE token. Returns [attrs, closingBracketIndex].
 */
function parseAttributeGroup(array $tokens, int $startIdx): array
{
    $count = count($tokens);
    $depth = 1;
    $end = $startIdx;
    for ($k = $startIdx + 1; $k < $count; $k++) {
        if (is_string($tokens[$k]) && $tokens[$k] === '[') $depth++;
        if (is_string($tokens[$k]) && $tokens[$k] === ']') { $depth--; if ($depth === 0) { $end = $k; break; } }
    }

    $attrs = [];
    $name = '';
    $args = null;
    $j = $startIdx + 1;
    while ($j < $end) {
        $tok = $tokens[$j];
        if (is_array($tok) && in_array($tok[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) { $j++; continue; }
        if ($args === null && isNameToken($tok)) { $name .= $tok[1]; $j++; continue; }
        if ($args === null && is_array($tok) && $tok[0] === T_NS_SEPARATOR) { $name .= '\\'; $j++; continue; }
        if ($args === null && is_string($tok) && $tok === '(') {
            [$args, $j] = parseAttributeArgs($tokens, $j, $end);
            continue;
        }
        if (is_string($tok) && $tok === ',') {
            $attrs[] = ['name' => $name, 'args' => $args ?? []];
            $name = ''; $args = null; $j++; continue;
        }
        $j++;
    }
    if ($name !== '') $attrs[] = ['name' => $name, 'args' => $args ?? []];

    return [$attrs, $end];
}

/** Parses `(key: value, key2: value2, positional)` starting at the '(' index. Returns [args, indexAfterClosingParen]. */
function parseAttributeArgs(array $tokens, int $openParenIdx, int $limit): array
{
    $depth = 1;
    $close = $openParenIdx;
    for ($k = $openParenIdx + 1; $k < $limit; $k++) {
        if (is_string($tokens[$k]) && in_array($tokens[$k], ['(', '['], true)) $depth++;
        if (is_string($tokens[$k]) && in_array($tokens[$k], [')', ']'], true)) { $depth--; if ($depth === 0) { $close = $k; break; } }
    }

    $args = [];
    $posIndex = 0;
    $segStart = $openParenIdx + 1;
    $localDepth = 0;
    for ($k = $openParenIdx + 1; $k <= $close; $k++) {
        $atComma = $k < $close && is_string($tokens[$k]) && $tokens[$k] === ',' && $localDepth === 0;
        $atEnd = $k === $close;
        if ($k < $close && is_string($tokens[$k]) && in_array($tokens[$k], ['(', '['], true)) $localDepth++;
        if ($k < $close && is_string($tokens[$k]) && in_array($tokens[$k], [')', ']'], true)) $localDepth--;
        if ($atComma || $atEnd) {
            if ($k > $segStart || $atEnd) {
                [$key, $val] = parseAttrArgSegment($tokens, $segStart, $k);
                if ($key !== null) $args[$key] = $val; else $args[$posIndex++] = $val;
            }
            $segStart = $k + 1;
        }
    }
    return [$args, $close + 1];
}

function parseAttrArgSegment(array $tokens, int $start, int $end)
{
    $i = $start;
    while ($i < $end && is_array($tokens[$i]) && $tokens[$i][0] === T_WHITESPACE) $i++;
    $key = null;
    if ($i < $end && is_array($tokens[$i]) && $tokens[$i][0] === T_STRING) {
        $p = $i + 1;
        while ($p < $end && is_array($tokens[$p]) && $tokens[$p][0] === T_WHITESPACE) $p++;
        if ($p < $end && is_string($tokens[$p]) && $tokens[$p] === ':') {
            $key = $tokens[$i][1];
            $i = $p + 1;
        }
    }
    return [$key, parseAttrValue($tokens, $i, $end)];
}

function findTopLevelDoubleArrow(array $tokens, int $start, int $end): ?int
{
    $depth = 0;
    for ($k = $start; $k < $end; $k++) {
        if (is_string($tokens[$k]) && in_array($tokens[$k], ['(', '['], true)) { $depth++; continue; }
        if (is_string($tokens[$k]) && in_array($tokens[$k], [')', ']'], true)) { $depth--; continue; }
        if ($depth === 0 && is_array($tokens[$k]) && $tokens[$k][0] === T_DOUBLE_ARROW) return $k;
    }
    return null;
}

function parseAttrValue(array $tokens, int $start, int $end)
{
    $i = $start;
    while ($i < $end && is_array($tokens[$i]) && $tokens[$i][0] === T_WHITESPACE) $i++;
    if ($i >= $end) return null;
    $tok = $tokens[$i];

    if (is_array($tok) && $tok[0] === T_CONSTANT_ENCAPSED_STRING) return trim($tok[1], "'\"");
    if (is_array($tok) && $tok[0] === T_LNUMBER) return (int) $tok[1];
    if (is_array($tok) && $tok[0] === T_DNUMBER) return (float) $tok[1];
    if (is_array($tok) && $tok[0] === T_STRING && in_array(strtolower($tok[1]), ['true', 'false', 'null'], true)) {
        return strtolower($tok[1]) === 'null' ? null : (strtolower($tok[1]) === 'true');
    }
    if (isNameToken($tok)) {
        // `Foo::class` or `Types::STRING` — capture the trailing segment, which is what
        // callers need (the related class short name, or the type constant name).
        $p = $i + 1;
        while ($p < $end && is_array($tokens[$p]) && $tokens[$p][0] === T_WHITESPACE) $p++;
        if ($p < $end && is_array($tokens[$p]) && $tokens[$p][0] === T_DOUBLE_COLON) {
            $q = $p + 1;
            while ($q < $end && is_array($tokens[$q]) && $tokens[$q][0] === T_WHITESPACE) $q++;
            if ($q < $end && (is_array($tokens[$q]) && ($tokens[$q][0] === T_CLASS || $tokens[$q][0] === T_STRING))) {
                return $tokens[$q][0] === T_CLASS ? $tok[1] : $tokens[$q][1];
            }
        }
        return $tok[1];
    }
    if (is_string($tok) && $tok === '[') {
        $depth = 1;
        $close = $i;
        for ($k = $i + 1; $k < $end; $k++) {
            if (is_string($tokens[$k]) && $tokens[$k] === '[') $depth++;
            if (is_string($tokens[$k]) && $tokens[$k] === ']') { $depth--; if ($depth === 0) { $close = $k; break; } }
        }
        $items = [];
        $segStart = $i + 1;
        $localDepth = 0;
        for ($k = $i + 1; $k <= $close; $k++) {
            $atComma = $k < $close && is_string($tokens[$k]) && $tokens[$k] === ',' && $localDepth === 0;
            $atEnd = $k === $close;
            if ($k < $close && is_string($tokens[$k]) && in_array($tokens[$k], ['(', '['], true)) $localDepth++;
            if ($k < $close && is_string($tokens[$k]) && in_array($tokens[$k], [')', ']'], true)) $localDepth--;
            if (($atComma || $atEnd) && $k > $segStart) {
                // Array-literal items use `'key' => value` (PHP array syntax), distinct from the
                // `key: value` named-argument syntax attribute calls use — e.g. OrderBy's array
                // argument `['publishedAt' => 'DESC']` needs the sort direction, not just the field.
                $arrowIdx = findTopLevelDoubleArrow($tokens, $segStart, $k);
                if ($arrowIdx !== null) {
                    $items[parseAttrValue($tokens, $segStart, $arrowIdx)] = parseAttrValue($tokens, $arrowIdx + 1, $k);
                } else {
                    $items[] = parseAttrValue($tokens, $segStart, $k);
                }
                $segStart = $k + 1;
            }
        }
        return $items;
    }
    return null; // dynamic/unsupported expression — caller treats missing value as "needs review"
}

/**
 * Scans a class body for typed property declarations and the attribute groups that
 * immediately precede each one. Skips over method bodies (and their parameter lists) wholesale
 * so a method's local variables or parameters are never mistaken for properties.
 */
function scanDoctrineProperties(array $tokens, array $lineOf, int $bodyStart, int $bodyEnd): array
{
    $properties = [];
    $pendingAttrs = [];
    $typeTokens = [];
    $depth = 0;
    $modifierTypes = [T_PUBLIC, T_PROTECTED, T_PRIVATE, T_STATIC, T_READONLY, T_VAR];

    for ($i = $bodyStart; $i <= $bodyEnd; $i++) {
        $tok = $tokens[$i];
        if (is_string($tok) && $tok === '{') { $depth++; continue; }
        if (is_string($tok) && $tok === '}') { $depth--; continue; }
        if ($depth !== 1) continue;

        if (is_array($tok) && $tok[0] === T_ATTRIBUTE) {
            [$attrs, $closeIdx] = parseAttributeGroup($tokens, $i);
            $pendingAttrs = array_merge($pendingAttrs, $attrs);
            $i = $closeIdx;
            continue;
        }
        if (is_array($tok) && $tok[0] === T_FUNCTION) {
            $j = $i + 1;
            while ($j <= $bodyEnd && !(is_string($tokens[$j]) && ($tokens[$j] === '{' || $tokens[$j] === ';'))) $j++;
            $i = (is_string($tokens[$j] ?? null) && $tokens[$j] === '{') ? findMatchingBrace($tokens, $j) : $j;
            $pendingAttrs = [];
            $typeTokens = [];
            continue;
        }
        if (is_array($tok) && $tok[0] === T_VARIABLE) {
            $propName = ltrim($tok[1], '$');
            $typeText = '';
            foreach ($typeTokens as $tt) $typeText .= is_array($tt) ? $tt[1] : $tt;
            $properties[] = [
                'name' => $propName,
                'phpType' => trim($typeText),
                'attrs' => $pendingAttrs,
                'line' => $lineOf[$i] ?? null,
            ];
            $pendingAttrs = [];
            $typeTokens = [];
            // skip to the statement's terminating ';' (default value expr may itself contain
            // parens/brackets, e.g. `= new ArrayCollection()`), tracking their depth.
            $j = $i + 1;
            $localDepth = 0;
            while ($j <= $bodyEnd) {
                if (is_string($tokens[$j]) && in_array($tokens[$j], ['(', '['], true)) $localDepth++;
                if (is_string($tokens[$j]) && in_array($tokens[$j], [')', ']'], true)) $localDepth--;
                if ($localDepth === 0 && is_string($tokens[$j]) && $tokens[$j] === ';') break;
                $j++;
            }
            $i = $j;
            continue;
        }
        if (is_array($tok) && in_array($tok[0], $modifierTypes, true)) continue; // visibility/static/readonly — not part of the type
        if (is_array($tok) && in_array($tok[0], [T_WHITESPACE, T_COMMENT, T_DOC_COMMENT], true)) continue;
        if (is_string($tok) && in_array($tok, ['{', '}'], true)) continue; // handled above
        // '?' nullable marker, T_NS_SEPARATOR, name tokens, '|' union-type separator, T_CONST, etc.
        $typeTokens[] = $tok;
    }

    return $properties;
}

function mapDoctrineType(?string $doctrineType, string $phpType, array $castOverrides): string
{
    $bare = ltrim($phpType, '?');
    $bare = ltrim($bare, '\\');
    if ($doctrineType !== null) {
        // Normalize case: `type: 'integer'` (lowercase literal) and `type: Types::INTEGER`
        // (constant name captured as "INTEGER") must resolve to the same map entry.
        $doctrineTypeKey = strtolower($doctrineType);
        return $castOverrides[$doctrineType] ?? $castOverrides[$doctrineTypeKey]
            ?? DOCTRINE_TYPE_MAP[$doctrineTypeKey]
            ?? "FLAG:unknown Doctrine type '$doctrineType' — no mapping, needs manual review";
    }
    return $castOverrides[$bare] ?? PHP_TYPE_MAP[$bare] ?? "FLAG:unmapped PHP type '$phpType' — no mapping, needs manual review";
}

function extractDoctrineEntityDetails(string $fqcn, array $entityInfo, array $config): array
{
    $flags = [];
    $castOverrides = $config['castTypeOverrides'] ?? [];
    $tableAttr = findAttr($entityInfo['classAttrs'], 'Table');
    $table = $tableAttr['args']['name'] ?? null;

    $id = null;
    $columns = [];
    $relationships = [];

    foreach ($entityInfo['properties'] as $prop) {
        $idAttr = findAttr($prop['attrs'], 'Id');
        $genAttr = findAttr($prop['attrs'], 'GeneratedValue');
        $colAttr = findAttr($prop['attrs'], 'Column');
        $joinColAttr = findAttr($prop['attrs'], 'JoinColumn');
        $joinTableAttr = findAttr($prop['attrs'], 'JoinTable');
        $orderByAttr = findAttr($prop['attrs'], 'OrderBy');

        $relAttr = null;
        $relType = null;
        foreach (DOCTRINE_RELATIONSHIP_ATTRS as $t) {
            if ($a = findAttr($prop['attrs'], $t)) { $relAttr = $a; $relType = $t; break; }
        }

        if ($idAttr !== null) {
            $id = [
                'field' => $prop['name'],
                'phpType' => $prop['phpType'],
                'generationStrategy' => $genAttr !== null ? ($genAttr['args']['strategy'] ?? 'AUTO') : 'NONE',
            ];
        }

        if ($relAttr !== null) {
            $relationships[] = [
                'field' => $prop['name'],
                'type' => $relType,
                'target' => $relAttr['args']['targetEntity'] ?? null,
                'mappedBy' => $relAttr['args']['mappedBy'] ?? null,
                'inversedBy' => $relAttr['args']['inversedBy'] ?? null,
                'joinColumn' => $joinColAttr !== null ? ['name' => $joinColAttr['args']['name'] ?? null, 'nullable' => $joinColAttr['args']['nullable'] ?? true] : null,
                'joinTable' => $joinTableAttr !== null ? ['name' => $joinTableAttr['args']['name'] ?? null] : null,
                'orderBy' => $orderByAttr['args'][0] ?? null,
                'cascade' => $relAttr['args']['cascade'] ?? [],
                'orphanRemoval' => $relAttr['args']['orphanRemoval'] ?? false,
                'line' => $prop['line'],
            ];
            if (($relAttr['args']['targetEntity'] ?? null) === null) {
                $flags[] = "{$prop['name']}: could not resolve targetEntity for $relType (line {$prop['line']}) — inspect manually";
            }
            continue;
        }

        if ($colAttr !== null || $idAttr !== null) {
            $doctrineType = $colAttr['args']['type'] ?? null;
            $javaType = mapDoctrineType($doctrineType, $prop['phpType'], $castOverrides);
            if (str_starts_with($javaType, 'FLAG:')) {
                $flags[] = "{$prop['name']}: " . substr($javaType, 5) . " (line {$prop['line']})";
            }
            $columns[] = [
                'field' => $prop['name'],
                'columnName' => $colAttr['args']['name'] ?? null,
                'phpType' => $prop['phpType'],
                'doctrineType' => $doctrineType,
                'javaType' => $javaType,
                'nullable' => $colAttr['args']['nullable'] ?? false, // Doctrine's own Column default is NOT NULL regardless of the PHP property's `?` type hint
                'unique' => $colAttr['args']['unique'] ?? false,
            ];
        }
    }

    if ($id === null) {
        $flags[] = 'no #[ORM\\Id] property found — confirm this entity really has no primary key (or that it inherits one), needs manual review';
    }

    return [
        'class' => $fqcn,
        'framework' => 'doctrine',
        'file' => $entityInfo['file'],
        'table' => $table,
        'id' => $id,
        'columns' => $columns,
        'relationships' => $relationships,
        'flags' => $flags,
    ];
}

// ---------------------------------------------------------------------------
// Doctrine XML-mapping strategy: some apps (Sylius among them) deliberately keep domain model
// classes framework-agnostic — no Doctrine attributes on the class at all — and map them
// externally via `*.orm.xml` files instead. A codebase using this style is invisible to the
// attribute-based strategy above; this is a completely independent discovery path (by config
// file, not by anything in the PHP source) that runs unconditionally alongside it.
// ---------------------------------------------------------------------------

function collectXmlMappingFiles(string $root, array $excludePaths): array
{
    $files = [];
    $iterator = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS)
    );
    foreach ($iterator as $fileInfo) {
        if (!str_ends_with($fileInfo->getFilename(), '.orm.xml')) continue;
        $relative = substr($fileInfo->getPathname(), strlen($root) + 1);
        $excluded = false;
        foreach ($excludePaths as $ex) {
            if (str_starts_with($relative, $ex . '/') || $relative === $ex) { $excluded = true; break; }
        }
        if (!$excluded) $files[] = $fileInfo->getPathname();
    }
    return $files;
}

/**
 * Returns direct children matching a tag name. Doctrine's mapping XSD declares a default
 * namespace (`xmlns="..."` with no prefix) — SimpleXMLElement::xpath() does NOT apply a default
 * namespace to unprefixed tag-name queries (a well-known gotcha), silently returning nothing.
 * Property/children() access handles the default namespace transparently, so filtering that way
 * instead of via xpath() is what actually works here.
 */
function xmlChildren(SimpleXMLElement $node, string $tag): array
{
    $result = [];
    foreach ($node->children() as $child) {
        if ($child->getName() === $tag) $result[] = $child;
    }
    return $result;
}

function xmlAttr(SimpleXMLElement $node, string $attr, $default = null)
{
    return isset($node[$attr]) ? (string) $node[$attr] : $default;
}

function xmlBoolAttr(SimpleXMLElement $node, string $attr, bool $default): bool
{
    return isset($node[$attr]) ? filter_var((string) $node[$attr], FILTER_VALIDATE_BOOLEAN) : $default;
}

const XML_RELATIONSHIP_TAGS = ['one-to-one', 'one-to-many', 'many-to-one', 'many-to-many'];

function scanXmlMappingFile(string $file): array
{
    $xml = @simplexml_load_file($file);
    if ($xml === false) return [];

    $entities = [];
    foreach (array_merge(xmlChildren($xml, 'entity'), xmlChildren($xml, 'mapped-superclass')) as $node) {
        $fqcn = xmlAttr($node, 'name');
        if ($fqcn === null) continue;
        $flags = [];

        $id = null;
        $idNodes = xmlChildren($node, 'id');
        if ($idNodes) {
            $idNode = $idNodes[0];
            $genNodes = xmlChildren($idNode, 'generator');
            $id = [
                'field' => xmlAttr($idNode, 'name'),
                'column' => xmlAttr($idNode, 'column', xmlAttr($idNode, 'name')),
                'type' => xmlAttr($idNode, 'type'),
                'generationStrategy' => $genNodes ? xmlAttr($genNodes[0], 'strategy', 'AUTO') : 'NONE',
            ];
        }

        $fields = [];
        foreach (xmlChildren($node, 'field') as $f) {
            $fields[] = [
                'field' => xmlAttr($f, 'name'),
                'column' => xmlAttr($f, 'column', xmlAttr($f, 'name')),
                'type' => xmlAttr($f, 'type'),
                'length' => xmlAttr($f, 'length') !== null ? (int) xmlAttr($f, 'length') : null,
                'nullable' => xmlBoolAttr($f, 'nullable', false),
                'unique' => xmlBoolAttr($f, 'unique', false),
            ];
        }

        $relationships = [];
        foreach (XML_RELATIONSHIP_TAGS as $relTag) {
            foreach (xmlChildren($node, $relTag) as $r) {
                $target = xmlAttr($r, 'target-entity');
                if ($target !== null && str_ends_with($target, 'Interface')) {
                    $flags[] = xmlAttr($r, 'field') . ": target-entity is an interface ($target) — "
                        . "Doctrine resolves this to a concrete class via ResolveTargetEntityListener "
                        . "at runtime; confirm the concrete implementation before choosing the Java "
                        . "relationship's target type";
                }

                $joinColumn = null;
                $jcNodes = xmlChildren($r, 'join-column');
                if ($jcNodes) {
                    $joinColumn = ['name' => xmlAttr($jcNodes[0], 'name'), 'nullable' => xmlBoolAttr($jcNodes[0], 'nullable', true)];
                }

                $joinTable = null;
                $jtNodes = xmlChildren($r, 'join-table');
                if ($jtNodes) {
                    $joinTable = ['name' => xmlAttr($jtNodes[0], 'name')];
                }

                $orderBy = [];
                $obParentNodes = xmlChildren($r, 'order-by');
                if ($obParentNodes) {
                    foreach (xmlChildren($obParentNodes[0], 'order-by-field') as $ob) {
                        $orderBy[xmlAttr($ob, 'name')] = xmlAttr($ob, 'direction', 'ASC');
                    }
                }

                $relationships[] = [
                    'field' => xmlAttr($r, 'field'),
                    'type' => $relTag,
                    'target' => $target,
                    'mappedBy' => xmlAttr($r, 'mapped-by'),
                    'inversedBy' => xmlAttr($r, 'inversed-by'),
                    'joinColumn' => $joinColumn,
                    'joinTable' => $joinTable,
                    'orderBy' => $orderBy ?: null,
                    'orphanRemoval' => xmlBoolAttr($r, 'orphan-removal', false),
                ];
            }
        }

        if ($id === null && $node->getName() === 'entity') {
            $flags[] = 'no <id> declared directly on this <entity> — it likely inherits one from a <mapped-superclass>; confirm the actual primary key before generating a Java entity';
        }

        $entities[$fqcn] = [
            'class' => $fqcn,
            'framework' => 'doctrine-xml',
            'file' => $file,
            'kind' => $node->getName(), // entity | mapped-superclass — maps to @Entity vs @MappedSuperclass
            'table' => xmlAttr($node, 'table'),
            'id' => $id,
            'fields' => $fields,
            'relationships' => $relationships,
            'flags' => $flags,
        ];
    }

    return $entities;
}

exit(main($argv));
