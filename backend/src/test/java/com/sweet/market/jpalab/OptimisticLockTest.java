package com.sweet.market.jpalab;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockTest extends IntegrationTestSupport {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void 같은_상품을_두_트랜잭션이_예약하면_나중_커밋이_optimistic_lock으로_실패한다() {
        Long productId = saveProduct();
        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager secondEntityManager = entityManagerFactory.createEntityManager();
        EntityTransaction firstTransaction = firstEntityManager.getTransaction();
        EntityTransaction secondTransaction = secondEntityManager.getTransaction();

        try {
            firstTransaction.begin();
            secondTransaction.begin();

            Product firstProduct = firstEntityManager.find(Product.class, productId);
            Product secondProduct = secondEntityManager.find(Product.class, productId);

            firstProduct.reserve();
            secondProduct.reserve();

            firstTransaction.commit();

            assertThatThrownBy(secondTransaction::commit)
                    .isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);
        } finally {
            rollbackIfActive(firstTransaction);
            rollbackIfActive(secondTransaction);
            firstEntityManager.close();
            secondEntityManager.close();
        }
    }

    private Long saveProduct() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Member seller = memberRepository.save(Member.create("seller@example.com", "encoded-password", "seller"));
            Product product = productRepository.save(Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L));
            return product.getId();
        });
    }

    private void rollbackIfActive(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
