package com.bookstack.controller;

import com.bookstack.entity.Book;
import com.bookstack.entity.Chapter;
import com.bookstack.repository.BookRepository;
import com.bookstack.repository.ChapterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;

    public BookController(BookRepository bookRepository, ChapterRepository chapterRepository) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
    }

    @GetMapping
    public List<Book> list() {
        return bookRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> get(@PathVariable Long id) {
        return bookRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/chapters")
    public List<Chapter> chapters(@PathVariable Long id) {
        return chapterRepository.findByBookId(id);
    }
}
