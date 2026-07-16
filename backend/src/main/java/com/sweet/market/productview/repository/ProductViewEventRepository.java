package com.sweet.market.productview.repository;

import com.sweet.market.productview.domain.ProductViewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductViewEventRepository extends JpaRepository<ProductViewEvent, Long> {
}
