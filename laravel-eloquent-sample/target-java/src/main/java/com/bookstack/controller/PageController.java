package com.bookstack.controller;

import com.bookstack.entity.Page;
import com.bookstack.repository.PageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageRepository pageRepository;

    public PageController(PageRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Page> get(@PathVariable Long id) {
        return pageRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
