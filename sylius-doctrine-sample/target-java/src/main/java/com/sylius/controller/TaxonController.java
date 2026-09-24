package com.sylius.controller;

import com.sylius.entity.Taxon;
import com.sylius.repository.TaxonRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/taxons")
public class TaxonController {

    private final TaxonRepository taxonRepository;

    public TaxonController(TaxonRepository taxonRepository) {
        this.taxonRepository = taxonRepository;
    }

    @GetMapping
    public List<Taxon> list() {
        return taxonRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Taxon> get(@PathVariable Long id) {
        return taxonRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/children")
    public List<Taxon> children(@PathVariable Long id) {
        return taxonRepository.findByParentId(id);
    }
}
