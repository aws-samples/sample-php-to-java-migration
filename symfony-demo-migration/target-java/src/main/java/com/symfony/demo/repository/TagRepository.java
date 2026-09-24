package com.symfony.demo.repository;

import com.symfony.demo.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Tag}. Port of the PHP {@code App\Repository\TagRepository},
 * which was an empty custom repository. {@code findByName} supports the blog tag filter, where
 * the {@code ?tag=} query parameter is resolved to a {@link Tag} before calling
 * {@code PostRepository.findLatest}.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {

    /**
     * Finds a tag by its unique name.
     */
    Optional<Tag> findByName(String name);
}
