package com.symfony.demo.event;

import com.symfony.demo.entity.Comment;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a blog {@link Comment} is successfully persisted. Port of the PHP
 * {@code App\Event\CommentCreatedEvent}, which extended Symfony's generic {@code Event} and
 * carried the created {@code Comment} ({@code CommentCreatedEvent(Comment $comment)} with a
 * {@code getComment()} accessor).
 *
 * <p>Modeled as a Spring {@link ApplicationEvent} so it can be dispatched through
 * {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)} and consumed
 * by an {@code @EventListener} (the notification listener is task 9.2). The event {@code source}
 * is the created comment itself, and {@link #getComment()} exposes it with the same accessor name
 * as the PHP event to preserve call-site parity.
 */
public class CommentCreatedEvent extends ApplicationEvent {

    private final Comment comment;

    /**
     * @param comment the freshly persisted comment (must not be {@code null})
     */
    public CommentCreatedEvent(Comment comment) {
        super(comment);
        this.comment = comment;
    }

    /** Returns the created comment, mirroring PHP {@code CommentCreatedEvent::getComment()}. */
    public Comment getComment() {
        return comment;
    }
}
