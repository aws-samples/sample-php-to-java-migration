package com.symfony.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Application user, mapped to {@code symfony_demo_user}. Structural port of the PHP
 * {@code App\Entity\User} Doctrine entity. Spring Security integration (UserDetails adapter,
 * password encoding) is added later (task 6.1); this class stays a clean JPA entity.
 */
@Entity
@Table(name = "symfony_demo_user")
public class User {

    // Role constants mirror the PHP entity so usages are traceable and typo-safe.
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Convert(converter = RolesConverter.class)
    @Column(name = "roles", nullable = false)
    private List<String> roles = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns the roles granted to the user. Guarantees every user has at least
     * {@link #ROLE_USER} and returns a de-duplicated list (insertion order preserved),
     * matching the PHP {@code getRoles()} behavior ({@code array_unique} + default role).
     */
    public List<String> getRoles() {
        List<String> effective = new ArrayList<>(roles == null ? List.of() : roles);
        if (effective.isEmpty()) {
            effective.add(ROLE_USER);
        }
        return new ArrayList<>(new LinkedHashSet<>(effective));
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }
}
