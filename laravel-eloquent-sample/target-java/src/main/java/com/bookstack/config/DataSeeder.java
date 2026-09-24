package com.bookstack.config;

import com.bookstack.entity.Book;
import com.bookstack.entity.Chapter;
import com.bookstack.entity.Page;
import com.bookstack.repository.BookRepository;
import com.bookstack.repository.ChapterRepository;
import com.bookstack.repository.PageRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a couple of sample rows so the EC2 smoke test has something to return. This app runs on
 * an in-memory H2 database (a deliberate simplification for this proof-of-concept slice, not a
 * connection to BookStack's real MySQL data — see README.md).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final BookRepository bookRepository;
    private final ChapterRepository chapterRepository;
    private final PageRepository pageRepository;

    public DataSeeder(BookRepository bookRepository, ChapterRepository chapterRepository, PageRepository pageRepository) {
        this.bookRepository = bookRepository;
        this.chapterRepository = chapterRepository;
        this.pageRepository = pageRepository;
    }

    @Override
    public void run(String... args) {
        Book book = new Book();
        book.setName("Getting Started with BookStack");
        book.setSlug("getting-started");
        book.setDescription("A sample book seeded for the EC2 smoke test.");
        bookRepository.save(book);

        Chapter chapter = new Chapter();
        chapter.setBook(book);
        chapter.setName("Introduction");
        chapter.setSlug("introduction");
        chapter.setDescription("Introductory chapter.");
        chapter.setPriority(1);
        chapterRepository.save(chapter);

        Page page = new Page();
        page.setBook(book);
        page.setChapter(chapter);
        page.setName("Welcome");
        page.setSlug("welcome");
        page.setHtml("<p>Welcome to the migrated slice.</p>");
        page.setMarkdown("Welcome to the migrated slice.");
        page.setText("Welcome to the migrated slice.");
        page.setPriority(1);
        page.setDraft(false);
        page.setTemplate(false);
        pageRepository.save(page);
    }
}
