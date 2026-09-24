package com.symfony.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Blog comment, mapped to {@code symfony_demo_comment}. Structural port of the PHP
 * {@code App\Entity\Comment} entity. {@code publishedAt} is set on construction, matching
 * the PHP constructor.
 */
@Entity
@Table(name = "symfony_demo_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    public Comment() {
        this.publishedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }
}
