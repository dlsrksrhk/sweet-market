package com.sweet.market.productview.repository;

import com.sweet.market.productview.domain.ProductViewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ProductViewEventRepository extends JpaRepository<ProductViewEvent, Long> {

    long deleteByViewedAtBefore(Instant cutoff);
}
