-- V2__seed.sql
--
-- Demo seed data equivalent to the PHP AppFixtures (src/DataFixtures/AppFixtures.php).
-- Kept small but representative: the three demo users, the nine demo tags, a few posts
-- (first authored by Jane Doe to simplify tests, matching the fixtures) and a couple of
-- comments per post authored by john_user (Requirements 1.4, 16.1).
--
-- Intentional deviation from the PHP fixtures: user emails use the RFC 2606 reserved
-- domain example.com instead of the upstream fixtures' symfony.com, so synthetic seed
-- data cannot be mistaken for (or collide with) real mailboxes on a registered domain.
-- Usernames, roles, and password hashes are unchanged; nothing keys off the email
-- values, so login and parity behavior are unaffected. See BEHAVIOR_CHANGES.md.
--
-- Passwords are the exact bcrypt hashes of the demo password "kitten" taken from the
-- original PHP-seeded database. Symfony uses the "auto" hasher (bcrypt, $2y$ prefix);
-- Spring Security's BCryptPasswordEncoder verifies $2y$/$2a$/$2b$ hashes, so login parity
-- holds with no re-hashing. Roles are stored as a JSON array string, matching Doctrine's
-- Types::JSON -> CLOB mapping and the entity RolesConverter.

-- Users -----------------------------------------------------------------------
-- Demo credentials (password "kitten" for all):
--   jane_admin / kitten  -> ROLE_ADMIN
--   tom_admin  / kitten  -> ROLE_ADMIN
--   john_user  / kitten  -> ROLE_USER
INSERT INTO symfony_demo_user (id, full_name, username, email, password, roles) VALUES
    (1, 'Jane Doe', 'jane_admin', 'jane_admin@example.com', '$2y$13$3EJxNVreeSXjCJooqMEDsu8jjTtCWVQWKs5QaCRZ2VcrXK1ATq0E.', '["ROLE_ADMIN"]'),
    (2, 'Tom Doe',  'tom_admin',  'tom_admin@example.com',  '$2y$13$aBB/wELjpysf6PpYuXLEBuK4pEGaWJLxnC7aa/8FFjLqQmLie8ni2', '["ROLE_ADMIN"]'),
    (3, 'John Doe', 'john_user',  'john_user@example.com',  '$2y$13$RNUzIa1pIzwdARInhBNS/.m5fnZYEK5UvK0W74fJrWkmIjebLOhpy', '["ROLE_USER"]');

-- Tags ------------------------------------------------------------------------
INSERT INTO symfony_demo_tag (id, name) VALUES
    (1, 'lorem'),
    (2, 'ipsum'),
    (3, 'consectetur'),
    (4, 'adipiscing'),
    (5, 'incididunt'),
    (6, 'labore'),
    (7, 'voluptate'),
    (8, 'dolore'),
    (9, 'pariatur');

-- Posts -----------------------------------------------------------------------
-- Post 1 is authored by Jane Doe, mirroring the fixtures' guarantee for tests.
-- published_at descends so the default "newest first" ordering is deterministic.
INSERT INTO symfony_demo_post (id, author_id, title, slug, summary, content, published_at) VALUES
    (1, 1,
     'Lorem ipsum dolor sit amet consectetur adipiscing elit',
     'lorem-ipsum-dolor-sit-amet-consectetur-adipiscing-elit',
     'Lorem ipsum dolor sit amet consectetur adipiscing elit. Pellentesque vitae velit ex. Mauris dapibus risus quis suscipit vulputate.',
     'Lorem ipsum dolor sit amet consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et **dolore magna aliqua**: Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.

  * Ut enim ad minim veniam
  * Quis nostrud exercitation *ullamco laboris*
  * Nisi ut aliquip ex ea commodo consequat',
     '2024-06-01 10:00:00'),
    (2, 2,
     'Pellentesque vitae velit ex',
     'pellentesque-vitae-velit-ex',
     'Eros diam egestas libero eu vulputate risus. In hac habitasse platea dictumst. Morbi tempus commodo mattis.',
     'Praesent id fermentum lorem. Ut est lorem, fringilla at accumsan nec, euismod at nunc. Aenean mattis sollicitudin mattis. **Class aptent taciti** sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos.',
     '2024-05-30 09:30:00'),
    (3, 1,
     'Mauris dapibus risus quis suscipit vulputate',
     'mauris-dapibus-risus-quis-suscipit-vulputate',
     'Ut suscipit posuere justo at vulputate. Ut eleifend mauris et risus ultrices egestas. Aliquam sodales odio id eleifend tristique.',
     'Integer auctor massa maximus nulla scelerisque accumsan. *Aliquam ac malesuada* ex. Pellentesque tortor magna, vulputate eu vulputate ut, venenatis ac lectus. Praesent ut lacinia sem.',
     '2024-05-28 14:15:00');

-- Post <-> Tag associations ---------------------------------------------------
INSERT INTO symfony_demo_post_tag (post_id, tag_id) VALUES
    (1, 1), (1, 2),
    (2, 3), (2, 4), (2, 8),
    (3, 6), (3, 7);

-- Comments (all authored by john_user, ordered so publishedAt DESC is stable) --
INSERT INTO symfony_demo_comment (id, post_id, author_id, content, published_at) VALUES
    (1, 1, 3, 'Nulla porta lobortis ligula vel egestas. Curabitur aliquam euismod dolor non ornare.', '2024-06-01 10:05:00'),
    (2, 1, 3, 'Sed varius a risus eget aliquam. Nunc viverra elit ac laoreet suscipit.',              '2024-06-01 10:06:00'),
    (3, 2, 3, 'Pellentesque et sapien pulvinar consectetur. Ubi est barbatus nix.',                    '2024-05-30 09:35:00'),
    (4, 2, 3, 'Abnobas sunt hilotaes de placidus vita. Ubi est audax amicitia.',                       '2024-05-30 09:36:00'),
    (5, 3, 3, 'Eposs sunt solems de superbus fortis. Vae humani generis.',                             '2024-05-28 14:20:00'),
    (6, 3, 3, 'Diatrias tolerare tanquam noster caesium. Era brevis ratione est.',                     '2024-05-28 14:21:00');
