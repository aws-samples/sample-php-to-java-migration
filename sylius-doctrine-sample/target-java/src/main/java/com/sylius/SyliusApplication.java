package com.sylius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @EnableJpaAuditing} is the Java-side equivalent of the source's Gedmo
 * {@code Timestampable} Doctrine extension — both set created/updated timestamps via a framework
 * listener rather than application code.
 */
@SpringBootApplication
@EnableJpaAuditing
public class SyliusApplication {
    public static void main(String[] args) {
        SpringApplication.run(SyliusApplication.class, args);
    }
}
