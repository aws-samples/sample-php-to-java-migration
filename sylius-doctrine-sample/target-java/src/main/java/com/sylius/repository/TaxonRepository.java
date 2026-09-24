package com.sylius.repository;

import com.sylius.entity.Taxon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxonRepository extends JpaRepository<Taxon, Long> {
    List<Taxon> findByParentId(Long parentId);
}
