package com.symfony.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Post tag, mapped to {@code symfony_demo_tag}. Structural port of the PHP
 * {@code App\Entity\Tag} entity. The name is set at construction and never changed
 * (PHP {@code readonly}); {@link #toString()} returns the tag name.
 */
@Entity
@Table(name = "symfony_demo_tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * No-arg constructor required by JPA. Not intended for application use.
     */
    protected Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
