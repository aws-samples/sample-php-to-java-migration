package com.sylius.config;

import com.sylius.entity.Channel;
import com.sylius.entity.Product;
import com.sylius.entity.Taxon;
import com.sylius.repository.ChannelRepository;
import com.sylius.repository.ProductRepository;
import com.sylius.repository.TaxonRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds sample rows so the API has something to return. Runs on an in-memory H2 database, not a
 * connection to a real Sylius store's data — a deliberate simplification for this pilot, same as
 * the BookStack slice.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ChannelRepository channelRepository;
    private final TaxonRepository taxonRepository;
    private final ProductRepository productRepository;

    public DataSeeder(ChannelRepository channelRepository, TaxonRepository taxonRepository, ProductRepository productRepository) {
        this.channelRepository = channelRepository;
        this.taxonRepository = taxonRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        Channel webStore = new Channel();
        webStore.setCode("WEB");
        webStore.setName("Web Store");
        webStore.setEnabled(true);
        webStore.setHostname("shop.example.com");
        channelRepository.save(webStore);
        // createdAt/updatedAt are set by JPA auditing (@EnableJpaAuditing), not here — mirrors the
        // source's Gedmo Timestampable behavior where application code never sets these fields.

        Taxon category = new Taxon();
        category.setCode("category");
        category.setEnabled(true);
        category.setPosition(0);
        taxonRepository.save(category);

        Taxon electronics = new Taxon();
        electronics.setCode("electronics");
        electronics.setEnabled(true);
        electronics.setPosition(0);
        electronics.setParent(category);
        taxonRepository.save(electronics);

        Product product = new Product();
        product.setVariantSelectionMethod("choice");
        product.setAverageRating(4.5);
        product.setMainTaxon(electronics);
        product.getChannels().add(webStore);
        productRepository.save(product);
    }
}
