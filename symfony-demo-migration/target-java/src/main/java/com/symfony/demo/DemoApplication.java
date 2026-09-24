package com.symfony.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Symfony Demo blog, migrated from PHP/Symfony 8 to Java 21 + Spring Boot.
 *
 * <p>The package root {@code com.symfony.demo} mirrors the PHP {@code App\} namespace for
 * traceability (Requirement 1.2). Component scanning starts here, so all controllers,
 * services, repositories, and configuration live under this package.
 *
 * <p>{@code @ConfigurationPropertiesScan} registers {@code @ConfigurationProperties} beans
 * such as {@link com.symfony.demo.config.DemoProperties} (Requirement 15.1).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
