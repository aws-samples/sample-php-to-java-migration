package com.sylius.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Structural slice of Sylius's {@code Channel} model (source:
 * {@code src/Sylius/Bundle/ChannelBundle/Resources/config/doctrine/model/Channel.orm.xml} — base
 * Component fields only, not CoreBundle's e-commerce overlay: currencies/locales/countries/
 * billing-data/price-history-config were deliberately left out to keep this slice bounded).
 *
 * <p>{@code createdAt}/{@code updatedAt} were mapped via Doctrine's Gedmo {@code Timestampable}
 * extension in the source (an event listener sets the value, not application code) — the Java
 * equivalent is Spring Data JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate} +
 * {@code AuditingEntityListener}, enabled via {@code @EnableJpaAuditing} on the application
 * class), which is the same "framework sets this automatically" shape as the source.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "sylius_channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String color;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean enabled = false;

    private String hostname;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
