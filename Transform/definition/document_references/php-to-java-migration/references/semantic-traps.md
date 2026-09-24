# Semantic Traps: PHP → Java

Full catalog of PHP behaviors that silently differ in Java. Each entry seeds a `RULEBOOK.md` rule. The rule for every trap: **translate the exact observed predicate, not what the code "looks like" it means**, and cover it with a test that pins the behavior.

## Loose comparison and truthiness

### `==` type juggling

```php
"0" == false;   // true
"" == null;     // true
0 == "abc";     // false in PHP 8+, true in PHP 7
"1e2" == "100"; // true (numeric string comparison)
null == false;  // true
```

Java has no loose `==` for objects. Use `.equals()` and replicate coercion **only where a captured test demands it**.

```java
// Replicate PHP 8 "0" == false only if behavior requires it:
static boolean looseEquals(String a, boolean b) {
    return parsesFalsey(a) == b;   // define parsesFalsey to match observed cases
}
```

Note the PHP 7 → 8 change in `0 == "abc"`. Check the source PHP version and pin the matching behavior.

### `empty()` and `isset()`

```php
empty("0");   // true  — "0" is falsey
empty("");    // true
empty(0);     // true
empty(null);  // true
empty([]);    // true
isset($a['k']); // false if key absent OR value is null
```

Do not collapse `empty($x)` to `x == null`. Translate the full falsey predicate:

```java
static boolean phpEmpty(Object v) {
    if (v == null) return true;
    if (v instanceof String s) return s.isEmpty() || s.equals("0");
    if (v instanceof Number n) return n.doubleValue() == 0.0;
    if (v instanceof Boolean b) return !b;
    if (v instanceof Collection<?> c) return c.isEmpty();
    if (v instanceof Map<?,?> m) return m.isEmpty();
    return false;
}
```

## Numeric behavior

### Division returns float

```php
7 / 2;        // 3.5  (always float for non-even division)
intdiv(7, 2); // 3
7 % 2;        // 1
```

`7 / 2` in Java integer context is `3`. Match PHP by using double division, or `Math.floorDiv`/`intdiv` semantics explicitly where the original used `intdiv`.

### Integer overflow becomes float

```php
PHP_INT_MAX + 1; // silently becomes a float (9.2233720368548E+18)
```

Java `long` overflow wraps silently — different again. At sites flagged in the gap inventory use `Math.addExact` (throw) or `BigInteger` (match PHP's promotion to arbitrary magnitude, then decide behavior). Pin with a test.

### String ↔ number juggling

```php
"10" + 5;      // 15   (arithmetic)
"10abc" + 5;   // 15 in PHP 7 (warning), TypeError in PHP 8
"abc" . 5;     // "abc5" (concatenation)
```

Java has no implicit numeric coercion of strings. Parse explicitly and test the malformed-input path — behavior differs between PHP 7 and 8, so pin to the source version.

## Strings

### Negative substring offsets

```php
substr("hello", -3);     // "llo"
substr("hello", -3, 2);  // "ll"
substr("hi", -5);        // "hi" (offset clamped)
```

```java
static String phpSubstr(String s, int start) {
    int from = start < 0 ? Math.max(0, s.length() + start) : Math.min(start, s.length());
    return s.substring(from);
}
```

### Multibyte functions

`strlen` counts bytes; `mb_strlen` counts characters. Java `String.length()` counts UTF-16 code units. For text with non-BMP characters use code points:

```java
s.codePointCount(0, s.length());  // closest to mb_strlen for most cases
```

Verify UTF-8 handling anywhere `mb_*` appeared in the source.

## Arrays

### Insertion order preserved

PHP arrays are ordered maps. Use `LinkedHashMap`, never `HashMap`, when order is observable.

### `foreach` by reference

```php
foreach ($items as &$item) { $item *= 2; }  // mutates the source array
unset($item); // dangling reference bug if forgotten
```

Rewrite with an indexed loop or list replacement; verify the mutation with a test.

```java
for (int i = 0; i < items.size(); i++) items.set(i, items.get(i) * 2);
```

### `usort` stability

Sort stability is PHP-version-dependent (stable from PHP 8.0). `List.sort`/`Collections.sort` are stable. Assert the expected ordering of equal elements in tests, especially when migrating from PHP < 8.0.

## Dates

### `strtotime` leniency

```php
strtotime("next thursday");
strtotime("2024-13-45");   // may parse to something unexpected
strtotime("+1 week");
```

`strtotime` accepts near-anything. Replace with a strict `DateTimeFormatter`. Enumerate the formats actually seen in production logs and support exactly those; reject the rest and test the rejection.

## Regex (PCRE vs java.util.regex)

Differences that bite:
- Delimiters: PHP patterns are wrapped (`/.../`, `#...#`); Java patterns are not.
- Inline modifiers and possessive quantifiers mostly port, but named groups differ: PHP `(?P<name>...)` → Java `(?<name>...)`.
- `\d`, `\w` are Unicode-aware differently; PHP `u` modifier → Java `Pattern.UNICODE_CHARACTER_CLASS`.
- `preg_match` returns 0/1/false; Java returns boolean. Map return handling.

Run every pattern through both engines against the same inputs in a test before trusting it.

## Null and error handling

- PHP's `@` error-suppression operator has no Java equivalent — the underlying operation must be made explicit (try/catch or a guarded check).
- Undefined array key / property access warns in PHP but yields `null`; in Java it throws or does not compile. This is a gap-inventory item, not a trap to translate silently.
- `??` null coalescing → `Optional.orElse` or an explicit null check. `?->` nullsafe → `Optional` chaining.
