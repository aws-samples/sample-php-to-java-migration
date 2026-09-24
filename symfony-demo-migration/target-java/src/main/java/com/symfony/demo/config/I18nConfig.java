package com.symfony.demo.config;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Wires the application {@link MessageSource} for internationalized text, replacing the Symfony
 * translation component. The catalog is converted from the PHP XLIFF files
 * ({@code translations/messages+intl-icu.en.xlf} and {@code validators+intl-icu.en.xlf}) into
 * {@code src/main/resources/i18n/messages*.properties}, keeping the original dotted message keys
 * (e.g. {@code title.post_list}, {@code action.save}, {@code post.no_posts_found},
 * {@code paginator.previous}, {@code comment.blank}) so lookups match the PHP source exactly.
 *
 * <p>English is the application default. {@code fallbackToSystemLocale} is disabled so an unknown
 * locale falls back to the base {@code messages.properties} (English) rather than the JVM's system
 * locale, matching Symfony's {@code default_locale: en} behavior.
 *
 * <p><b>ICU vs MessageFormat:</b> the PHP catalog uses ICU MessageFormat named/typed arguments
 * (e.g. {@code {status_code, number}}, {@code {count, plural, ...}}, {@code {title}}). Spring's
 * {@code MessageSource} uses {@link java.text.MessageFormat}, which only supports positional
 * ({@code {0}}) arguments. The ICU literals are preserved verbatim in the properties and are not
 * argument-formatted; see {@code BEHAVIOR_CHANGES.md}.
 *
 * <p>This class intentionally contains <b>only</b> the {@code MessageSource} bean. The
 * {@code LocaleResolver} and {@code WebMvcConfigurer} locale-negotiation wiring is deferred to task
 * 12.2 to avoid a merge conflict.
 */
@Configuration
public class I18nConfig {

    /**
     * Reloadable, classpath-based message source rooted at {@code i18n/messages}. Uses UTF-8 and
     * does not fall back to the system locale, so English ({@code messages.properties}) is the
     * effective default for unmatched locales.
     *
     * @return the configured {@link MessageSource}
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(Locale.ENGLISH);
        return messageSource;
    }
}
