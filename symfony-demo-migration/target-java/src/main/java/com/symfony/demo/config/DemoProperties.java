package com.symfony.demo.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App-specific configuration, mirroring the Symfony {@code services.yaml} parameters and
 * {@code framework.enabled_locales} (Requirements 14.1, 14.2, 15.1).
 *
 * <p>PHP source parity:
 * <ul>
 *   <li>{@code parameters: app.locale: 'en'} &rarr; {@link #locale()}</li>
 *   <li>{@code framework.enabled_locales: ['ar', 'bg', ...]} &rarr; {@link #enabledLocales()}</li>
 *   <li>{@code parameters: app.notifications.email_sender} &rarr;
 *       {@link Notifications#emailSender()}</li>
 *   <li>{@code DEFAULT_URI} &rarr; {@link #defaultUri()}</li>
 * </ul>
 *
 * <p>Bound from the {@code app.*} keys in {@code application.yml}. Registered via
 * {@code @ConfigurationPropertiesScan} on the application class.
 */
@ConfigurationProperties(prefix = "app")
public record DemoProperties(
        String locale,
        String defaultUri,
        List<String> enabledLocales,
        Notifications notifications) {

    public DemoProperties {
        // Sensible defaults mirroring the PHP parameters when a key is absent.
        if (locale == null || locale.isBlank()) {
            locale = "en";
        }
        if (enabledLocales == null || enabledLocales.isEmpty()) {
            enabledLocales = List.of("en");
        }
        if (notifications == null) {
            notifications = new Notifications("anonymous@example.com");
        }
    }

    /** Mirrors {@code app.notifications.email_sender} from the Symfony parameters. */
    public record Notifications(String emailSender) {
        public Notifications {
            if (emailSender == null || emailSender.isBlank()) {
                emailSender = "anonymous@example.com";
            }
        }
    }
}
