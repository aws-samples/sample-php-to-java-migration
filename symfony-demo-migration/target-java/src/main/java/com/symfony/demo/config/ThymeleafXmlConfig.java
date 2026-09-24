package com.symfony.demo.config;

import java.util.Set;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Registers an additional Thymeleaf template resolver that renders the blog RSS feed as XML.
 *
 * <p>The public blog templates (task 8.3) are ordinary HTML rendered by Spring Boot's
 * autoconfigured Thymeleaf resolver. The RSS feed, however, must be emitted as well-formed XML:
 * in HTML template mode Thymeleaf treats {@code <link>} as a void element and would break the
 * {@code <link>...</link>} elements an RSS 2.0 document requires. This resolver handles only the
 * {@code blog/rss} view, loading {@code templates/blog/rss.xml} in {@link TemplateMode#XML} so the
 * feed serializes correctly. It is ordered ahead of the default resolver and restricted via
 * {@code resolvablePatterns} so every other view continues to use the standard HTML resolver.
 *
 * <p>The {@link BlogController} returns the {@code blog/rss} view name with an
 * {@code application/xml} content type; this resolver supplies the matching XML template.
 */
@Configuration
public class ThymeleafXmlConfig {

    /**
     * XML-mode resolver scoped to the {@code blog/rss} view only.
     *
     * @param applicationContext the Spring context (used to load classpath template resources)
     * @return a template resolver that renders {@code templates/blog/rss.xml} as XML
     */
    @Bean
    public SpringResourceTemplateResolver xmlTemplateResolver(ApplicationContext applicationContext) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".xml");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setResolvablePatterns(Set.of("blog/rss"));
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        // Ahead of the autoconfigured HTML resolver so blog/rss resolves to the XML template.
        resolver.setOrder(0);
        resolver.setCacheable(false);
        return resolver;
    }
}
