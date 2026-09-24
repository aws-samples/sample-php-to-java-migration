package com.symfony.demo.event;

import com.symfony.demo.config.DemoProperties;
import com.symfony.demo.entity.Comment;
import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CommentNotificationListener} (task 9.2). Uses a mocked
 * {@link org.springframework.mail.javamail.JavaMailSender} so no live SMTP server is required, and
 * a real Thymeleaf engine wired to the classpath templates so the actual email body is rendered.
 *
 * <p>Asserts the parity points: the email is addressed to the <em>post author</em>, carries the
 * expected subject, and the HTML body contains the post title and the absolute link (with the
 * {@code #comment_{id}} fragment).
 */
class CommentNotificationListenerTest {

    private org.springframework.mail.javamail.JavaMailSender mailSender;
    private CommentNotificationListener listener;

    @BeforeEach
    void setUp() {
        // Real MimeMessage creation via a stub sender, but send(...) is mocked so nothing is
        // transmitted. Spying JavaMailSenderImpl lets createMimeMessage() work normally.
        mailSender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        JavaMailSenderImpl real = new JavaMailSenderImpl();
        when(mailSender.createMimeMessage()).thenReturn(real.createMimeMessage());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        DemoProperties properties = new DemoProperties(
                "en",
                "http://localhost",
                List.of("en"),
                new DemoProperties.Notifications("anonymous@example.com"));

        listener = new CommentNotificationListener(mailSender, templateEngine(), properties);
    }

    @Test
    void sendsHtmlEmailToPostAuthorWithSubjectTitleAndLink() throws Exception {
        User postAuthor = new User();
        postAuthor.setEmail("author@example.com");
        postAuthor.setFullName("Post Author");

        Post post = new Post();
        post.setTitle("Hello World");
        post.setSlug("hello-world");
        post.setAuthor(postAuthor);

        Comment comment = new Comment();
        comment.setContent("Nice post!");
        post.addComment(comment);

        listener.onCommentCreated(new CommentCreatedEvent(comment));

        org.mockito.ArgumentCaptor<MimeMessage> captor =
                org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("author@example.com");
        assertThat(sent.getSubject()).isEqualTo("A new comment has been posted");

        String rendered = extractBody(sent);
        assertThat(rendered).contains("Hello World");
        assertThat(rendered).contains("http://localhost/blog/posts/hello-world#comment_");
    }

    private static String extractBody(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
