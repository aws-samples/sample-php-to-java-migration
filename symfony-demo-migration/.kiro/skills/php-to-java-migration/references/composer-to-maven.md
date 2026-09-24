# Composer → Maven / Spring: Extended Dependency Map

Extended replacement table beyond the core set in the skill. Add dependencies to `pom.xml` incrementally — only when a phase needs them. List anything unmapped in `DEPENDENCY_GAPS.md` and resolve before business-logic translation.

## HTTP and clients

| Composer package | Java replacement | Notes |
|---|---|---|
| guzzlehttp/guzzle | Spring `RestClient` (3.2+) / `WebClient` | `RestClient` sync, `WebClient` reactive |
| symfony/http-client | Spring `RestClient` | — |
| kriswallsmith/buzz | Spring `RestClient` | — |
| php-http/httplug | Spring `RestClient` | — |

## Persistence and data

| Composer package | Java replacement | Notes |
|---|---|---|
| doctrine/orm | Spring Data JPA + Hibernate | — |
| doctrine/dbal | Spring `JdbcTemplate` / JDBC | — |
| illuminate/database (Eloquent) | Spring Data JPA | — |
| doctrine/migrations, laravel migrations | Flyway or Liquibase | keep schema stable during migration |
| predis/predis, ext-redis | Spring Data Redis + Lettuce | — |
| elasticsearch/elasticsearch | Spring Data Elasticsearch | — |
| mongodb/mongodb | Spring Data MongoDB | — |

## Serialization and data formats

| Composer package | Java replacement | Notes |
|---|---|---|
| symfony/serializer | Jackson (`ObjectMapper`) | match field naming + null handling exactly |
| jms/serializer | Jackson | — |
| nesbot/carbon | java.time | flag every lenient parse |
| ramsey/uuid | java.util.UUID | — |
| brick/math | java.math.BigDecimal / BigInteger | money = BigDecimal |
| league/csv | Apache Commons CSV / OpenCSV | — |
| symfony/yaml | SnakeYAML / Jackson YAML | — |

## Validation and DI

| Composer package | Java replacement | Notes |
|---|---|---|
| symfony/validator | Jakarta Bean Validation (3.0+) | `jakarta.*` namespace |
| respect/validation | Jakarta Bean Validation | — |
| php-di/php-di, symfony/dependency-injection | Spring DI | — |

## Auth and security

| Composer package | Java replacement | Notes |
|---|---|---|
| firebase/php-jwt | jjwt 0.12+ | — |
| lcobucci/jwt | jjwt / Nimbus JOSE | — |
| laravel/sanctum, laravel/passport | Spring Security + OAuth2 / jjwt | keep token format compatible during strangler |
| paragonie/* crypto | Java `javax.crypto` / Bouncy Castle | verify algorithm parity |

## Messaging and async

| Composer package | Java replacement | Notes |
|---|---|---|
| php-amqplib/php-amqplib | Spring AMQP | — |
| symfony/messenger | Spring AMQP / JMS / Spring Integration | — |
| enqueue/* | Spring JMS / AMQP | — |
| aws/aws-sdk-php (SQS/SNS) | AWS SDK for Java v2 + Spring Cloud AWS | — |

## Logging and observability

| Composer package | Java replacement | Notes |
|---|---|---|
| monolog/monolog | SLF4J + Logback | match log format if downstream parses it |
| sentry/sentry | sentry-spring-boot-starter | — |
| open-telemetry/* | OpenTelemetry Java agent / SDK | — |

## Files, storage, media

| Composer package | Java replacement | Notes |
|---|---|---|
| league/flysystem | Spring `Resource` + provider SDK | — |
| aws/aws-sdk-php (S3) | AWS SDK for Java v2 (S3) | — |
| intervention/image | Thumbnailator / ImageIO / imgscalr | — |
| dompdf/dompdf, tecnickcom/tcpdf | OpenPDF or iText | verify layout parity |
| phpoffice/phpspreadsheet | Apache POI | — |

## Testing

| Composer package | Java replacement | Notes |
|---|---|---|
| phpunit/phpunit | JUnit 5 + AssertJ | — |
| mockery/mockery | Mockito | — |
| fakerphp/faker | Datafaker / Instancio | — |
| behat/behat | Cucumber-JVM | — |

## Utilities

| Composer package | Java replacement | Notes |
|---|---|---|
| vlucas/phpdotenv | application.yml + `@ConfigurationProperties` | secrets stay external |
| symfony/cache, illuminate/cache | Spring Cache + Redis/Caffeine | — |
| nikic/php-parser | (analysis only) JavaParser if needed | usually only used at migration time |
| guzzlehttp/promises | `CompletableFuture` | — |
| league/fractal | Jackson views / DTO mappers | match output shape exactly |
| symfony/console | Spring Shell / `CommandLineRunner` | — |

## Handling unmapped packages

For a Composer package with no clean Java equivalent:
1. Determine what it actually does in *this* codebase (grep its usages — often a small subset of the library).
2. Prefer a well-maintained Java library; pin an exact version; check for typosquatting on unfamiliar names.
3. If no library fits, implement the used subset behind a small interface and cover it with parity tests.
4. Record the decision in `DEPENDENCY_GAPS.md` with the rationale.
