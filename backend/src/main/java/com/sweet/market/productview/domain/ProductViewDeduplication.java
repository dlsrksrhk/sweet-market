package com.sweet.market.productview.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(name = "product_view_deduplications")
@IdClass(ProductViewDeduplication.IdValue.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductViewDeduplication {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Id
    @Column(name = "visitor_hash", nullable = false, columnDefinition = "char(64)")
    private String visitorHash;

    @Column(name = "last_counted_at", nullable = false)
    private Instant lastCountedAt;

    public static final class IdValue implements Serializable {

        private Long productId;
        private String visitorHash;

        public IdValue() {
        }

        public IdValue(Long productId, String visitorHash) {
            this.productId = productId;
            this.visitorHash = visitorHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdValue that)) {
                return false;
            }
            return Objects.equals(productId, that.productId)
                    && Objects.equals(visitorHash, that.visitorHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, visitorHash);
        }
    }
}
