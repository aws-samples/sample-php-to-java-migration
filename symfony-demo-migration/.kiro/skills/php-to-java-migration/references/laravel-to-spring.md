# Laravel → Spring Boot

Idiom map for Laravel-specific constructs. These seed the `RULEBOOK.md` for a Laravel source. Preserve behavior first; adopt idiomatic Spring only after parity.

## Eloquent ORM → Spring Data JPA

| Laravel / Eloquent | Spring / JPA |
|---|---|
| `Model` subclass | `@Entity` class |
| `$table`, `$primaryKey` | `@Table`, `@Id` |
| `$fillable` / `$guarded` | explicit setters / DTO mapping (no mass-assignment concept) |
| `$casts` | `@Convert`, `@Enumerated`, or field type |
| `hasMany` / `belongsTo` | `@OneToMany` / `@ManyToOne` |
| `belongsToMany` | `@ManyToMany` + `@JoinTable` |
| `$model->save()` | `repository.save(entity)` |
| `Model::find($id)` | `repository.findById(id)` |
| `Model::where(...)->get()` | derived query method or `@Query` |
| Query scopes | `Specification` or repository method |
| `with('relation')` (eager) | `@EntityGraph` or `JOIN FETCH` |
| Soft deletes (`SoftDeletes`) | `@SQLDelete` + `@Where("deleted_at is null")` |
| Accessors/mutators | `@PostLoad` / setter logic, or DTO transform |
| Timestamps (`created_at`) | `@CreatedDate` / `@LastModifiedDate` (Auditing) |

Watch: Eloquent lazy-loads relations by default (N+1 risk carried into behavior). Replicate the observed query behavior first, then optimize with fetch graphs after parity.

## Routing → Spring MVC

| Laravel | Spring |
|---|---|
| `Route::get('/x', [C::class,'m'])` | `@GetMapping("/x")` on controller method |
| Route model binding | `@PathVariable` + repository lookup, or a resolver |
| Route groups + prefix | `@RequestMapping` at class level |
| `apiResource` | one `@RestController` with CRUD methods |
| Named routes | not needed; keep URL literal (parity) |

Preserve exact paths, HTTP verbs, and status codes — the judge diffs these.

## Middleware → filters / interceptors

| Laravel | Spring |
|---|---|
| Global middleware | `OncePerRequestFilter` |
| Route middleware | `HandlerInterceptor` |
| Middleware groups | ordered filter chain / `SecurityFilterChain` |
| `terminate()` | `afterCompletion` |

## Validation

| Laravel | Spring |
|---|---|
| `FormRequest` rules | `@Valid` DTO + Jakarta constraints |
| `required`, `email`, `max` | `@NotNull`, `@Email`, `@Size` |
| Custom rule (`Rule` object) | custom `ConstraintValidator` |
| `$request->validated()` | validated DTO instance |
| Error response shape | replicate via `@ControllerAdvice` — match JSON exactly |

## Queues and jobs

| Laravel | Spring |
|---|---|
| `implements ShouldQueue` | `@Async` method or message listener |
| `dispatch(new Job(...))` | publish to broker / call async service |
| `php artisan queue:work` | `@RabbitListener` / `@SqsListener` consumer |
| Horizon | broker dashboard + Spring Boot Actuator |
| Job retries / backoff | listener retry config / Resilience4j |
| Delayed dispatch | message TTL / delayed exchange |

## Events and listeners

| Laravel | Spring |
|---|---|
| `Event::dispatch($e)` | `ApplicationEventPublisher.publishEvent(e)` |
| `EventServiceProvider` map | `@EventListener` methods |
| Queued listeners | `@Async @EventListener` or `@TransactionalEventListener` |
| Model observers | JPA entity listeners (`@EntityListeners`) |

## Scheduling

| Laravel | Spring |
|---|---|
| `Kernel::schedule()` | `@Scheduled` methods |
| `->cron('* * * * *')` | `@Scheduled(cron="...")` |
| `->everyMinute()` | `@Scheduled(fixedRate=60000)` |
| `->withoutOverlapping()` | ShedLock or `@Scheduled` + guard |

## Blade → server-side templates

| Laravel | Spring |
|---|---|
| Blade `.blade.php` | Thymeleaf `.html` |
| `@if`, `@foreach` | `th:if`, `th:each` |
| `{{ $var }}` (escaped) | `th:text` (escaped by default) |
| `{!! $raw !!}` | `th:utext` |
| `@extends` / `@section` | Thymeleaf layout dialect |
| Components | Thymeleaf fragments |

For API-only apps, Blade usually maps to JSON responses instead — no template engine needed.

## Artisan → Spring Boot equivalents

| Laravel | Spring |
|---|---|
| Custom Artisan command | `CommandLineRunner` / `ApplicationRunner` or Spring Shell |
| `php artisan migrate` | Flyway / Liquibase migrations |
| `php artisan tinker` | not needed; use tests |
| Service providers | `@Configuration` classes |
| Facades (`Cache::`, `DB::`) | injected beans (`CacheManager`, `JdbcTemplate`) |
| Config (`config/*.php`) | `application.yml` + `@ConfigurationProperties` |
| `.env` | `application.yml` + externalized secrets |

## Auth

| Laravel | Spring |
|---|---|
| Guards | `SecurityFilterChain` |
| Sanctum / Passport tokens | Spring Security + jjwt / OAuth2 Resource Server |
| `Auth::user()` | `SecurityContextHolder` / `@AuthenticationPrincipal` |
| Gates / Policies | `@PreAuthorize` / method security |
| Password hashing (`bcrypt`) | `BCryptPasswordEncoder` — same algorithm, verify cost factor |

Keep token/session format compatible during the strangler period so both stacks accept the same credentials.
