package com.sweet.market.discovery.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class JdbcStatementMetrics {

    private final Counter statements;

    public JdbcStatementMetrics(MeterRegistry meterRegistry) {
        this.statements = Counter.builder("discovery.jdbc.statements")
                .description("JDBC statements executed by Hibernate and JDBC template paths")
                .register(meterRegistry);
    }

    public void record() {
        statements.increment();
    }
}
