package com.sweet.market.store.operations;

public record StoreCatalogSummaryResponse(
        long onSaleCount,
        long reservedCount,
        long soldOutCount,
        long hiddenCount,
        boolean catalogWritable
) {

    public StoreCatalogSummaryResponse(long onSaleCount, long reservedCount, long soldOutCount, long hiddenCount) {
        this(onSaleCount, reservedCount, soldOutCount, hiddenCount, false);
    }

    public StoreCatalogSummaryResponse withCatalogWritable(boolean catalogWritable) {
        return new StoreCatalogSummaryResponse(
                onSaleCount,
                reservedCount,
                soldOutCount,
                hiddenCount,
                catalogWritable
        );
    }
}
