# Symfony → Spring Boot

Idiom map for Symfony-specific constructs. These seed the `RULEBOOK.md` for a Symfony source. Preserve behavior first; adopt idiomatic Spring only after parity.

## Dependency injection container

| Symfony | Spring |
|---|---|
| Service autowiring | constructor injection + component scan |
| `services.yaml` | `@Configuration` + `@Bean`, or `@Component` scan |
| Service tags | `@Qualifier`, marker interfaces, or `List<T>` injection |
| Compiler passes | `BeanFactoryPostProcessor` (rare) |
| `#[Autowire]` attribute | `@Autowired` / constructor param |
| Lazy services | `@Lazy` |
| Service decoration | `@Primary` + delegation, or `BeanPostProcessor` |

## Routing → Spring MVC

| Symfony | Spring |
|---|---|
| `#[Route('/x', methods:['GET'])]` | `@GetMapping("/x")` |
| Route requirements/regex | `@GetMapping` path regex |
| `#[MapQueryParameter]` (7+) | `@RequestParam` |
| `#[MapRequestPayload]` | `@RequestBody` + `@Valid` |
| Route prefix on controller | class-level `@RequestMapping` |
| Enum in route (6+) | `@PathVariable` + enum converter |

Preserve exact paths, verbs, and status codes — the judge diffs these.

## Doctrine ORM → Spring Data JPA

| Doctrine | Spring / JPA |
|---|---|
| `#[ORM\Entity]` | `@Entity` |
| `#[ORM\Column]` | `@Column` |
| `#[ORM\Id]` `#[ORM\GeneratedValue]` | `@Id` `@GeneratedValue` |
| `#[ORM\OneToMany]` etc. | `@OneToMany` etc. (same names) |
| `EntityManager::persist/flush` | `repository.save` / `EntityManager` |
| Repository classes | `JpaRepository` interfaces |
| DQL | JPQL (`@Query`) |
| QueryBuilder | `Specification` / Criteria API |
| Lifecycle callbacks | `@PrePersist`, `@PostLoad`, etc. |
| Migrations (doctrine/migrations) | Flyway / Liquibase |

Doctrine defaults to lazy loading via proxies. Replicate observed query counts first; tune with fetch graphs after parity.

## Validation

| Symfony | Spring |
|---|---|
| `#[Assert\NotBlank]` | `@NotBlank` |
| `#[Assert\Email]` | `@Email` |
| `#[Assert\Length(max:...)]` | `@Size` |
| Custom constraint + validator | `@Constraint` + `ConstraintValidator` |
| Validation groups | Jakarta validation groups |
| `ValidatorInterface` | `Validator` bean / `@Valid` |

## Messenger → messaging / async

| Symfony Messenger | Spring |
|---|---|
| Message + handler | event/command + `@RabbitListener` / `@SqsListener` |
| `MessageBusInterface::dispatch` | publish to broker or `ApplicationEventPublisher` |
| Transports (AMQP, Doctrine, Redis) | Spring AMQP / JMS / Spring Data Redis |
| `messenger:consume` worker | listener container (auto-managed) |
| Middleware (retry, validation) | listener config / advice |
| Stamps | message headers |

## Event dispatcher

| Symfony | Spring |
|---|---|
| `EventDispatcherInterface::dispatch` | `ApplicationEventPublisher.publishEvent` |
| `#[AsEventListener]` | `@EventListener` |
| Event subscribers | `@EventListener` methods on a bean |
| Kernel events (`kernel.request`) | filters / `HandlerInterceptor` / `@ControllerAdvice` |

## Twig → templates

| Twig | Spring |
|---|---|
| `.twig` templates | Thymeleaf `.html` |
| `{{ var }}` (escaped) | `th:text` |
| `{{ var|raw }}` | `th:utext` |
| `{% if %}` / `{% for %}` | `th:if` / `th:each` |
| `{% extends %}` / `{% block %}` | Thymeleaf layout dialect |
| Filters | Thymeleaf expression utilities / custom dialect |

API-only services usually map Twig responses to JSON instead.

## Console → CLI

| Symfony | Spring |
|---|---|
| `#[AsCommand]` command | Spring Shell command or `CommandLineRunner` |
| `#[AsCronTask]` (7+) | `@Scheduled(cron=...)` |
| Console input/output | Spring Shell IO / logging |

## Configuration

| Symfony | Spring |
|---|---|
| `config/packages/*.yaml` | `application.yml` profiles |
| Environment variables / `.env` | `application.yml` + externalized secrets |
| Parameters (`%param%`) | `@Value` / `@ConfigurationProperties` |
| Bundles | starters + `@Configuration` |

## Security

| Symfony | Spring |
|---|---|
| `security.yaml` firewalls | `SecurityFilterChain` |
| Authenticators / guards | `AuthenticationProvider` / filters |
| Voters | `@PreAuthorize` / `AccessDecisionManager` |
| `#[IsGranted]` | `@PreAuthorize` |
| Password hashers | `PasswordEncoder` — match algorithm and cost |
| JWT (LexikJWTAuthenticationBundle) | Spring Security + jjwt / OAuth2 Resource Server |

Keep token/session format compatible during the strangler period so both stacks accept the same credentials.
