-- V1__schema.sql
--
-- Schema for the Symfony Demo blog, ported unchanged from the original Doctrine-managed
-- SQLite database (symfony-demo-migration/source-php/data/database.sqlite). Table names,
-- column names/types, unique constraints, foreign keys and indexes are reproduced exactly
-- so the Java port runs against a byte-for-byte compatible schema (Requirements 1.4, 16.1).
--
-- Types mirror Doctrine's SQLite output: VARCHAR(255) for strings, CLOB for TEXT/JSON,
-- DATETIME for timestamps, INTEGER PRIMARY KEY AUTOINCREMENT for identities.
-- Tables are created in FK-dependency order: user -> tag -> post -> post_tag -> comment.

-- Users -----------------------------------------------------------------------
CREATE TABLE symfony_demo_user (
    id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    username  VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL,
    password  VARCHAR(255) NOT NULL,
    roles     CLOB NOT NULL --(DC2Type:json)
);
CREATE UNIQUE INDEX UNIQ_8FB094A1F85E0677 ON symfony_demo_user (username);
CREATE UNIQUE INDEX UNIQ_8FB094A1E7927C74 ON symfony_demo_user (email);

-- Tags ------------------------------------------------------------------------
CREATE TABLE symfony_demo_tag (
    id   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name VARCHAR(255) NOT NULL
);
CREATE UNIQUE INDEX UNIQ_4D5855405E237E06 ON symfony_demo_tag (name);

-- Posts -----------------------------------------------------------------------
CREATE TABLE symfony_demo_post (
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    author_id    INTEGER NOT NULL,
    title        VARCHAR(255) NOT NULL,
    slug         VARCHAR(255) NOT NULL,
    summary      VARCHAR(255) NOT NULL,
    content      CLOB NOT NULL,
    published_at DATETIME NOT NULL,
    CONSTRAINT FK_58A92E65F675F31B FOREIGN KEY (author_id)
        REFERENCES symfony_demo_user (id) NOT DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX IDX_58A92E65F675F31B ON symfony_demo_post (author_id);

-- Post <-> Tag join table -----------------------------------------------------
CREATE TABLE symfony_demo_post_tag (
    post_id INTEGER NOT NULL,
    tag_id  INTEGER NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT FK_6ABC1CC44B89032C FOREIGN KEY (post_id)
        REFERENCES symfony_demo_post (id) ON DELETE CASCADE NOT DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT FK_6ABC1CC4BAD26311 FOREIGN KEY (tag_id)
        REFERENCES symfony_demo_tag (id) ON DELETE CASCADE NOT DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX IDX_6ABC1CC44B89032C ON symfony_demo_post_tag (post_id);
CREATE INDEX IDX_6ABC1CC4BAD26311 ON symfony_demo_post_tag (tag_id);

-- Comments --------------------------------------------------------------------
CREATE TABLE symfony_demo_comment (
    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    post_id      INTEGER NOT NULL,
    author_id    INTEGER NOT NULL,
    content      CLOB NOT NULL,
    published_at DATETIME NOT NULL,
    CONSTRAINT FK_53AD8F834B89032C FOREIGN KEY (post_id)
        REFERENCES symfony_demo_post (id) NOT DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT FK_53AD8F83F675F31B FOREIGN KEY (author_id)
        REFERENCES symfony_demo_user (id) NOT DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX IDX_53AD8F834B89032C ON symfony_demo_comment (post_id);
CREATE INDEX IDX_53AD8F83F675F31B ON symfony_demo_comment (author_id);
