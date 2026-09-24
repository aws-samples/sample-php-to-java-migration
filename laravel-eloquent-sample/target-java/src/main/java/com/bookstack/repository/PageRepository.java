package com.bookstack.repository;

import com.bookstack.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Long> {
    List<Page> findByChapterId(Long chapterId);
    List<Page> findByBookIdAndChapterIsNull(Long bookId);
}
