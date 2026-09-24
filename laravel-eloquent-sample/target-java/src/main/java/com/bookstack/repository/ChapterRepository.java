package com.bookstack.repository;

import com.bookstack.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByBookId(Long bookId);
}
