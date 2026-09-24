package com.symfony.demo.event;

import com.symfony.demo.config.DemoProperties;
import com.symfony.demo.entity.Comment;
import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;

/**
 * Sends a notification email to a post's author when a new comment is created. Port of the PHP
 * {@code App\EventSubscriber\CommentNotificationSubscriber::onCommentCreated}.
 *
 * <p>PHP source behaviour reproduced here:
 * <pre>
 *   $comment = $event->getComment();
 *   $post    = $comment->getPost();
 *   $author  = $post->getAuthor();
 *   $linkToPost = $urlGenerator->generate('blog_post',
 *       ['slug' =&gt; $post->getSlug(), '_fragment' =&gt; 'comment_'.$comment->getId()], ABSOLUTE_URL);
 *   $subject = trans('notification.comment_created');
 *   $body    = trans('notification.comment_created.description', ['title' =&gt; ..., 'link' =&gt; ...]);
 *   $email   = (new Email())->from($sender)->to($author->getEmail())->subject($subject)->html($body);
 *   $mailer->send($email);
 * </pre>
 *
 * <p>Parity notes:
 * <ul>
 *   <li><b>Recipient</b> is the <em>post author</em> ({@code comment.getPost().getAuthor()}),
 *       not the comment author, matching the PHP subscriber.</li>
 *   <li><b>From</b> is the configured sender {@code app.notifications.email-sender}
 *       ({@code %app.notifications.email_sender%} in Symfony, default
 *       {@code anonymous@example.com}), bound via {@link DemoProperties}.</li>
 *   <li><b>Subject</b> uses the English literal for {@code notification.comment_created}
 *       ("A new comment has been posted"). MessageSource-based i18n is task 12.1; a plain English
 *       literal is used here, consistent with how the public templates (task 8.3) handled
 *       translations.</li>
 *   <li><b>Body</b> is HTML rendered from the Thymeleaf template
 *       {@code templates/email/comment_notification.html} with model variables {@code title}
 *       (post title) and {@code link} (absolute post URL + {@code #comment_{id}} fragment).</li>
 *   <li><b>Link</b> is built from the configured base URI ({@code app.default-uri}, mirroring the
 *       Symfony {@code DEFAULT_URI} used for absolute URL generation) plus the {@code blog_post}
 *       path and the {@code comment_{id}} fragment.</li>
 * </ul>
 *
 * <p><b>Listener style — plain {@code @EventListener} (synchronous):</b> the PHP dispatch runs
 * synchronously right after the comment is flushed. In {@code BlogController.commentNew} the event
 * is published <em>after</em> {@code CommentRepository.save(...)} has already committed its own
 * transaction, and the controller itself is not transactional, so there is no transaction bound to
 * the publishing thread. A {@code @TransactionalEventListener(phase = AFTER_COMMIT)} would
 * therefore silently not fire (no active transaction to hook), so a plain {@code @EventListener} is
 * both correct and the closest parity match. The comment is already persisted at publish time, so
 * {@code comment.getId()} is available for the fragment link.
 *
 * <p><b>Mail failure handling:</b> the send is wrapped so a missing/unavailable SMTP server (e.g.
 * the dev default {@code localhost:1025}) does not break the comment POST. The PHP demo uses a
 * {@code null://null} transport that silently discards mail; logging a warning on failure keeps the
 * request succeeding, matching that non-fatal behaviour. See BEHAVIOR_CHANGES.md.
 */
@Component
public class CommentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(CommentNotificationListener.class);

    /** English literal for the PHP {@code notification.comment_created} key (i18n is task 12.1). */
    static final String SUBJECT = "A new comment has been posted";

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final DemoProperties properties;

    public CommentNotificationListener(JavaMailSender mailSender,
                                       SpringTemplateEngine templateEngine,
                                       DemoProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    /**
     * Reacts to {@link CommentCreatedEvent} and emails the post author. Synchronous, mirroring the
     * PHP subscriber dispatch order (after the comment is persisted).
     *
     * @param event the published event carrying the freshly created comment
     */
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        Comment comment = event.getComment();
        Post post = comment.getPost();
        User author = post.getAuthor();
        String recipient = author.getEmail();

        String link = buildPostLink(post.getSlug(), comment.getId());

        Context context = new Context();
        context.setVariable("title", post.getTitle());
        context.setVariable("link", link);
        String body = templateEngine.process("email/comment_notification", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.notifications().emailSender());
            helper.setTo(recipient);
            helper.setSubject(SUBJECT);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            // Non-fatal: the PHP demo's null transport discards mail. Do not fail the comment POST
            // when no SMTP server is reachable (e.g. the dev default localhost:1025).
            log.warn("Failed to send comment notification email to {}: {}", recipient,
                    ex.getMessage());
        }
    }

    /**
     * Builds the absolute URL to the post, including the {@code comment_{id}} fragment, mirroring
     * the PHP {@code urlGenerator->generate('blog_post', [...], ABSOLUTE_URL)} call.
     */
    private String buildPostLink(String slug, Integer commentId) {
        String base = properties.defaultUri();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/blog/posts/" + slug + "#comment_" + commentId;
    }
}
