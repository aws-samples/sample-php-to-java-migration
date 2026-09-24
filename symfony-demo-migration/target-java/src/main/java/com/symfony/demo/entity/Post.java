package com.symfony.demo.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Blog post, mapped to {@code symfony_demo_post}. Structural port of the PHP
 * {@code App\Entity\Post} entity. {@code publishedAt} is set on construction; comments are
 * ordered newest first and tags alphabetically, mirroring the Doctrine {@code OrderBy} mappings.
 * The {@code addComment}/{@code addTag} helpers reproduce the PHP association-management logic.
 */
@Entity
@Table(name = "symfony_demo_post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "post", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("publishedAt DESC")
    private List<Comment> comments = new ArrayList<>();

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(
            name = "symfony_demo_post_tag",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @OrderBy("name ASC")
    private List<Tag> tags = new ArrayList<>();

    public Post() {
        this.publishedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public List<Comment> getComments() {
        return comments;
    }

    /**
     * Adds a comment, setting both sides of the association (mirrors the PHP {@code addComment}).
     */
    public void addComment(Comment comment) {
        comment.setPost(this);
        if (!comments.contains(comment)) {
            comments.add(comment);
        }
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
    }

    public List<Tag> getTags() {
        return tags;
    }

    /**
     * Adds one or more tags, skipping duplicates (mirrors the PHP varargs {@code addTag}).
     */
    public void addTag(Tag... tags) {
        for (Tag tag : tags) {
            if (!this.tags.contains(tag)) {
                this.tags.add(tag);
            }
        }
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
    }
}
