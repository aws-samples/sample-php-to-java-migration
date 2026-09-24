package com.bookstack.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural slice of BookStack's {@code Book} model (source: {@code app/Entities/Models/Book.php},
 * schema from {@code create_books_table} + later {@code add_description_html_to_entities} /
 * {@code add_default_template_to_books} / {@code add_sort_rule_id_to_books} migrations —
 * only name/slug/description ported here; cover image, default template, and sort rule are out
 * of scope for this slice). {@code description}/{@code descriptionHtml} were not in the
 * php-model-discovery manifest's {@code hidden} list, so they stay visible here unlike Page's
 * html/text (see {@link Page}).
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "book", cascade = CascadeType.PERSIST)
    private List<Chapter> chapters = new ArrayList<>();

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<Chapter> getChapters() { return chapters; }
}
