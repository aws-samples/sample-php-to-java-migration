package com.symfony.demo.repository;

import com.symfony.demo.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Comment}. The PHP demo had no dedicated
 * {@code CommentRepository}; comments are persisted via the owning {@link com.symfony.demo.entity.Post}
 * association (cascade PERSIST) and read through {@code Post.getComments()} (ordered DESC).
 * This basic repository is provided for direct comment persistence in the comment-creation flow
 * (task 9.1).
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {
}
