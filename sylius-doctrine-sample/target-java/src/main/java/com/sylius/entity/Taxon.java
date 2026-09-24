package com.sylius.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural slice of Sylius's {@code Taxon} model (source:
 * {@code src/Sylius/Bundle/TaxonomyBundle/Resources/config/doctrine/model/Taxon.orm.xml}).
 *
 * <p><b>Deliberate redesign, not a direct translation:</b> the source models the tree via
 * Gedmo's {@code nested-set} extension ({@code tree-left}/{@code tree-right}/{@code tree-level}
 * bookkeeping columns, maintained automatically by a Doctrine event listener on every
 * insert/move/delete). There is no JPA/Hibernate equivalent of that automatic bookkeeping. Rather
 * than hand-implement nested-set maintenance in Java — a real but disproportionate amount of work
 * for a bounded pilot — this entity uses a plain adjacency list ({@code parent}/{@code children}
 * via a self-referencing {@code @ManyToOne}/{@code @OneToMany}), which is a legitimate, common
 * Java-side pattern for trees and is explicitly a redesign decision, not a bug: nested sets exist
 * specifically to make subtree reads a single range query, which matters far less once the app is
 * off MySQL-era tree-query optimization concerns. A real migration adopting this simplification
 * needs to confirm no caller actually depends on nested-set-specific query patterns (e.g.
 * "get entire subtree in one query" via {@code left BETWEEN x AND y}) before relying on it.
 */
@Entity
@Table(name = "sylius_taxon")
public class Taxon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Boolean enabled = false;

    private Integer position;

    // Source target-entity is `Sylius\Component\Taxonomy\Model\TaxonInterface`, resolved here to
    // this same concrete class since Taxon is self-referencing and no downstream
    // resolve_target_entities config exists in the framework repo to confirm a different choice.
    @ManyToOne
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private Taxon parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.PERSIST)
    @OrderBy("position ASC, id ASC")
    @JsonIgnore
    private List<Taxon> children = new ArrayList<>();

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Taxon getParent() { return parent; }
    public void setParent(Taxon parent) { this.parent = parent; }

    public List<Taxon> getChildren() { return children; }
}
