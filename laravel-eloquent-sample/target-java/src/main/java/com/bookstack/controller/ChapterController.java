package com.bookstack.controller;

import com.bookstack.entity.Chapter;
import com.bookstack.entity.Page;
import com.bookstack.repository.ChapterRepository;
import com.bookstack.repository.PageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chapters")
public class ChapterController {

    private final ChapterRepository chapterRepository;
    private final PageRepository pageRepository;

    public ChapterController(ChapterRepository chapterRepository, PageRepository pageRepository) {
        this.chapterRepository = chapterRepository;
        this.pageRepository = pageRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Chapter> get(@PathVariable Long id) {
        return chapterRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pages")
    public List<Page> pages(@PathVariable Long id) {
        return pageRepository.findByChapterId(id);
    }
}
