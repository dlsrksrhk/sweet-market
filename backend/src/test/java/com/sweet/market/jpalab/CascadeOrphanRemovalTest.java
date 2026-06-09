package com.sweet.market.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductImageRepository;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

class CascadeOrphanRemovalTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Test
    @Transactional
    void cascadePersistSavesProductImagesWithProduct() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");

        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        Product foundProduct = productRepository.findWithSellerAndImagesById(product.getId()).orElseThrow();

        assertThat(foundProduct.getImages()).hasSize(2);
        assertThat(productImageRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    void orphanRemovalDeletesImageWhenRemovedFromProductCollection() {
        Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");
        productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        Product foundProduct = productRepository.findWithSellerAndImagesById(product.getId()).orElseThrow();
        Long imageId = foundProduct.getImages().get(0).getId();

        foundProduct.removeImage(imageId);
        entityManager.flush();
        entityManager.clear();

        assertThat(productImageRepository.existsById(imageId)).isFalse();
        assertThat(productImageRepository.count()).isEqualTo(1);
    }
}
