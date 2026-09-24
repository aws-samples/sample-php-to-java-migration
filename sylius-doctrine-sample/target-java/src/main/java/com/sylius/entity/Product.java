package com.sylius.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Structural slice of Sylius's {@code Product} model (source:
 * {@code src/Sylius/Bundle/CoreBundle/Resources/config/doctrine/model/Product.orm.xml} — base
 * fields plus {@code mainTaxon}/{@code channels} only; {@code productTaxons}, {@code reviews},
 * and {@code images} were left out to keep this slice bounded).
 *
 * <p>The source declares this as a Doctrine {@code <mapped-superclass>}, not a concrete
 * {@code <entity>} — Sylius's Component layer never defines a table-owning entity itself; a
 * downstream application (a Sylius-based shop) provides the concrete {@code App\Entity\Product
 * extends Sylius\Component\Core\Model\Product} with its own mapping. That downstream application
 * doesn't exist in this framework repo, so there's nothing to mirror faithfully — this class is
 * flattened directly into a concrete {@code @Entity} rather than modeled as an abstract
 * {@code @MappedSuperclass} with an empty concrete subclass, which would add a layer of
 * indirection with no real content behind it for this bounded pilot.
 *
 * <p><b>Interface-relationship resolution:</b> the source's {@code mainTaxon} targets
 * {@code Sylius\Component\Taxonomy\Model\TaxonInterface} and {@code channels} targets
 * {@code Sylius\Component\Channel\Model\ChannelInterface} — both resolved via Doctrine's
 * {@code ResolveTargetEntityListener} to a concrete class in a downstream app's service config.
 * That config doesn't exist in this repo (see {@code MIGRATION_SPEC.md} §10), so the concrete
 * types below ({@link Taxon}, {@link Channel}) are a documented decision made for this pilot, not
 * a value read from any actual Sylius configuration.
 */
@Entity
@Table(name = "sylius_product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_selection_method", nullable = false)
    private String variantSelectionMethod;

    @Column(name = "average_rating", nullable = false)
    private Double averageRating = 0.0;

    @ManyToOne
    @JoinColumn(name = "main_taxon_id")
    private Taxon mainTaxon;

    // FetchType.EAGER here is a deliberate, bounded-pilot-only choice: @ManyToMany defaults to
    // LAZY, and with open-in-view disabled (application.yml) the Hibernate session closes before
    // Jackson serializes the response, throwing LazyInitializationException. EAGER is fine for a
    // small read-only demo; a real migration with non-trivial product/channel counts should use a
    // DTO projection or an explicit fetch-join query instead of eager-loading a many-to-many by
    // default, to avoid the N+1/cartesian-product cost that comes with it at scale.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "sylius_product_channels",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "channel_id"))
    @OrderBy("id ASC")
    private Set<Channel> channels = new LinkedHashSet<>();

    public Long getId() { return id; }

    public String getVariantSelectionMethod() { return variantSelectionMethod; }
    public void setVariantSelectionMethod(String variantSelectionMethod) { this.variantSelectionMethod = variantSelectionMethod; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Taxon getMainTaxon() { return mainTaxon; }
    public void setMainTaxon(Taxon mainTaxon) { this.mainTaxon = mainTaxon; }

    public Set<Channel> getChannels() { return channels; }
}
