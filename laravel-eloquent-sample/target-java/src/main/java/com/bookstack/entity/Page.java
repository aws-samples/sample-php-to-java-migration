package com.bookstack.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Structural slice of BookStack's {@code Page} model (source:
 * {@code app/Entities/Models/Page.php}). {@code html}/{@code markdown}/{@code text} are
 * {@code @JsonIgnore} here specifically because the php-model-discovery manifest recorded them in
 * Page's Eloquent {@code $hidden} list — this mirrors that default serialization visibility
 * rather than being an arbitrary Java-side choice. A real content endpoint (as BookStack's own
 * API does) would need an explicit DTO that deliberately includes them; that's out of scope for
 * this read-only list/get slice.
 */
@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    @JsonIgnore
    private Book book;

    // Nullable: pages can be direct children of a Book with no Chapter.
    @ManyToOne
    @JoinColumn(name = "chapter_id")
    @JsonIgnore
    private Chapter chapter;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String html;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String markdown;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false)
    private Boolean draft = false;

    @Column(nullable = false)
    private Boolean template = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }

    public Chapter getChapter() { return chapter; }
    public void setChapter(Chapter chapter) { this.chapter = chapter; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }

    public String getMarkdown() { return markdown; }
    public void setMarkdown(String markdown) { this.markdown = markdown; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getDraft() { return draft; }
    public void setDraft(Boolean draft) { this.draft = draft; }

    public Boolean getTemplate() { return template; }
    public void setTemplate(Boolean template) { this.template = template; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
